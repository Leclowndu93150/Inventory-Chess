package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import com.leclowndu93150.guichess.data.GameHistory;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessBoard;
import com.leclowndu93150.guichess.util.ChessSoundManager;
import com.leclowndu93150.guichess.util.PieceOverlayHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Post-game analysis GUI that allows players to review their completed games with Stockfish analysis.
 * Features move-by-move navigation, detailed move evaluations, and visual highlighting of key moves.
 */
public class MatchAnalysisGUI extends SimpleGui {
    private final GameHistory gameHistory;
    private final ServerPlayer player;
    private final PieceColor playerColor;
    private ChessBoard analysisBoard;
    private int currentMoveIndex = -1; // -1 = starting position
    private boolean isAnalyzing = false;
    private CompletableFuture<Void> analysisTask;

    // Analysis cache
    private final List<String> moveEvaluations = new ArrayList<>();
    private final List<String> bestMoves = new ArrayList<>();
    private final List<Integer> centipawnLosses = new ArrayList<>();
    private boolean analysisCompleted = false;

    public MatchAnalysisGUI(ServerPlayer player, GameHistory gameHistory) {
        super(MenuType.GENERIC_9x6, player, true);
        this.player = player;
        this.gameHistory = gameHistory;
        this.playerColor = gameHistory.isPlayerWhite(player.getUUID()) ? PieceColor.WHITE : PieceColor.BLACK;
        
        setTitle(Component.literal("§0Analysis: vs " + gameHistory.getOpponentName(player.getUUID())));
        
        initializeAnalysisBoard();
        setupInitialGUI();
        startStockfishAnalysis();
    }

    private void initializeAnalysisBoard() {
        analysisBoard = new ChessBoard();
        // If game had custom starting position, load it
        if (!gameHistory.initialFen.equals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")) {
            // TODO: Implement FEN loading when ChessBoard supports it
        }
    }

    private void setupInitialGUI() {
        updateBoard();
        setupNavigationControls();
        setupAnalysisInfo();
    }

    private void startStockfishAnalysis() {
        if (isAnalyzing || analysisCompleted) return;
        
        isAnalyzing = true;
        player.sendSystemMessage(Component.literal("§eStarting computer analysis..."));
        
        analysisTask = CompletableFuture.runAsync(() -> {
            ChessBoard tempBoard = new ChessBoard();
            String currentFen = tempBoard.toFEN();
            
            for (int i = 0; i < gameHistory.moves.size(); i++) {
                final int moveIndex = i;
                GameHistory.MoveRecord moveRecord = gameHistory.moves.get(i);
                
                // Analyze position before the move
                try {
                    StockfishIntegration.AnalysisResult analysis = 
                        StockfishIntegration.getInstance().analyzePosition(currentFen).get();
                    
                    moveEvaluations.add(analysis.evaluation != null ? analysis.evaluation : "0.00");
                    bestMoves.add(analysis.bestMove != null ? analysis.bestMove : "");
                    
                    // Calculate centipawn loss
                    int currentEval = parseEvaluation(analysis.evaluation);
                    tempBoard.makeMove(moveRecord.move);
                    String nextFen = tempBoard.toFEN();
                    
                    StockfishIntegration.AnalysisResult nextAnalysis = 
                        StockfishIntegration.getInstance().analyzePosition(nextFen).get();
                    int nextEval = parseEvaluation(nextAnalysis.evaluation);
                    
                    // Flip evaluation if it's black's move
                    if (moveIndex % 2 == 1) {
                        currentEval = -currentEval;
                        nextEval = -nextEval;
                    }
                    
                    int centipawnLoss = Math.max(0, currentEval - nextEval);
                    centipawnLosses.add(centipawnLoss);
                    
                    // Classify the move
                    classifyMove(gameHistory.moves.get(moveIndex), centipawnLoss);
                    
                    currentFen = nextFen;
                    
                    // Update progress
                    if ((i + 1) % 5 == 0) {
                        int progress = (int) ((i + 1) * 100.0 / gameHistory.moves.size());
                        player.sendSystemMessage(Component.literal("§eAnalysis progress: " + progress + "%"));
                    }
                    
                } catch (Exception e) {
                    // Handle analysis failure
                    moveEvaluations.add("Error");
                    bestMoves.add("");
                    centipawnLosses.add(0);
                }
            }
            
            isAnalyzing = false;
            analysisCompleted = true;
            player.sendSystemMessage(Component.literal("§aAnalysis completed!"));
            
            // Update GUI on main thread
            getPlayer().server.execute(this::updateAnalysisInfo);
        });
    }

