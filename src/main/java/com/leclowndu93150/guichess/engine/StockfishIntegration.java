package com.leclowndu93150.guichess.engine;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local binary integration layer for the Stockfish chess engine as a fallback system.
 * 
 * <p><strong>NOTE: THE BINARY SYSTEM IS ONLY A FALLBACK IF INTERNET CONNECTION IS UNAVAILABLE
 * (CONSIDERING THAT WE'VE DOWNLOADED THE BINARY BEFORE).</strong> This class manages the 
 * lifecycle of a local Stockfish process and handles UCI communication when the primary
 * web-based analysis service is not accessible.
 * 
 * <h3>Fallback Architecture:</h3>
 * <p>This integration serves as the secondary analysis method, automatically engaged when:
 * <ul>
 *   <li>Internet connectivity is unavailable</li>
 *   <li>The chess-api.com web service is unreachable</li>
 *   <li>Web API requests timeout or fail</li>
 *   <li>Local analysis is specifically requested for offline play</li>
 * </ul>
 * 
 * <h3>Binary Management:</h3>
 * <p>The class handles automatic download and setup of platform-specific Stockfish binaries
 * when needed, but operates silently to avoid user notification of internal processes.
 * Binary management includes:
 * <ul>
 *   <li>Platform detection (Windows, Linux, macOS)</li>
 *   <li>Automatic binary download from official sources</li>
 *   <li>Binary verification and executable permission setup</li>
 *   <li>Graceful fallback to basic analysis if binary unavailable</li>
 * </ul>
 * 
 * <h3>Performance Characteristics:</h3>
 * <p>Local analysis provides consistent performance but may consume server CPU resources.
 * The integration is optimized for:
 * <ul>
 *   <li>Minimal resource usage during gameplay</li>
 *   <li>Asynchronous operation to prevent game blocking</li>
 *   <li>Configurable analysis depth for performance tuning</li>
 *   <li>Process lifecycle management to prevent resource leaks</li>
 * </ul>
 * 
 * @author GUIChess Team
 * @since 1.0
 * @see StockfishWebIntegration For the primary web-based analysis system
 */
public class StockfishIntegration {
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
                    // Silent fallback - binary system operates quietly
                    return null;
                });
    }

    public static StockfishIntegration getInstance() {
        if (instance == null) {
            instance = new StockfishIntegration();
        }
        return instance;
    }

    private void initializeStockfish() {
        try {
            findStockfishExecutable();
            startStockfishProcess();
            configureEngine();
            isInitialized = true;
            // Silent initialization - binary system operates quietly
        } catch (Exception e) {
            // Silent fallback - binary system operates quietly
            isInitialized = false;
        }
    }

    private void findStockfishExecutable() throws IOException {
        String overridePath = System.getProperty("chess.stockfish.path", "");
        if (!overridePath.isEmpty() && Files.exists(Path.of(overridePath))) {
            this.stockfishPath = overridePath;
            // Silent path override - binary system operates quietly
            return;
        }

        Path dataDir = Paths.get("config", "guichess");
        Files.createDirectories(dataDir);

        StockfishInstaller installer = new StockfishInstaller(dataDir);

        try {
            installer.installIfNeededAsync().get(60, TimeUnit.SECONDS);

            Path executablePath = installer.getExecutablePath();
            // Silent binary verification - binary system operates quietly

            if (Files.exists(executablePath)) {
                if (Files.isExecutable(executablePath)) {
                    this.stockfishPath = executablePath.toString();
                    // Silent path assignment - binary system operates quietly
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        this.stockfishPath = executablePath.toString();
                        // Silent Windows path assignment - binary system operates quietly
                    } else {
                        throw new IOException("Stockfish file exists but is not executable: " + executablePath);
                    }
                }
            } else {
                throw new IOException("Stockfish executable not found at: " + executablePath);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Failed to install Stockfish", e);
        }
    }

    private void startStockfishProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(stockfishPath);
        pb.redirectErrorStream(true);

        stockfishProcess = pb.start();
        stockfishInput = new BufferedWriter(new OutputStreamWriter(stockfishProcess.getOutputStream()));
        stockfishOutput = new BufferedReader(new InputStreamReader(stockfishProcess.getInputStream()));

        sendCommand("uci");
        waitForResponse("uciok", 5000);

        sendCommand("isready");
        waitForResponse("readyok", 5000);

        isReady = true;
    }

    private void configureEngine() throws IOException {
        sendCommand("setoption name Hash value 128");
        sendCommand("setoption name Threads value " + Math.min(4, Runtime.getRuntime().availableProcessors()));
        sendCommand("setoption name MultiPV value " + multiPv);
        sendCommand("setoption name Skill Level value " + skillLevel);

        sendCommand("isready");
        waitForResponse("readyok", 5000);
    }

    private void sendCommand(String command) throws IOException {
        if (!isReady && stockfishInput == null) {
            throw new IOException("Stockfish not ready");
        }

        stockfishInput.write(command);
        stockfishInput.newLine();
        stockfishInput.flush();
    }

    private void waitForResponse(String expectedResponse, long timeoutMs) throws IOException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (stockfishOutput.ready()) {
                String line = stockfishOutput.readLine();
                if (line != null && line.contains(expectedResponse)) {
                    return;
                }
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for response");
            }
        }

        throw new IOException("Timeout waiting for response: " + expectedResponse);
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

                while ((line = stockfishOutput.readLine()) != null) {
                    if (line.startsWith("bestmove")) {
                        Matcher matcher = BEST_MOVE_PATTERN.matcher(line);
                        if (matcher.find()) {
                            result.bestMove = matcher.group(1);
                        }
                        break;
                    } else if (line.startsWith("info")) {
                        parseInfoLine(line, result);
                    }
                }

                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return new AnalysisResult("Error: " + e.getMessage());
            }
        }, executor);
    }

    public void analyzePosition(String fen, Consumer<AnalysisResult> callback) {
        analyzePosition(fen).thenAccept(callback).exceptionally(throwable -> {
            callback.accept(new AnalysisResult("Error: " + throwable.getMessage()));
            return null;
        });
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
                sendCommand("go movetime " + analysisTime);

                EvaluationResult result = new EvaluationResult();
                String line;

                while ((line = stockfishOutput.readLine()) != null) {
                    if (line.startsWith("bestmove")) {
                        break;
                    } else if (line.startsWith("info") && line.contains("depth")) {
                        parseEvaluationLine(line, result);
                    }
                }

                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return new EvaluationResult("Error: " + e.getMessage());
            }
        }, executor);
    }

    public void evaluatePosition(String fen, Consumer<EvaluationResult> callback) {
        evaluatePosition(fen).thenAccept(callback).exceptionally(throwable -> {
            callback.accept(new EvaluationResult("Error: " + throwable.getMessage()));
            return null;
        });
    }

    private void parseInfoLine(String line, AnalysisResult result) {
        Matcher cpMatcher = INFO_PATTERN.matcher(line);
        if (cpMatcher.find()) {
            int depth = Integer.parseInt(cpMatcher.group(1));
            int centipawns = Integer.parseInt(cpMatcher.group(2));
            String pv = cpMatcher.group(3);

            if (depth >= result.depth) {
                result.depth = depth;
                result.evaluation = formatCentipawns(centipawns);
                result.principalVariation = pv;
            }
        }

        Matcher mateMatcher = MATE_PATTERN.matcher(line);
        if (mateMatcher.find()) {
            int depth = Integer.parseInt(mateMatcher.group(1));
            int mateIn = Integer.parseInt(mateMatcher.group(2));
            String pv = mateMatcher.group(3);

            if (depth >= result.depth) {
                result.depth = depth;
                result.evaluation = "Mate in " + Math.abs(mateIn);
                result.principalVariation = pv;
                result.isMate = true;
            }
        }
    }

    private void parseEvaluationLine(String line, EvaluationResult result) {
        if (line.contains("score cp")) {
            Matcher matcher = Pattern.compile("score cp ([+-]?\\d+)").matcher(line);
            if (matcher.find()) {
                int centipawns = Integer.parseInt(matcher.group(1));
                result.centipawnEvaluation = centipawns;
                result.evaluationText = formatCentipawns(centipawns);
            }
        } else if (line.contains("score mate")) {
            Matcher matcher = Pattern.compile("score mate ([+-]?\\d+)").matcher(line);
            if (matcher.find()) {
                int mateIn = Integer.parseInt(matcher.group(1));
                result.isMate = true;
                result.mateIn = mateIn;
                result.evaluationText = "Mate in " + Math.abs(mateIn);
            }
        }

        if (line.contains("depth")) {
            Matcher matcher = Pattern.compile("depth (\\d+)").matcher(line);
            if (matcher.find()) {
                result.depth = Integer.parseInt(matcher.group(1));
            }
        }

        if (line.contains("nodes")) {
            Matcher matcher = Pattern.compile("nodes (\\d+)").matcher(line);
            if (matcher.find()) {
                result.nodesSearched = Long.parseLong(matcher.group(1));
            }
        }

        if (line.contains("nps")) {
            Matcher matcher = Pattern.compile("nps (\\d+)").matcher(line);
            if (matcher.find()) {
                result.nodesPerSecond = Long.parseLong(matcher.group(1));
            }
        }
    }

    private String formatCentipawns(int centipawns) {
        double pawns = centipawns / 100.0;
        return String.format("%+.2f", pawns);
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

    public void shutdown() {
        try {
            if (stockfishInput != null) {
                sendCommand("quit");
                stockfishInput.close();
            }
            if (stockfishOutput != null) {
                stockfishOutput.close();
            }
            if (stockfishProcess != null) {
                stockfishProcess.destroyForcibly();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
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

    public boolean isAvailable() {
        return isInitialized && isReady;
    }

    public CompletableFuture<Boolean> waitUntilReady() {
        return initializationFuture.thenApply(v -> isAvailable());
    }

    public void setAnalysisDepth(int depth) {
        this.analysisDepth = Math.max(1, Math.min(30, depth));
    }

    public void setMultiPv(int lines) {
        this.multiPv = Math.max(1, Math.min(5, lines));
    }

    public void setAnalysisTime(int milliseconds) {
        this.analysisTime = Math.max(100, milliseconds);
    }

    public void setSkillLevel(int level) {
        this.skillLevel = Math.max(-20, Math.min(20, level));
        if (isReady) {
            try {
                sendCommand("setoption name Skill Level value " + skillLevel);
            } catch (IOException e) {
                // Silent skill level setting failure - binary system operates quietly
            }
        }
    }

    public static class AnalysisResult {
        public String bestMove;
        public String evaluation;
        public String principalVariation;
        public int depth = 0;
        public boolean isMate = false;
        public String error;

        public AnalysisResult() {}

        public AnalysisResult(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            if (error != null) return error;

            StringBuilder sb = new StringBuilder();
            if (bestMove != null) {
                sb.append("Best: ").append(bestMove);
            }
            if (evaluation != null) {
                sb.append(" (").append(evaluation).append(")");
            }
            if (principalVariation != null) {
                sb.append(" PV: ").append(principalVariation);
            }
            return sb.toString();
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
