package com.leclowndu93150.guichess.engine.integration;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for Stockfish engine implementations.
 */
public interface IStockfishEngine {
    
    /**
     * Checks if engine is ready.
     */
    boolean isAvailable();
    
    /**
     * Waits for engine initialization.
     */
    CompletableFuture<Boolean> waitUntilReady();
    
    /**
     * Gets best move for position.
     * @param fen position in FEN notation
     */
    CompletableFuture<String> requestHint(String fen);
    
    /**
     * Gets best move with callback.
     * @param fen position in FEN notation
     * @param callback receives best move
     */
    void requestHint(String fen, Consumer<String> callback);
    
    /**
     * Evaluates position strength.
     * @param fen position in FEN notation
     */
    CompletableFuture<StockfishIntegration.EvaluationResult> evaluatePosition(String fen);
    
    /**
     * Evaluates position with callback.
     * @param fen position in FEN notation
     * @param callback receives evaluation
     */
    void evaluatePosition(String fen, Consumer<StockfishIntegration.EvaluationResult> callback);
    
    /**
     * Full position analysis.
     * @param fen position in FEN notation
     */
    CompletableFuture<StockfishIntegration.AnalysisResult> analyzePosition(String fen);
    
    /**
     * Full analysis with callback.
     * @param fen position in FEN notation
     * @param callback receives analysis
     */
    void analyzePosition(String fen, Consumer<StockfishIntegration.AnalysisResult> callback);
    
    /**
     * Sets engine difficulty.
     * @param level -20 to 20, higher is stronger
     */
    void setSkillLevel(int level);
    
    /**
     * Sets search depth.
     * @param depth search depth (10-20)
     */
    void setAnalysisDepth(int depth);
    
    /**
     * Sets analysis time limit.
     * @param timeMs time in milliseconds
     */
    void setAnalysisTime(int timeMs);
    
    /**
     * Shuts down engine.
     */
    void shutdown();
}