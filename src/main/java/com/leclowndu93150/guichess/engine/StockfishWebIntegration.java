package com.leclowndu93150.guichess.engine;

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
 * Web-based Stockfish integration using chess-api.com service for position analysis.
 * 
 * <p>This class provides high-performance chess analysis through a cloud-based Stockfish service,
 * offering up to 80 MNPS (Million Nodes Per Second) calculation power with 32 vCores and 128 GB DDR5.
 * The web service provides instant access to Stockfish 17 NNUE without requiring local binary downloads
 * or CPU-intensive calculations on the server.
 * 
 * <h3>Service Features:</h3>
 * <ul>
 *   <li><strong>High Performance:</strong> Up to 80 MNPS calculation power</li>
 *   <li><strong>No Local Resources:</strong> Analysis performed in the cloud</li>
 *   <li><strong>Instant Availability:</strong> No binary downloads or setup required</li>
 *   <li><strong>Stockfish 17 NNUE:</strong> Latest engine with neural network evaluation</li>
 *   <li><strong>Comprehensive Analysis:</strong> Position evaluation, best moves, and analysis depth</li>
 * </ul>
 * 
 * <h3>API Integration:</h3>
 * <p>Uses the chess-api.com REST API with POST requests to analyze positions:
 * <pre>
 * POST https://chess-api.com/v1
 * Content-Type: application/json
 * 
 * {
 *     "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
 *     "depth": 12,
 *     "maxThinkingTime": 50
 * }
 * </pre>
 * 
 * <h3>Response Processing:</h3>
 * <p>The service returns comprehensive analysis data including:
 * <ul>
 *   <li><strong>Best Move:</strong> Optimal move in various notations (SAN, LAN, UCI)</li>
 *   <li><strong>Evaluation:</strong> Position score in centipawns and win percentage</li>
 *   <li><strong>Analysis Depth:</strong> Search depth achieved (correlates to playing strength)</li>
 *   <li><strong>Continuation:</strong> Principal variation showing best play sequence</li>
 *   <li><strong>Move Details:</strong> Piece type, capture info, special move flags</li>
 * </ul>
 * 
 * <h3>Fallback Strategy:</h3>
 * <p><strong>NOTE: THE LOCAL BINARY SYSTEM IS ONLY A FALLBACK IF INTERNET CONNECTION IS UNAVAILABLE
 * (CONSIDERING THAT WE'VE DOWNLOADED THE BINARY BEFORE).</strong> This web integration is the primary
 * analysis method when internet connectivity is available, providing superior performance and
 * immediate availability without local resource consumption.
 * 
 * <h3>Error Handling:</h3>
 * <p>The integration includes robust error handling for:
 * <ul>
 *   <li>Network connectivity issues</li>
 *   <li>API service unavailability</li>
 *   <li>Invalid FEN positions</li>
 *   <li>Request timeout scenarios</li>
 * </ul>
 * 
 * <p>All network operations are performed asynchronously to avoid blocking the game thread,
 * ensuring smooth gameplay experience even during analysis requests.
 * 
 * @author GUIChess Team
 * @since 1.0
 * @see StockfishIntegration For the local binary fallback system
 */
public class StockfishWebIntegration {
    private static final String API_URL = "https://chess-api.com/v1";
    private static final int DEFAULT_DEPTH = 12;
    private static final int DEFAULT_THINKING_TIME = 50; // milliseconds
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    private static StockfishWebIntegration instance;
    
    private StockfishWebIntegration() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Returns the singleton instance of StockfishWebIntegration.
     * 
     * @return the singleton instance
     */
    public static StockfishWebIntegration getInstance() {
        if (instance == null) {
            instance = new StockfishWebIntegration();
        }
        return instance;
    }
    
    /**
     * Requests a move hint for the given FEN position using the web API.
     * 
     * <p>This method analyzes the position using cloud-based Stockfish and returns
     * the best move suggestion asynchronously. The analysis is performed at depth 12
     * which corresponds to approximately 2350 FIDE ELO (International Master level).
     * 
     * @param fen the FEN string representing the current position
     * @param callback the callback to receive the hint result
     */
    public void requestHint(String fen, Consumer<String> callback) {
        analyzePosition(fen, DEFAULT_DEPTH, DEFAULT_THINKING_TIME, response -> {
            if (response != null && response.has("lan")) {
                // Use LAN (Long Algebraic Notation) for "from-to" format like "d2d4"
                String moveUci = response.get("lan").getAsString();
                String formattedMove = formatMove(moveUci);
                
                String hint = "Best move: " + formattedMove;
                
                // Add evaluation if available
                if (response.has("eval")) {
                    double eval = response.get("eval").getAsDouble();
                    hint += " (Eval: " + String.format("%.2f", eval) + ")";
                }
                
                callback.accept(hint);
            } else {
                // Fallback to local binary if web service fails
                StockfishIntegration.getInstance().requestHint(fen, callback);
            }
        });
    }
    
    /**
     * Formats a move from UCI notation to readable "from-to" format.
     * 
     * @param move the move in UCI format (e.g., "d2d4", "e7e8q")
     * @return the formatted move (e.g., "d2-d4", "e7-e8=Q")
     */
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
    
    /**
     * Analyzes a chess position and returns comprehensive evaluation data.
     * 
     * <p>This method provides detailed position analysis including evaluation score,
     * best move, analysis depth, and win percentage calculations. The analysis is
     * performed asynchronously using the chess-api.com service.
     * 
     * @param fen the FEN string representing the position to analyze
     * @param depth the analysis depth (max 18 for free tier)
     * @param thinkingTime maximum thinking time in milliseconds (max 100 for free tier)
     * @param callback the callback to receive the analysis result as JsonObject
     */
    public void analyzePosition(String fen, int depth, int thinkingTime, Consumer<JsonObject> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("fen", fen);
                requestBody.addProperty("depth", Math.min(depth, 18)); // Respect API limits
                requestBody.addProperty("maxThinkingTime", Math.min(thinkingTime, 100)); // Respect API limits
                
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
                    // Service unavailable, callback with null to trigger fallback
                    callback.accept(null);
                }
                
            } catch (IOException | InterruptedException e) {
                // Network error, callback with null to trigger fallback
                callback.accept(null);
            }
        });
    }
    
    /**
     * Evaluates a chess position and returns the evaluation result.
     * 
     * <p>This method provides comprehensive position evaluation including centipawn score,
     * evaluation text, analysis depth, and other metrics. The evaluation is performed 
     * using Stockfish 17 NNUE via the chess-api.com service.
     * 
     * @param fen the FEN string representing the position to evaluate
     * @param callback the callback to receive the evaluation result
     */
    public void evaluatePosition(String fen, Consumer<StockfishIntegration.EvaluationResult> callback) {
        analyzePosition(fen, DEFAULT_DEPTH, DEFAULT_THINKING_TIME, response -> {
            if (response != null && response.has("eval")) {
                StockfishIntegration.EvaluationResult result = new StockfishIntegration.EvaluationResult();
                
                // Extract evaluation data from web API response
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
                
                // Set reasonable defaults for web API (since these aren't provided)
                result.nodesSearched = 0; // Web API doesn't provide node count
                result.nodesPerSecond = 80_000_000; // Approximate based on service specs
                
                callback.accept(result);
            } else {
                // Fallback to local binary if web service fails
                StockfishIntegration.getInstance().evaluatePosition(fen, callback);
            }
        });
    }
    
    /**
     * Checks if the web API service is available.
     * 
     * <p>This method performs a quick connectivity test to determine if the chess-api.com
     * service is accessible. It can be used to decide whether to use web analysis or
     * fall back to local binary analysis.
     * 
     * @param callback the callback to receive the availability result
     */
    public void checkServiceAvailability(Consumer<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // Simple connectivity test with minimal position
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
    
    /**
     * Parses the detailed move information from the API response.
     * 
     * <p>This utility method extracts comprehensive move details from the chess-api.com
     * response, including move coordinates, piece information, and special move flags.
     * 
     * @param response the JsonObject response from the API
     * @return a formatted string with move details, or null if parsing fails
     */
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
}