package com.leclowndu93150.guichess.engine;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockfishIntegration {
    private static StockfishIntegration instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CompletableFuture<Void> initializationFuture;

    private Process stockfishProcess;
    private BufferedWriter stockfishInput;
    private BufferedReader stockfishOutput;
    private boolean isReady = false;
    private boolean isInitialized = false;

    // Configuration
    private int analysisDepth = 15;
    private int multiPv = 3;
    private int analysisTime = 1000; // milliseconds
    private String stockfishPath;

    // Pattern matching for UCI responses
    private static final Pattern BEST_MOVE_PATTERN = Pattern.compile("bestmove\\s+(\\w+)");
    private static final Pattern INFO_PATTERN = Pattern.compile("info.*depth\\s+(\\d+).*score\\s+cp\\s+([+-]?\\d+).*pv\\s+(.+)");
    private static final Pattern MATE_PATTERN = Pattern.compile("info.*depth\\s+(\\d+).*score\\s+mate\\s+([+-]?\\d+).*pv\\s+(.+)");

    private StockfishIntegration() {
        this.initializationFuture = CompletableFuture.runAsync(this::initializeStockfish, executor);
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
            System.out.println("Stockfish integration initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Stockfish: " + e.getMessage());
            e.printStackTrace();
            isInitialized = false;
        }
    }

    private void findStockfishExecutable() throws IOException {
        // Try common Stockfish locations
        String[] possiblePaths = {
                "stockfish",           // If in PATH
                "./stockfish",         // Current directory
                "./engines/stockfish", // Engines subdirectory
                "/usr/bin/stockfish",  // Linux system installation
                "/opt/homebrew/bin/stockfish", // macOS Homebrew
                "C:\\stockfish\\stockfish.exe", // Windows common location
                System.getProperty("chess.stockfish.path", "") // System property override
        };

        for (String path : possiblePaths) {
            if (path.isEmpty()) continue;

            Path stockfishFile = Path.of(path);
            if (Files.exists(stockfishFile) && Files.isExecutable(stockfishFile)) {
                this.stockfishPath = path;
                return;
            }
        }

        // Try to find in system PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "stockfish");
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);

            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String foundPath = reader.readLine();
                    if (foundPath != null && !foundPath.trim().isEmpty()) {
                        this.stockfishPath = foundPath.trim();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to error
        }

        throw new IOException("Stockfish executable not found. Please install Stockfish and ensure it's in PATH or set chess.stockfish.path system property");
    }

    private void startStockfishProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(stockfishPath);
        pb.redirectErrorStream(true);

        stockfishProcess = pb.start();
        stockfishInput = new BufferedWriter(new OutputStreamWriter(stockfishProcess.getOutputStream()));
        stockfishOutput = new BufferedReader(new InputStreamReader(stockfishProcess.getInputStream()));

        // Wait for UCI acknowledgment
        sendCommand("uci");
        waitForResponse("uciok", 5000);

        sendCommand("isready");
        waitForResponse("readyok", 5000);

        isReady = true;
    }

    private void configureEngine() throws IOException {
        // Set engine options
        sendCommand("setoption name Hash value 128"); // 128MB hash
        sendCommand("setoption name Threads value " + Math.min(4, Runtime.getRuntime().availableProcessors()));
        sendCommand("setoption name MultiPV value " + multiPv);

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

    public void analyzePosition(String fen, Consumer<AnalysisResult> callback) {
        if (!isInitialized) {
            initializationFuture.thenRun(() -> analyzePosition(fen, callback));
            return;
        }

        executor.submit(() -> {
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

                callback.accept(result);
            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(new AnalysisResult("Error: " + e.getMessage()));
            }
        });
    }

    public void requestHint(String fen, Consumer<String> callback) {
        analyzePosition(fen, result -> {
            String hint;
            if (result.bestMove != null) {
                hint = "Best move: " + formatMove(result.bestMove);
                if (result.evaluation != null) {
                    hint += " (Eval: " + result.evaluation + ")";
                }
            } else {
                hint = "Unable to analyze position";
            }
            callback.accept(hint);
        });
    }

    public void evaluatePosition(String fen, Consumer<EvaluationResult> callback) {
        executor.submit(() -> {
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

                callback.accept(result);
            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(new EvaluationResult("Error: " + e.getMessage()));
            }
        });
    }

    private void parseInfoLine(String line, AnalysisResult result) {
        // Parse centipawn evaluation
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

        // Parse mate scores
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

    // Configuration methods
    public void setAnalysisDepth(int depth) {
        this.analysisDepth = Math.max(1, Math.min(30, depth));
    }

    public void setMultiPv(int lines) {
        this.multiPv = Math.max(1, Math.min(5, lines));
    }

    public void setAnalysisTime(int milliseconds) {
        this.analysisTime = Math.max(100, milliseconds);
    }

    // Result classes
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

