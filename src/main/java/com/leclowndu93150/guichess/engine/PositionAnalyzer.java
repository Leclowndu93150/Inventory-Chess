package com.leclowndu93150.guichess.engine;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.game.ChessBoard;
import com.leclowndu93150.guichess.game.ChessGame;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Additional analysis utilities
class PositionAnalyzer {
    private final StockfishIntegration stockfish;

    public PositionAnalyzer() {
        this.stockfish = StockfishIntegration.getInstance();
    }

    public void analyzeGameAccuracy(ChessGame game, Consumer<GameAnalysis> callback) {
        if (!stockfish.isAvailable()) {
            callback.accept(new GameAnalysis("Stockfish not available"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            GameAnalysis analysis = new GameAnalysis();
            analysis.gameId = game.getGameId();
            analysis.moves = game.getBoard().getMoveHistory();

            // Analyze each position for accuracy
            ChessBoard tempBoard = new ChessBoard();
            for (int i = 0; i < analysis.moves.size(); i++) {
                ChessMove move = analysis.moves.get(i);
                String fen = tempBoard.toFEN();

                // Get best move for this position
                CompletableFuture<StockfishIntegration.AnalysisResult> future = new CompletableFuture<>();
                stockfish.analyzePosition(fen, future::complete);

                try {
                    StockfishIntegration.AnalysisResult result = future.get(5, TimeUnit.SECONDS);

                    MoveAnalysis moveAnalysis = new MoveAnalysis();
                    moveAnalysis.moveNumber = i + 1;
                    moveAnalysis.move = move;
                    moveAnalysis.bestMove = result.bestMove;
                    moveAnalysis.evaluation = result.evaluation;

                    // Calculate accuracy based on difference from best move
                    if (move.toNotation().equals(result.bestMove)) {
                        moveAnalysis.accuracy = 100.0;
                        moveAnalysis.classification = "Best";
                    } else {
                        // This would require more sophisticated analysis
                        moveAnalysis.accuracy = 85.0; // Placeholder
                        moveAnalysis.classification = "Good";
                    }

                    analysis.moveAnalyses.add(moveAnalysis);

                    tempBoard.makeMove(move);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Calculate overall accuracy
            double totalAccuracy = analysis.moveAnalyses.stream()
                    .mapToDouble(ma -> ma.accuracy)
                    .average()
                    .orElse(0.0);

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

    public static class GameAnalysis {
        public java.util.UUID gameId;
        public java.util.List<ChessMove> moves;
        public java.util.List<MoveAnalysis> moveAnalyses = new java.util.ArrayList<>();
        public double whiteAccuracy;
        public double blackAccuracy;
        public String error;

        public GameAnalysis() {}
        public GameAnalysis(String error) { this.error = error; }
    }

    public static class MoveAnalysis {
        public int moveNumber;
        public ChessMove move;
        public String bestMove;
        public String evaluation;
        public double accuracy;
        public String classification; // "Best", "Excellent", "Good", "Inaccuracy", "Mistake", "Blunder"
    }
}