package com.leclowndu93150.guichess.engine.integration;

import com.leclowndu93150.guichess.engine.installer.StockfishInstaller;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local Stockfish binary integration for offline chess analysis.
 * 
 * <p><strong>THE BINARY SYSTEM IS ONLY A FALLBACK IF INTERNET CONNECTION IS UNAVAILABLE.</strong>
 * This integration handles local Stockfish process management and UCI communication when the 
 * primary web-based analysis service is not accessible.
 */
public class StockfishIntegration implements IStockfishEngine {
    private static StockfishIntegration instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CompletableFuture<Void> initializationFuture;

    private Process stockfishProcess;
    private BufferedWriter stockfishInput;
    private BufferedReader stockfishOutput;
    private boolean isReady = false;
    private boolean isInitialized = false;

    private int analysisDepth = 15;
    private int multiPv = 3;
    private int analysisTime = 1000;
    private int skillLevel = 20;
    private String stockfishPath;

    private static final Pattern BEST_MOVE_PATTERN = Pattern.compile("bestmove\\s+(\\w+)");
    private static final Pattern INFO_PATTERN = Pattern.compile("info.*depth\\s+(\\d+).*score\\s+cp\\s+([+-]?\\d+).*pv\\s+(.+)");
    private static final Pattern MATE_PATTERN = Pattern.compile("info.*depth\\s+(\\d+).*score\\s+mate\\s+([+-]?\\d+).*pv\\s+(.+)");

    private StockfishIntegration() {
        this.initializationFuture = CompletableFuture.runAsync(this::initializeStockfish, executor)
                .exceptionally(throwable -> {
                    return null;
                });
    }

    public static StockfishIntegration getInstance() {
        if (instance == null) {
            instance = new StockfishIntegration();
        }
        return instance;
    }

    public CompletableFuture<Boolean> waitUntilReady() {
        return initializationFuture.thenApply(v -> isInitialized);
    }

    private void initializeStockfish() {
        try {
            findStockfishExecutable();
            startStockfishProcess();
            configureEngine();
            isInitialized = true;
        } catch (Exception e) {
            isInitialized = false;
        }
    }

    private void findStockfishExecutable() throws IOException {
        String overridePath = System.getProperty("chess.stockfish.path", "");
        if (!overridePath.isEmpty() && Files.exists(Path.of(overridePath))) {
            this.stockfishPath = overridePath;
            return;
        }

        Path dataDir = Paths.get("config", "guichess");
        Files.createDirectories(dataDir);

        StockfishInstaller installer = new StockfishInstaller(dataDir);

        try {
            installer.installIfNeededAsync().get(60, TimeUnit.SECONDS);

            Path executablePath = installer.getExecutablePath();

            if (Files.exists(executablePath)) {
                if (Files.isExecutable(executablePath)) {
                    this.stockfishPath = executablePath.toString();
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        this.stockfishPath = executablePath.toString();
                    } else {
                        throw new IOException("Stockfish file exists but is not executable: " + executablePath);
                    }
                }
            } else {
                throw new IOException("Stockfish executable not found at: " + executablePath);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Failed to install Stockfish: " + e.getMessage(), e);
        }
    }

    private void startStockfishProcess() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(stockfishPath);
        processBuilder.redirectErrorStream(true);

        stockfishProcess = processBuilder.start();
        stockfishInput = new BufferedWriter(new OutputStreamWriter(stockfishProcess.getOutputStream()));
        stockfishOutput = new BufferedReader(new InputStreamReader(stockfishProcess.getInputStream()));

