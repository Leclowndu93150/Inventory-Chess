package com.leclowndu93150.guichess.engine.integration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages Stockfish engine access.
 * Tries web API first, falls back to local binary.
 */
public class StockfishEngineManager implements IStockfishEngine {
    private static StockfishEngineManager instance;
    
    private final StockfishWebIntegration webEngine;
    private final StockfishIntegration binaryEngine;
    
    private final AtomicBoolean webAvailable = new AtomicBoolean(false);
    private final AtomicBoolean binaryAvailable = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private volatile IStockfishEngine activeEngine;
    
    private StockfishEngineManager() {
        this.webEngine = StockfishWebIntegration.getInstance();
        this.binaryEngine = StockfishIntegration.getInstance();
        
        checkEngineAvailability();
    }
    
    public static StockfishEngineManager getInstance() {
        if (instance == null) {
            synchronized (StockfishEngineManager.class) {
                if (instance == null) {
                    instance = new StockfishEngineManager();
                }
            }
        }
        return instance;
    }
    
    private void checkEngineAvailability() {
        CompletableFuture.allOf(
            webEngine.waitUntilReady().thenAccept(webAvailable::set),
            binaryEngine.waitUntilReady().thenAccept(binaryAvailable::set)
        ).thenRun(() -> {
            selectActiveEngine();
            initialized.set(true);
        });
    }
    
    private void selectActiveEngine() {
        if (webAvailable.get()) {
            activeEngine = webEngine;
        } else if (binaryAvailable.get()) {
            activeEngine = binaryEngine;
        } else {
            activeEngine = binaryEngine; // Default fallback even if not ready
        }
    }
    
    private IStockfishEngine getActiveEngine() {
        if (!initialized.get()) {
            return binaryEngine; // Use binary as immediate fallback
        }
        return activeEngine;
    }
    
    private CompletableFuture<IStockfishEngine> getActiveEngineAsync() {
        if (initialized.get()) {
            return CompletableFuture.completedFuture(activeEngine);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            while (!initialized.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return binaryEngine;
                }
            }
            return activeEngine;
        });
    }
    
    @Override
    public boolean isAvailable() {
        return webAvailable.get() || binaryAvailable.get();
    }
    
    @Override
    public CompletableFuture<Boolean> waitUntilReady() {
        if (initialized.get()) {
            return CompletableFuture.completedFuture(isAvailable());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            while (!initialized.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return isAvailable();
        });
    }
    
    @Override
    public CompletableFuture<String> requestHint(String fen) {
        return getActiveEngineAsync().thenCompose(engine -> {
            return engine.requestHint(fen).exceptionally(throwable -> {
                if (engine == webEngine && binaryAvailable.get()) {
                    return binaryEngine.requestHint(fen).join();
                }
                return "Unable to analyze position: " + throwable.getMessage();
            });
        });
    }
    
    @Override
    public void requestHint(String fen, Consumer<String> callback) {
        getActiveEngineAsync().thenAccept(engine -> {
            engine.requestHint(fen, result -> {
                if (result.startsWith("Unable to analyze") && engine == webEngine && binaryAvailable.get()) {
                    binaryEngine.requestHint(fen, callback);
                } else {
                    callback.accept(result);
                }
            });
        });
    }
    
    @Override
    public CompletableFuture<StockfishIntegration.EvaluationResult> evaluatePosition(String fen) {
        return getActiveEngineAsync().thenCompose(engine -> {
            return engine.evaluatePosition(fen).exceptionally(throwable -> {
                if (engine == webEngine && binaryAvailable.get()) {
                    return binaryEngine.evaluatePosition(fen).join();
                }
                return new StockfishIntegration.EvaluationResult("Unable to evaluate position: " + throwable.getMessage());
            });
        });
    }
    
    @Override
    public void evaluatePosition(String fen, Consumer<StockfishIntegration.EvaluationResult> callback) {
        getActiveEngineAsync().thenAccept(engine -> {
            engine.evaluatePosition(fen, result -> {
                if (result.error != null && engine == webEngine && binaryAvailable.get()) {
                    binaryEngine.evaluatePosition(fen, callback);
                } else {
                    callback.accept(result);
                }
            });
        });
    }
    
    @Override
    public CompletableFuture<StockfishIntegration.AnalysisResult> analyzePosition(String fen) {
        return getActiveEngineAsync().thenCompose(engine -> {
            return engine.analyzePosition(fen).exceptionally(throwable -> {
                if (engine == webEngine && binaryAvailable.get()) {
                    return binaryEngine.analyzePosition(fen).join();
                }
                return new StockfishIntegration.AnalysisResult("Unable to analyze position: " + throwable.getMessage());
            });
        });
    }
    
    @Override
    public void analyzePosition(String fen, Consumer<StockfishIntegration.AnalysisResult> callback) {
        getActiveEngineAsync().thenAccept(engine -> {
            engine.analyzePosition(fen, result -> {
                if (result.error != null && engine == webEngine && binaryAvailable.get()) {
                    binaryEngine.analyzePosition(fen, callback);
                } else {
                    callback.accept(result);
                }
            });
        });
    }
    
    @Override
    public void setSkillLevel(int level) {
        webEngine.setSkillLevel(level);
        binaryEngine.setSkillLevel(level);
    }
    
    @Override
    public void setAnalysisDepth(int depth) {
        webEngine.setAnalysisDepth(depth);
        binaryEngine.setAnalysisDepth(depth);
    }
    
    @Override
    public void setAnalysisTime(int timeMs) {
        webEngine.setAnalysisTime(timeMs);
        binaryEngine.setAnalysisTime(timeMs);
    }
    
    @Override
    public void shutdown() {
        webEngine.shutdown();
        binaryEngine.shutdown();
        initialized.set(false);
    }
    
    /**
     * Forces a recheck of engine availability and updates the active engine.
     */
    public void refreshEngineAvailability() {
        initialized.set(false);
        checkEngineAvailability();
    }
    
    /**
     * Gets information about current engine status.
     */
    public String getEngineStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Web Engine: ").append(webAvailable.get() ? "Available" : "Unavailable").append("\n");
        status.append("Binary Engine: ").append(binaryAvailable.get() ? "Available" : "Unavailable").append("\n");
        status.append("Active Engine: ");
        
        if (activeEngine == webEngine) {
            status.append("Web");
        } else if (activeEngine == binaryEngine) {
            status.append("Binary");
        } else {
            status.append("None");
        }
        
        return status.toString();
    }
}