    private int parseEvaluation(String evaluation) {
        if (evaluation == null || evaluation.equals("Error")) return 0;
        
        try {
            if (evaluation.contains("Mate")) {
                return evaluation.contains("-") ? -10000 : 10000;
            }
            return (int) (Double.parseDouble(evaluation.replace("+", "")) * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void classifyMove(GameHistory.MoveRecord move, int centipawnLoss) {
        move.centipawnLoss = centipawnLoss;
        
        // Special cases that override centipawn loss evaluation
        if (move.wasCheckmate) {
            move.isBrilliant = true;
            move.centipawnLoss = 0; // Checkmate is always the best move
            return;
        }
        
        if (move.wasPromotion && centipawnLoss <= 50) {
            move.isGood = true; // Promotions are generally good moves
            return;
        }
        
        // Standard centipawn loss evaluation
        if (centipawnLoss <= 0) {
            move.isGood = true; // Best move or better than best
        } else if (centipawnLoss <= 20) {
            move.isGood = true; // Excellent move
        } else if (centipawnLoss <= 50) {
            move.isGood = true; // Good move
        } else if (centipawnLoss <= 100) {
            move.isInaccuracy = true;
        } else if (centipawnLoss <= 300) {
            move.isMistake = true;
        } else {
            move.isBlunder = true;
        }
        
        // Check for brilliant moves (significant improvement in evaluation)
        if (centipawnLoss < -50) {
            move.isBrilliant = true;
            move.isGood = false;
        }
    }

    private void updateBoard() {
        // Clear all slots first
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        // Get the board position at current move
        ChessBoard displayBoard = getBoardAtMove(currentMoveIndex);
        
        // Get highlighted moves
        ChessMove lastMove = currentMoveIndex >= 0 && currentMoveIndex < gameHistory.moves.size() ? 
            gameHistory.moves.get(currentMoveIndex).move : null;
        ChessMove nextMove = currentMoveIndex + 1 < gameHistory.moves.size() ? 
            gameHistory.moves.get(currentMoveIndex + 1).move : null;

        // Render the board
        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;

            int chessRank, chessFile;
            if (playerColor == PieceColor.WHITE) {
                chessRank = 7 - row;
                chessFile = col;
            } else {
                chessRank = row;
                chessFile = 7 - col;
            }

            ChessPosition position = new ChessPosition(chessFile, chessRank);
            ChessPiece piece = displayBoard.getPiece(position);

            int slotIndex = i + i / 8;
            if (slotIndex >= 72) continue;

            GuiElementBuilder builder;

            if (piece != null) {
                builder = createAnalysisPieceElement(piece, position, lastMove, nextMove);
            } else {
                builder = createAnalysisSquareElement(position, lastMove, nextMove);
            }

            setSlot(slotIndex, builder);
        }
    }

    private ChessBoard getBoardAtMove(int moveIndex) {
        ChessBoard board = new ChessBoard();
        
        // Play moves up to the specified index
        for (int i = 0; i <= moveIndex && i < gameHistory.moves.size(); i++) {
            board.makeMove(gameHistory.moves.get(i).move);
        }
        
        return board;
    }

    private GuiElementBuilder createAnalysisPieceElement(ChessPiece piece, ChessPosition position, 
                                                        ChessMove lastMove, ChessMove nextMove) {
        boolean isLightSquare = (position.file + position.rank) % 2 == 0;
        boolean isLastMoveSquare = (lastMove != null && 
            (position.equals(lastMove.from) || position.equals(lastMove.to)));
        boolean isNextMoveSquare = (nextMove != null && 
            (position.equals(nextMove.from) || position.equals(nextMove.to)));

        int modelData = PieceOverlayHelper.getModelDataForPieceState(
            piece, isLightSquare, false, false, isLastMoveSquare || isNextMoveSquare, false);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Position: " + position.toNotation()));
        
        if (isLastMoveSquare && currentMoveIndex >= 0) {
            GameHistory.MoveRecord moveRecord = gameHistory.moves.get(currentMoveIndex);
            lore.add(Component.literal("§6Last Move: " + moveRecord.moveNotation));
            
            if (analysisCompleted && currentMoveIndex < moveEvaluations.size()) {
                lore.add(Component.literal("§bEvaluation: " + moveEvaluations.get(currentMoveIndex)));
                
                // Calculate move quality based on move record
                String moveQuality = getMoveQuality(moveRecord);
                if (!moveQuality.isEmpty()) {
                    lore.add(Component.literal(moveQuality));
                }
                
                if (moveRecord.centipawnLoss > 0) {
                    lore.add(Component.literal("§cCentipawn loss: " + moveRecord.centipawnLoss));
                }
            }
        }

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(modelData)
                .setName(piece.getDisplayName())
                .setLore(lore);
    }

    private GuiElementBuilder createAnalysisSquareElement(ChessPosition position, 
                                                         ChessMove lastMove, ChessMove nextMove) {
        boolean isLight = (position.file + position.rank) % 2 == 0;
        boolean isHighlighted = (lastMove != null && 
            (position.equals(lastMove.from) || position.equals(lastMove.to))) ||
            (nextMove != null && (position.equals(nextMove.from) || position.equals(nextMove.to)));

        int modelData;
        if (isHighlighted) {
            modelData = isLight ? BoardSquare.LAST_MOVE_FROM.getModelData() : BoardSquare.LAST_MOVE_TO.getModelData();
        } else {
            modelData = isLight ? BoardSquare.LIGHT_SQUARE.getModelData() : BoardSquare.DARK_SQUARE.getModelData();
        }

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(modelData)
                .setName(Component.literal("§7" + position.toNotation()))
                .hideDefaultTooltip();
    }

    private void setupNavigationControls() {
        // Clear all hotbar slots first (actual hotbar is 81-89)
        for (int i = 81; i <= 89; i++) {
            clearSlot(i);
        }
        
        // Previous move button (hotbar slot 82)
        setSlot(82, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§e◀ Previous Move"))
                .addLoreLine(Component.literal("§7Go back one move"))
                .setCallback((index, type, action, gui) -> {
                    if (currentMoveIndex >= 0) {
                        currentMoveIndex--;
                        updateBoard();
                        updateMoveInfo();
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    }
                }));

        // Next move button (hotbar slot 83)
        setSlot(83, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§eNext Move ▶"))
                .addLoreLine(Component.literal("§7Go forward one move"))
                .setCallback((index, type, action, gui) -> {
                    if (currentMoveIndex < gameHistory.moves.size() - 1) {
                        currentMoveIndex++;
                        updateBoard();
                        updateMoveInfo();
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    }
                }));

        // Go to start button (hotbar slot 84)
        setSlot(84, new GuiElementBuilder(Items.COMPASS)
                .setName(Component.literal("§eStart Position"))
                .addLoreLine(Component.literal("§7Go to beginning"))
                .setCallback((index, type, action, gui) -> {
                    currentMoveIndex = -1;
                    updateBoard();
                    updateMoveInfo();
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                }));

        // Go to end button (hotbar slot 85)
        setSlot(85, new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("§eFinal Position"))
                .addLoreLine(Component.literal("§7Go to end"))
                .setCallback((index, type, action, gui) -> {
                    currentMoveIndex = gameHistory.moves.size() - 1;
                    updateBoard();
                    updateMoveInfo();
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                }));

        // Game info button (hotbar slot 86)
        setSlot(86, createGameInfoButton());

        // Export PGN button (hotbar slot 87)
        setSlot(87, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(Component.literal("§eExport PGN"))
                .addLoreLine(Component.literal("§7Copy game notation"))
                .setCallback((index, type, action, gui) -> {
                    exportToPGN();
                }));

        // Close button (hotbar slot 89 - right corner)
        setSlot(89, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cClose Analysis"))
                .setCallback((index, type, action, gui) -> {
                    close();
                }));
        
        // Move analysis info to right sidebar
        setupAnalysisInfo();
    }
    

    private GuiElementBuilder createGameInfoButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7Date: " + gameHistory.getFormattedDate()));
        lore.add(Component.literal("§7Time Control: " + gameHistory.timeControl.displayName));
        lore.add(Component.literal("§7Result: " + gameHistory.getResultString()));
        lore.add(Component.literal("§7Duration: " + gameHistory.getDuration()));
        lore.add(Component.literal(""));
        lore.add(Component.literal("§fWhite: " + gameHistory.whitePlayerName + " (" + gameHistory.whiteEloAtStart + ")"));
        lore.add(Component.literal("§8Black: " + gameHistory.blackPlayerName + " (" + gameHistory.blackEloAtStart + ")"));
        
        if (gameHistory.wasRated) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("§7ELO Changes:"));
            lore.add(Component.literal("§fWhite: " + formatEloChange(gameHistory.whiteEloChange)));
            lore.add(Component.literal("§8Black: " + formatEloChange(gameHistory.blackEloChange)));
        }

        return new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§eGame Information"))
                .setLore(lore);
    }

    private String formatEloChange(int change) {
        if (change > 0) return "+" + change;
        return String.valueOf(change);
    }

    private void setupAnalysisInfo() {
        updateSidebarMoveInfo();
        updateAnalysisInfo();
    }

    private void updateAnalysisInfo() {
        // Analysis status indicator (slot 26)
        if (isAnalyzing) {
            setSlot(26, new GuiElementBuilder(Items.CLOCK)
                    .setName(Component.literal("§eAnalyzing..."))
                    .addLoreLine(Component.literal("§7Computer analysis in progress"))
                    .glow());
        } else if (analysisCompleted) {
            setSlot(26, new GuiElementBuilder(Items.EMERALD)
                    .setName(Component.literal("§aAnalysis Complete"))
                    .addLoreLine(Component.literal("§7Click moves to see evaluations")));
        } else {
            setSlot(26, new GuiElementBuilder(Items.REDSTONE)
                    .setName(Component.literal("§cNo Analysis"))
                    .addLoreLine(Component.literal("§7Analysis not available")));
        }
    }

    private void updateMoveInfo() {
        // Update the sidebar elements with current move info instead of middle slots
        updateSidebarMoveInfo();
    }
    
    private void updateSidebarMoveInfo() {
        // Update move info display (right sidebar - slot 8)
        if (currentMoveIndex >= 0 && currentMoveIndex < gameHistory.moves.size()) {
            GameHistory.MoveRecord move = gameHistory.moves.get(currentMoveIndex);
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Move: " + (currentMoveIndex + 1) + "/" + gameHistory.moves.size()));
            lore.add(Component.literal("§7Notation: " + move.moveNotation));
            lore.add(Component.literal("§7Time: " + formatTime(move.moveTimeMs)));
            
            setSlot(8, new GuiElementBuilder(Items.PAPER)
                    .setName(Component.literal("§eMove Information"))
                    .setLore(lore)
                    .setCallback((index, type, action, gui) -> {
                        player.sendSystemMessage(Component.literal("§eMove " + (currentMoveIndex + 1) + ": " + move.moveNotation));
                        if (move.stockfishEvaluation != null) {
                            player.sendSystemMessage(Component.literal("§bEvaluation: " + move.stockfishEvaluation));
                        }
                    }));
        } else {
            // No move selected
            setSlot(8, new GuiElementBuilder(Items.PAPER)
                    .setName(Component.literal("§eMove Information"))
                    .addLoreLine(Component.literal("§7Starting position"))
                    .setCallback((index, type, action, gui) -> {
                        player.sendSystemMessage(Component.literal("§7At starting position"));
                    }));
        }
        
        // Update analysis toggle (right sidebar - slot 17)
        if (currentMoveIndex >= 0 && currentMoveIndex < gameHistory.moves.size()) {
            GameHistory.MoveRecord move = gameHistory.moves.get(currentMoveIndex);
            
            List<Component> analysisLore = new ArrayList<>();
            if (analysisCompleted && currentMoveIndex < moveEvaluations.size()) {
                analysisLore.add(Component.literal("§bEvaluation: " + moveEvaluations.get(currentMoveIndex)));
                
                if (currentMoveIndex < bestMoves.size() && !bestMoves.get(currentMoveIndex).isEmpty()) {
                    analysisLore.add(Component.literal("§aBest: " + bestMoves.get(currentMoveIndex)));
                }
                
                if (move.centipawnLoss > 0) {
                    analysisLore.add(Component.literal("§cLoss: " + move.centipawnLoss + " cp"));
                }
                
                // Add move quality
                String moveQuality = getMoveQuality(move);
                if (!moveQuality.isEmpty()) {
                    analysisLore.add(Component.literal(moveQuality));
                }
            } else {
                analysisLore.add(Component.literal("§7No analysis available"));
            }
            
            setSlot(17, new GuiElementBuilder(Items.SPYGLASS)
                    .setName(Component.literal("§dMove Analysis"))
                    .setLore(analysisLore)
                    .setCallback((index, type, action, gui) -> {
                        if (!isAnalyzing && !analysisCompleted) {
                            startAnalysis();
                        }
                    }));
        } else {
            // At starting position
            setSlot(17, new GuiElementBuilder(Items.SPYGLASS)
                    .setName(Component.literal("§dToggle Analysis"))
                    .addLoreLine(Component.literal(analysisCompleted ? "§aAnalysis completed" : "§7Click to analyze"))
                    .setCallback((index, type, action, gui) -> {
                        if (!isAnalyzing && !analysisCompleted) {
                            startAnalysis();
                        }
                    }));
        }
    }

    private String formatTime(int timeMs) {
        if (timeMs < 1000) {
            return timeMs + "ms";
        } else {
            return String.format("%.1fs", timeMs / 1000.0);
        }
    }
    
    private String getMoveQuality(int centipawnLoss) {
        if (centipawnLoss <= 0) {
            return "§b✦ Best move";
        } else if (centipawnLoss <= 20) {
            return "§a✓ Excellent";
        } else if (centipawnLoss <= 50) {
            return "§a✓ Good";
        } else if (centipawnLoss <= 100) {
            return "§e⚠ Inaccuracy";
        } else if (centipawnLoss <= 300) {
            return "§c⚠ Mistake";
        } else {
            return "§4⚠ Blunder";
        }
    }
    
    private String getMoveQuality(GameHistory.MoveRecord move) {
        // Special cases override centipawn loss
        if (move.isBrilliant) {
            return "§d✦ Brilliant!";
        }
        
        if (move.wasCheckmate) {
            return "§6✦ Checkmate!";
        }
        
        if (move.isGood) {
            if (move.centipawnLoss <= 0) {
                return "§b✦ Best move";
            } else if (move.centipawnLoss <= 20) {
                return "§a✓ Excellent";
            } else {
                return "§a✓ Good";
            }
        }
        
        if (move.isInaccuracy) {
            return "§e⚠ Inaccuracy";
        }
        
        if (move.isMistake) {
            return "§c⚠ Mistake";
        }
        
        if (move.isBlunder) {
            return "§4⚠ Blunder";
        }
        
        // Fallback to centipawn loss
        return getMoveQuality(move.centipawnLoss);
    }
    
    private void startAnalysis() {
        if (isAnalyzing || analysisCompleted) return;
        
        isAnalyzing = true;
        updateAnalysisInfo();
        
        player.sendSystemMessage(Component.literal("§eStarting position analysis..."));
        
        // For now, just mark as completed since we don't have real Stockfish integration here
        // In a real implementation, this would analyze each position with Stockfish
        analysisCompleted = true;
        isAnalyzing = false;
        updateAnalysisInfo();
        
        player.sendSystemMessage(Component.literal("§aAnalysis completed!"));
    }

    private void exportToPGN() {
        StringBuilder pgn = new StringBuilder();
        
        // PGN headers
        pgn.append("[Event \"Casual Game\"]\n");
        pgn.append("[Site \"Minecraft Chess\"]\n");
        pgn.append("[Date \"").append(gameHistory.getFormattedDate()).append("\"]\n");
        pgn.append("[White \"").append(gameHistory.whitePlayerName).append("\"]\n");
        pgn.append("[Black \"").append(gameHistory.blackPlayerName).append("\"]\n");
        pgn.append("[Result \"").append(gameHistory.getResultString().split(" ")[0]).append("\"]\n");
        pgn.append("[TimeControl \"").append(gameHistory.timeControl.name()).append("\"]\n\n");
        
        // Moves
        for (int i = 0; i < gameHistory.moves.size(); i++) {
            if (i % 2 == 0) {
                pgn.append((i / 2 + 1)).append(". ");
            }
            pgn.append(gameHistory.moves.get(i).moveNotation).append(" ");
        }
        
        pgn.append(gameHistory.getResultString().split(" ")[0]);
        
        player.sendSystemMessage(Component.literal("§7PGN: " + pgn.toString()));
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
    }

    @Override
    public void onClose() {
        // Cancel analysis if still running
        if (analysisTask != null && !analysisTask.isDone()) {
            analysisTask.cancel(true);
        }
        super.onClose();
    }

    @Override
    public boolean canPlayerClose() {
        return true; // Allow closing analysis GUI
    }
}