        sendCommand("uci");
        waitForResponse("uciok", 5000);
        isReady = true;
    }

    private void configureEngine() throws IOException {
        sendCommand("setoption name Hash value 256");
        sendCommand("setoption name Threads value 1");
        sendCommand("setoption name MultiPV value " + multiPv);
        sendCommand("setoption name Skill Level value " + skillLevel);
        sendCommand("ucinewgame");
        sendCommand("isready");
        waitForResponse("readyok", 5000);
    }

    private void sendCommand(String command) throws IOException {
        if (stockfishInput != null) {
            stockfishInput.write(command);
            stockfishInput.newLine();
            stockfishInput.flush();
        }
    }

    private boolean waitForResponse(String expectedResponse, long timeoutMs) throws IOException {
        long startTime = System.currentTimeMillis();
        String line;
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (stockfishOutput.ready()) {
                line = stockfishOutput.readLine();
                if (line != null && line.trim().equals(expectedResponse)) {
                    return true;
                }
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public void shutdown() {
        if (stockfishProcess != null && stockfishProcess.isAlive()) {
            try {
                sendCommand("quit");
                if (!stockfishProcess.waitFor(5, TimeUnit.SECONDS)) {
                    stockfishProcess.destroyForcibly();
                }
            } catch (Exception e) {
                stockfishProcess.destroyForcibly();
            }
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public CompletableFuture<String> requestHint(String fen) {
        return analyzePosition(fen).thenApply(result -> {
            String hint;
            if (result.bestMove != null) {
                hint = "Best move: " + formatMove(result.bestMove);
                if (result.evaluation != null) {
                    hint += " (Eval: " + result.evaluation + ")";
                }
            } else {
                hint = "Unable to analyze position";
            }
            return hint;
        });
    }

    public void requestHint(String fen, Consumer<String> callback) {
        requestHint(fen).thenAccept(callback).exceptionally(throwable -> {
            callback.accept("Error: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<EvaluationResult> evaluatePosition(String fen) {
        if (!isInitialized) {
            return initializationFuture.thenCompose(v -> evaluatePosition(fen));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                sendCommand("position fen " + fen);
                sendCommand("go depth " + analysisDepth);

                AnalysisResult bestResult = null;
                String line;
                long startTime = System.currentTimeMillis();

                while (System.currentTimeMillis() - startTime < analysisTime + 2000) {
                    if (stockfishOutput.ready()) {
                        line = stockfishOutput.readLine();
                        if (line == null) break;

                        if (line.startsWith("bestmove")) {
                            break;
                        }

                        if (line.startsWith("info") && line.contains("depth") && line.contains("score")) {
                            AnalysisResult result = parseInfoLine(line);
                            if (result != null) {
                                bestResult = result;
                            }
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }

                if (bestResult != null) {
                    EvaluationResult result = new EvaluationResult();
                    result.centipawnEvaluation = bestResult.centipawns;
                    result.evaluationText = bestResult.evaluation;
                    result.depth = bestResult.depth;
                    result.isMate = bestResult.isMate;
                    result.mateIn = bestResult.mateIn;
                    return result;
                } else {
                    return new EvaluationResult("No analysis result");
                }

            } catch (Exception e) {
                return new EvaluationResult("Analysis failed: " + e.getMessage());
            }
        }, executor);
    }

    public void evaluatePosition(String fen, Consumer<EvaluationResult> callback) {
        evaluatePosition(fen).thenAccept(callback).exceptionally(throwable -> {
            callback.accept(new EvaluationResult("Error: " + throwable.getMessage()));
            return null;
        });
    }

    public CompletableFuture<AnalysisResult> analyzePosition(String fen) {
        if (!isInitialized) {
            return initializationFuture.thenCompose(v -> analyzePosition(fen));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                sendCommand("position fen " + fen);
                sendCommand("go depth " + analysisDepth);

                AnalysisResult result = new AnalysisResult();
                String line;
                long startTime = System.currentTimeMillis();

                while (System.currentTimeMillis() - startTime < analysisTime + 2000) {
                    if (stockfishOutput.ready()) {
                        line = stockfishOutput.readLine();
                        if (line == null) break;

                        Matcher bestMoveMatcher = BEST_MOVE_PATTERN.matcher(line);
                        if (bestMoveMatcher.find()) {
                            result.bestMove = bestMoveMatcher.group(1);
                            break;
                        }

                        if (line.startsWith("info")) {
                            AnalysisResult tempResult = parseInfoLine(line);
                            if (tempResult != null) {
                                result.evaluation = tempResult.evaluation;
                                result.depth = tempResult.depth;
                                result.centipawns = tempResult.centipawns;
                                result.isMate = tempResult.isMate;
                                result.mateIn = tempResult.mateIn;
                            }
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }

                return result;
            } catch (Exception e) {
                AnalysisResult errorResult = new AnalysisResult();
                errorResult.error = "Analysis failed: " + e.getMessage();
                return errorResult;
            }
        }, executor);
    }

    public void analyzePosition(String fen, Consumer<AnalysisResult> callback) {
        analyzePosition(fen).thenAccept(callback).exceptionally(throwable -> {
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.error = "Error: " + throwable.getMessage();
            callback.accept(errorResult);
            return null;
        });
    }

    private String formatMove(String move) {
        if (move.length() < 4) return move;

        String from = move.substring(0, 2);
        String to = move.substring(2, 4);
        String promotion = move.length() > 4 ? move.substring(4) : "";

        String formatted = from + "-" + to;
        if (!promotion.isEmpty()) {
            formatted += "=" + promotion.toUpperCase();
        }

        return formatted;
    }

    private AnalysisResult parseInfoLine(String line) {
        Matcher infoMatcher = INFO_PATTERN.matcher(line);
        Matcher mateMatcher = MATE_PATTERN.matcher(line);

        if (mateMatcher.find()) {
            AnalysisResult result = new AnalysisResult();
            result.depth = Integer.parseInt(mateMatcher.group(1));
            result.mateIn = Integer.parseInt(mateMatcher.group(2));
            result.isMate = true;
            result.evaluation = "Mate in " + Math.abs(result.mateIn);
            result.centipawns = result.mateIn > 0 ? 10000 : -10000;
            return result;
        } else if (infoMatcher.find()) {
            AnalysisResult result = new AnalysisResult();
            result.depth = Integer.parseInt(infoMatcher.group(1));
            result.centipawns = Integer.parseInt(infoMatcher.group(2));
            result.evaluation = String.format("%.2f", result.centipawns / 100.0);
            return result;
        }

        return null;
    }

    public void setSkillLevel(int level) {
        this.skillLevel = Math.max(-20, Math.min(20, level));
        if (isReady) {
            try {
                sendCommand("setoption name Skill Level value " + skillLevel);
            } catch (IOException e) {
            }
        }
    }
    
    @Override
    public boolean isAvailable() {
        return isInitialized && isReady && stockfishProcess != null && stockfishProcess.isAlive();
    }
    
    @Override
    public void setAnalysisDepth(int depth) {
        this.analysisDepth = Math.max(1, Math.min(30, depth));
    }
    
    @Override
    public void setAnalysisTime(int timeMs) {
        this.analysisTime = Math.max(100, Math.min(60000, timeMs));
    }

    public static class AnalysisResult {
        public String bestMove;
        public String evaluation;
        public String principalVariation;
        public int depth = 0;
        public boolean isMate = false;
        public int mateIn = 0;
        public int centipawns = 0;
        public String error;

        public AnalysisResult() {}

        public AnalysisResult(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            if (error != null) return error;
            return String.format("Best: %s, Eval: %s (Depth: %d)",
                    bestMove != null ? bestMove : "?",
                    evaluation != null ? evaluation : "?", depth);
        }
    }

    public static class EvaluationResult {
        public int centipawnEvaluation;
        public String evaluationText;
        public boolean isMate = false;
        public int mateIn;
        public int depth;
        public long nodesSearched;
        public long nodesPerSecond;
        public String error;

        public EvaluationResult() {}

        public EvaluationResult(String error) {
            this.error = error;
        }

        public double getPawnAdvantage() {
            return centipawnEvaluation / 100.0;
        }

        @Override
        public String toString() {
            if (error != null) return error;
            return String.format("Eval: %s (Depth: %d, Nodes: %d)",
                    evaluationText != null ? evaluationText : "?", depth, nodesSearched);
        }
    }
}