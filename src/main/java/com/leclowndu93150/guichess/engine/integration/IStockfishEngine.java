package com.leclowndu93150.guichess.engine.integration;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Common interface for Stockfish chess engine integrations.
 * 
 * <p>Provides a unified API for chess position analysis, evaluation, and move hints
 * regardless of the underlying implementation (local binary or web service).
 */
public interface IStockfishEngine {
    
    /**
     * Checks if the engine is available and ready for use.
     * 
     * @return true if the engine is available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Waits until the engine is ready for use.
     * 
     * @return CompletableFuture that completes with true when ready, false if initialization failed
     */
    CompletableFuture<Boolean> waitUntilReady();
    
    /**
     * Requests the best move hint for a given position.
     * 
     * @param fen the position in FEN notation
     * @return CompletableFuture containing the best move in UCI notation
     */
    CompletableFuture<String> requestHint(String fen);
    
    /**
     * Requests the best move hint for a given position with callback.
     * 
     * @param fen the position in FEN notation
     * @param callback called with the best move in UCI notation
     */
    void requestHint(String fen, Consumer<String> callback);
    
    /**
     * Evaluates a chess position.
     * 
     * @param fen the position in FEN notation
     * @return CompletableFuture containing the evaluation result
     */
    CompletableFuture<StockfishIntegration.EvaluationResult> evaluatePosition(String fen);
    
    /**
     * Evaluates a chess position with callback.
     * 
     * @param fen the position in FEN notation
     * @param callback called with the evaluation result
     */
    void evaluatePosition(String fen, Consumer<StockfishIntegration.EvaluationResult> callback);
    
    /**
     * Performs full analysis of a chess position.
     * 
     * @param fen the position in FEN notation
     * @return CompletableFuture containing the analysis result
     */
    CompletableFuture<StockfishIntegration.AnalysisResult> analyzePosition(String fen);
    
    /**
     * Performs full analysis of a chess position with callback.
     * 
     * @param fen the position in FEN notation
     * @param callback called with the analysis result
     */
    void analyzePosition(String fen, Consumer<StockfishIntegration.AnalysisResult> callback);
    
    /**
     * Sets the skill level for the engine.
     * 
     * @param level skill level (-20 to 20, where 20 is strongest)
     */
    void setSkillLevel(int level);
    
    /**
     * Sets the analysis depth.
     * 
     * @param depth the search depth (typically 10-20)
     */
    void setAnalysisDepth(int depth);
    
    /**
     * Sets the analysis time limit.
     * 
     * @param timeMs the time limit in milliseconds
     */
    void setAnalysisTime(int timeMs);
    
    /**
     * Shuts down the engine and releases resources.
     */
    void shutdown();
}