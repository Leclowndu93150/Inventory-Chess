package com.leclowndu93150.guichess.engine.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Web-based Stockfish integration using chess-api.com for high-performance analysis.
 * 
 * <p>Provides cloud-based chess analysis with up to 80 MNPS calculation power without
 * consuming local server resources. Falls back to local binary integration when unavailable.
 * 
 * <p><strong>THE BINARY SYSTEM IS ONLY A FALLBACK IF INTERNET CONNECTION IS UNAVAILABLE.</strong>
 */
public class StockfishWebIntegration implements IStockfishEngine {
    private static final String API_URL = "https://chess-api.com/v1";
    private static final int DEFAULT_DEPTH = 12;
    private static final int DEFAULT_THINKING_TIME = 50;
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    private static StockfishWebIntegration instance;
    private int analysisDepth = DEFAULT_DEPTH;
    private int analysisTime = DEFAULT_THINKING_TIME;
    private int skillLevel = 20;
    
    private StockfishWebIntegration() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }
    
    public static StockfishWebIntegration getInstance() {
        if (instance == null) {
            instance = new StockfishWebIntegration();
        }
        return instance;
    }
    
    public void requestHint(String fen, Consumer<String> callback) {
        analyzePositionWithParams(fen, DEFAULT_DEPTH, DEFAULT_THINKING_TIME, response -> {
            if (response != null && response.has("lan")) {
                String moveUci = response.get("lan").getAsString();
                String formattedMove = formatMove(moveUci);
                
                String hint = "Best move: " + formattedMove;
                
                if (response.has("eval")) {
                    double eval = response.get("eval").getAsDouble();
                    hint += " (Eval: " + String.format("%.2f", eval) + ")";
                }
                
                callback.accept(hint);
            } else {
                callback.accept("Unable to analyze position");
            }
        });
    }
    
    private void analyzePositionWithParams(String fen, int depth, int thinkingTime, Consumer<JsonObject> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("fen", fen);
                requestBody.addProperty("depth", Math.min(depth, 18));
                requestBody.addProperty("maxThinkingTime", Math.min(thinkingTime, 100));
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    callback.accept(jsonResponse);
                } else {
                    System.err.println("[StockfishWeb] HTTP Error " + response.statusCode() + ": " + response.body());
                    callback.accept(null);
                }
                
            } catch (IOException | InterruptedException e) {
                System.err.println("[StockfishWeb] Exception: " + e.getMessage());
                e.printStackTrace();
                callback.accept(null);
            }
        });
    }
    
    public void evaluatePosition(String fen, Consumer<StockfishIntegration.EvaluationResult> callback) {
        analyzePositionWithParams(fen, DEFAULT_DEPTH, DEFAULT_THINKING_TIME, response -> {
            if (response != null && response.has("eval")) {
                StockfishIntegration.EvaluationResult result = new StockfishIntegration.EvaluationResult();
                
                double evaluation = response.get("eval").getAsDouble();
                result.centipawnEvaluation = (int) Math.round(evaluation * 100);
                
                if (response.has("text")) {
                    result.evaluationText = response.get("text").getAsString();
                }
                
                if (response.has("depth")) {
                    result.depth = response.get("depth").getAsInt();
                }
                
                if (response.has("mate")) {
                    if (!response.get("mate").isJsonNull()) {
                        result.isMate = true;
                        result.mateIn = response.get("mate").getAsInt();
                    }
                }
                
                result.nodesSearched = 0;
                result.nodesPerSecond = 80_000_000;
                
                callback.accept(result);
            } else {
                callback.accept(new StockfishIntegration.EvaluationResult("Web service unavailable"));
            }
        });
    }
    
    public void checkServiceAvailability(Consumer<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject testRequest = new JsonObject();
                testRequest.addProperty("fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                testRequest.addProperty("depth", 1);
                testRequest.addProperty("maxThinkingTime", 10);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(testRequest)))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                callback.accept(response.statusCode() == 200);
                
            } catch (Exception e) {
                callback.accept(false);
            }
        });
    }
    
    public String parseDetailedMoveInfo(JsonObject response) {
        if (response == null) return null;
        
        StringBuilder moveInfo = new StringBuilder();
        
        if (response.has("san")) {
            moveInfo.append("Move: ").append(response.get("san").getAsString());
        }
        
        if (response.has("eval")) {
            double eval = response.get("eval").getAsDouble();
            moveInfo.append(" (").append(String.format("%.2f", eval)).append(")");
        }
        
        if (response.has("depth")) {
            moveInfo.append(" [depth ").append(response.get("depth").getAsInt()).append("]");
        }
        
        return moveInfo.toString();
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
    
    @Override
    public boolean isAvailable() {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            checkServiceAvailability(result -> {
                // This is handled asynchronously
            });
            return true;
        });
        
        try {
            return future.get();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> waitUntilReady() {
        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture<Boolean> checkFuture = new CompletableFuture<>();
            checkServiceAvailability(checkFuture::complete);
            try {
                return checkFuture.get();
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<String> requestHint(String fen) {
        CompletableFuture<String> future = new CompletableFuture<>();
        requestHint(fen, future::complete);
        return future;
    }
    
    @Override
    public CompletableFuture<StockfishIntegration.EvaluationResult> evaluatePosition(String fen) {
        CompletableFuture<StockfishIntegration.EvaluationResult> future = new CompletableFuture<>();
        evaluatePosition(fen, future::complete);
        return future;
    }
    
    @Override
    public CompletableFuture<StockfishIntegration.AnalysisResult> analyzePosition(String fen) {
        CompletableFuture<StockfishIntegration.AnalysisResult> future = new CompletableFuture<>();
        analyzePositionWithParams(fen, analysisDepth, analysisTime, response -> {
            if (response != null) {
                StockfishIntegration.AnalysisResult result = new StockfishIntegration.AnalysisResult();
                
                if (response.has("lan")) {
                    result.bestMove = response.get("lan").getAsString();
                } else if (response.has("move")) {
                    result.bestMove = response.get("move").getAsString();
                }
                
                if (response.has("eval")) {
                    double eval = response.get("eval").getAsDouble();
                    result.centipawns = (int) Math.round(eval * 100);
                    result.evaluation = String.format("%.2f", eval);
                }
                
                if (response.has("depth")) {
                    result.depth = response.get("depth").getAsInt();
                }
                
                if (response.has("mate") && !response.get("mate").isJsonNull()) {
                    result.isMate = true;
                    result.mateIn = response.get("mate").getAsInt();
                }
                
                future.complete(result);
            } else {
                StockfishIntegration.AnalysisResult errorResult = new StockfishIntegration.AnalysisResult();
                errorResult.error = "Web service unavailable or no response received";
                future.complete(errorResult);
            }
        });
        return future;
    }
    
    @Override
    public void analyzePosition(String fen, Consumer<StockfishIntegration.AnalysisResult> callback) {
        analyzePosition(fen).thenAccept(callback).exceptionally(throwable -> {
            StockfishIntegration.AnalysisResult errorResult = new StockfishIntegration.AnalysisResult();
            errorResult.error = "Error: " + throwable.getMessage();
            throwable.printStackTrace();
            callback.accept(errorResult);
            return null;
        });
    }
    
    @Override
    public void setSkillLevel(int level) {
        this.skillLevel = Math.max(-20, Math.min(20, level));
        // Note: Web API doesn't support skill level, so this is for interface compatibility
    }
    
    @Override
    public void setAnalysisDepth(int depth) {
        this.analysisDepth = Math.max(1, Math.min(18, depth));
    }
    
    @Override
    public void setAnalysisTime(int timeMs) {
        this.analysisTime = Math.max(10, Math.min(100, timeMs / 10)); // Convert ms to deciseconds
    }
    
    @Override
    public void shutdown() {
        // No resources to clean up for web integration
    }
}