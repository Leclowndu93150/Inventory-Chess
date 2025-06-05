package com.leclowndu93150.guichess.engine.analysis;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.engine.integration.StockfishIntegration;
import com.leclowndu93150.guichess.game.core.ChessBoard;
import com.leclowndu93150.guichess.game.core.ChessGame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Analyzes chess positions and games using Stockfish engine.
 */
public class PositionAnalyzer {
    private final StockfishEngineManager stockfish;

    public PositionAnalyzer() {
        this.stockfish = StockfishEngineManager.getInstance();
    }

    /**
     * Analyzes game accuracy for all moves in the game.
     * 
     * @param game the chess game to analyze
     * @param callback callback to receive the analysis result
     */
    public void analyzeGameAccuracy(ChessGame game, Consumer<GameAnalysis> callback) {
        if (!stockfish.isAvailable()) {
            callback.accept(new GameAnalysis("Stockfish not available"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            GameAnalysis analysis = new GameAnalysis();
            analysis.gameId = game.getGameId();
            analysis.moves = game.getBoard().getMoveHistory();

            ChessBoard tempBoard = new ChessBoard();
            for (int i = 0; i < analysis.moves.size(); i++) {
                ChessMove move = analysis.moves.get(i);
                String fen = tempBoard.toFEN();

                CompletableFuture<StockfishIntegration.AnalysisResult> future = new CompletableFuture<>();
                stockfish.analyzePosition(fen, future::complete);

                try {
                    StockfishIntegration.AnalysisResult result = future.get(5, TimeUnit.SECONDS);

                    MoveAnalysis moveAnalysis = new MoveAnalysis();
                    moveAnalysis.moveNumber = i + 1;
                    moveAnalysis.move = move;
                    moveAnalysis.bestMove = result.bestMove;
                    moveAnalysis.evaluation = result.evaluation;

                    if (move.toNotation().equals(result.bestMove)) {
                        moveAnalysis.accuracy = 100.0;
                        moveAnalysis.classification = "Best";
                    } else {
                        moveAnalysis.accuracy = 85.0;
                        moveAnalysis.classification = "Good";
                    }

                    analysis.moveAnalyses.add(moveAnalysis);
                    tempBoard.makeMove(move);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            analysis.whiteAccuracy = analysis.moveAnalyses.stream()
                    .filter(ma -> ma.moveNumber % 2 == 1)
                    .mapToDouble(ma -> ma.accuracy)
                    .average()
                    .orElse(0.0);

            analysis.blackAccuracy = analysis.moveAnalyses.stream()
                    .filter(ma -> ma.moveNumber % 2 == 0)
                    .mapToDouble(ma -> ma.accuracy)
                    .average()
                    .orElse(0.0);

            callback.accept(analysis);
        });
    }

    /**
     * Contains complete analysis of a chess game.
     */
    public static class GameAnalysis {
        public UUID gameId;
        public List<ChessMove> moves;
        public List<MoveAnalysis> moveAnalyses = new ArrayList<>();
        public double whiteAccuracy;
        public double blackAccuracy;
        public String error;

        public GameAnalysis() {}
        
        public GameAnalysis(String error) { 
            this.error = error; 
        }
    }

    /**
     * Analysis data for a single move.
     */
    public static class MoveAnalysis {
        public int moveNumber;
        public ChessMove move;
        public String bestMove;
        public String evaluation;
        public double accuracy;
        public String classification;
    }
}