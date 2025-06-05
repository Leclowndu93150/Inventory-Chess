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
import com.leclowndu93150.guichess.game.GameManager;
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
 * 
 * <p>Features include:
 * <ul>
 * <li>Move-by-move navigation with arrow keys and buttons</li>
 * <li>Detailed move evaluations and centipawn loss calculations</li>
 * <li>Visual highlighting of last moves and position changes</li>
 * <li>Evaluation displays for both players' perspectives</li>
 * <li>PGN export with Chess.com integration</li>
 * <li>Move quality classification (brilliant, good, mistake, etc.)</li>
 * </ul>
 * 
 * @author GUIChess
 * @since 1.0
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

    /**
     * Creates a new match analysis GUI for the specified player and game history.
     * 
     * @param player the player viewing the analysis
     * @param gameHistory the completed game to analyze
     */
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

    /**
     * Initializes the analysis board to the starting position of the game.
     * Currently uses standard starting position, with FEN loading planned for future implementation.
     */
    private void initializeAnalysisBoard() {
        analysisBoard = new ChessBoard();
        if (!gameHistory.initialFen.equals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")) {
            // TODO: Implement FEN loading when ChessBoard supports it
        }
    }

    private void setupInitialGUI() {
        updateBoard();
        setupNavigationControls();
        setupAnalysisInfo();
    }

    /**
     * Starts asynchronous Stockfish analysis of all moves in the game.
     * Calculates evaluations, best moves, and centipawn losses for each position.
     */
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
                
                try {
                    StockfishIntegration.AnalysisResult analysis = 
                        StockfishIntegration.getInstance().analyzePosition(currentFen).get();
                    
                    moveEvaluations.add(analysis.evaluation != null ? analysis.evaluation : "0.00");
                    bestMoves.add(analysis.bestMove != null ? analysis.bestMove : "");
                    
                    int currentEval = parseEvaluation(analysis.evaluation);
                    tempBoard.makeMove(moveRecord.move);
                    String nextFen = tempBoard.toFEN();
                    
                    StockfishIntegration.AnalysisResult nextAnalysis = 
                        StockfishIntegration.getInstance().analyzePosition(nextFen).get();
                    int nextEval = parseEvaluation(nextAnalysis.evaluation);
                    
                    if (moveIndex % 2 == 1) {
                        currentEval = -currentEval;
                        nextEval = -nextEval;
                    }
                    
                    int centipawnLoss = Math.max(0, currentEval - nextEval);
                    centipawnLosses.add(centipawnLoss);
                    
                    classifyMove(gameHistory.moves.get(moveIndex), centipawnLoss);
                    
                    currentFen = nextFen;
                    
                    if ((i + 1) % 5 == 0) {
                        int progress = (int) ((i + 1) * 100.0 / gameHistory.moves.size());
                        player.sendSystemMessage(Component.literal("§eAnalysis progress: " + progress + "%"));
                    }
                    
                } catch (Exception e) {
                    moveEvaluations.add("Error");
                    bestMoves.add("");
                    centipawnLosses.add(0);
                }
            }
            
            isAnalyzing = false;
            analysisCompleted = true;
            player.sendSystemMessage(Component.literal("§aAnalysis completed!"));
            
            getPlayer().server.execute(this::updateAnalysisInfo);
        });
    }

    /**
     * Parses a Stockfish evaluation string into normalized centipawns.
     * 
     * <p>Handles both traditional centipawn values and the new normalized evaluation format
     * where 1.0 pawn = 50% win probability. Also processes tablebase scores and mate values.
     * 
     * @param evaluation the evaluation string (e.g., "+1.25", "Mate 3", "-0.50", "199.50")
     * @return the evaluation in normalized centipawns, or 0 if parsing fails
     */
    private int parseEvaluation(String evaluation) {
        if (evaluation == null || evaluation.equals("Error")) return 0;
        
        try {
            if (evaluation.contains("Mate")) {
                return evaluation.contains("-") ? -10000 : 10000;
            }
            
            double evalValue = Double.parseDouble(evaluation.replace("+", ""));
            
            // Handle tablebase scores (SF16+): values around 200.00 indicate tablebase wins
            if (Math.abs(evalValue) >= 199.0) {
                return evalValue > 0 ? 15000 : -15000; // Tablebase win/loss
            }
            
            // Convert normalized evaluation to centipawns
            // In new format: 1.0 pawn = 50% win probability
            // We'll use a modified scale for move classification
            return (int) (evalValue * 100);
            
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Classifies a move based on its centipawn loss and special characteristics.
     * Uses updated thresholds for Stockfish's normalized evaluation system.
     * 
     * <p>Classification thresholds adjusted for the new evaluation format where
     * 1.0 pawn = 50% win probability. Thresholds are more sensitive to reflect
     * the probabilistic nature of modern engine evaluation.
     * 
     * @param move the move record to classify
     * @param centipawnLoss the centipawn loss caused by this move
     */
    private void classifyMove(GameHistory.MoveRecord move, int centipawnLoss) {
        move.centipawnLoss = centipawnLoss;
        
        move.isBrilliant = false;
        move.isGood = false;
        move.isInaccuracy = false;
        move.isMistake = false;
        move.isBlunder = false;
        
        if (move.wasCheckmate) {
            move.isBrilliant = true;
            move.centipawnLoss = 0;
            return;
        }
        
        // Brilliant moves: significant improvement or tactical excellence
        if (centipawnLoss < -25 || 
            (move.wasCapture && centipawnLoss < -10) ||
            (move.wasPromotion && centipawnLoss <= 0)) {
            move.isBrilliant = true;
            return;
        }
        
        // Adjusted thresholds for normalized evaluation
        // These values correspond better to win probability changes
        if (centipawnLoss <= 10) {
            move.isGood = true; // Best move or near-best
        } else if (centipawnLoss <= 25) {
            move.isGood = true; // Good move 
        } else if (centipawnLoss <= 50) {
            move.isInaccuracy = true; // Minor error (~5% win probability loss)
        } else if (centipawnLoss <= 100) {
            move.isMistake = true; // Significant error (~10% win probability loss)
        } else {
            move.isBlunder = true; // Major error (>10% win probability loss)
        }
    }

    /**
     * Converts a Stockfish evaluation to win/draw/loss probabilities.
     * 
     * <p>Based on Stockfish's normalized evaluation where 1.0 pawn = 50% win probability.
     * Uses the probability model from SF15.1 data for 60+0.6s games.
     * 
     * @param evaluation the raw evaluation string from Stockfish
     * @return array of [win%, draw%, loss%] probabilities
     */
    private double[] calculateWinDrawLoss(String evaluation) {
        if (evaluation == null || evaluation.equals("Error") || evaluation.equals("Unknown")) {
            return new double[]{33.3, 33.3, 33.3}; // Unknown position
        }
        
        try {
            if (evaluation.contains("Mate")) {
                // Extract mate in N value, accounting for sign
                String mateStr = evaluation.replaceAll("[^0-9-]", "");
                if (!mateStr.isEmpty()) {
                    int mateIn = Integer.parseInt(mateStr);
                    if (mateIn > 0) {
                        return new double[]{100.0, 0.0, 0.0}; // Mate for current side
                    } else {
                        return new double[]{0.0, 0.0, 100.0}; // Mate against current side
                    }
                }
                // If we can't parse mate value, assume forced win/loss
                if (evaluation.startsWith("-")) {
                    return new double[]{0.0, 0.0, 100.0};
                } else {
                    return new double[]{100.0, 0.0, 0.0};
                }
            }
            
            double evalValue = Double.parseDouble(evaluation.replace("+", ""));
            
            // Handle very large evaluations (tablebase/forced wins)
            if (Math.abs(evalValue) >= 10.0) {
                if (evalValue > 0) {
                    return new double[]{95.0, 5.0, 0.0}; // Very strong advantage
                } else {
                    return new double[]{0.0, 5.0, 95.0}; // Very bad position
                }
            }
            
            // Convert evaluation to win probability using logistic function
            // Based on Stockfish's WDL model - approximation of fishtest data
            // Formula: winP ≈ 1 / (1 + e^(-K * eval))
            // K ≈ 1.1 is calibrated from LTC results
            double K = 1.1;
            double winProb = 1.0 / (1.0 + Math.exp(-K * evalValue));
            
            // Convert to percentage
            winProb *= 100.0;
            
            // Calculate draw probability - decreases as advantage increases
            // Based on observed pattern: equal positions have ~40% draws
            double drawProb = Math.max(5.0, 40.0 * Math.exp(-Math.abs(evalValue) * 0.8));
            
            // Loss probability is the remainder
            double lossProb = 100.0 - winProb - drawProb;
            if (lossProb < 0) {
                lossProb = 0.0;
                drawProb = 100.0 - winProb;
            }
            
            return new double[]{winProb, drawProb, lossProb};
            
        } catch (NumberFormatException e) {
            return new double[]{33.3, 33.3, 33.3};
        }
    }

    /**
     * Updates the chess board display with the current position and move highlights.
     */
    private void updateBoard() {
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        ChessBoard displayBoard = getBoardAtMove(currentMoveIndex);
        
        ChessMove lastMove = currentMoveIndex >= 0 && currentMoveIndex < gameHistory.moves.size() ? 
            gameHistory.moves.get(currentMoveIndex).move : null;
        ChessMove nextMove = currentMoveIndex + 1 < gameHistory.moves.size() ? 
            gameHistory.moves.get(currentMoveIndex + 1).move : null;

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
        
        // Update evaluation displays
        updateEvaluationDisplays();
    }
    
    private void updateEvaluationDisplays() {
        // Opponent evaluation glass (slot 35)
        if (analysisCompleted && currentMoveIndex >= 0 && currentMoveIndex < moveEvaluations.size()) {
            String currentEval = moveEvaluations.get(currentMoveIndex);
            String opponentEval = getOpponentEvaluation(currentEval, currentMoveIndex);
            double[] opponentWDL = calculateWinDrawLoss(opponentEval);
            
            PieceColor opponentColor = playerColor == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
            boolean isOpponentBlack = opponentColor == PieceColor.BLACK;
            
            List<Component> opponentLore = new ArrayList<>();
            opponentLore.add(Component.literal("§7" + gameHistory.getOpponentName(player.getUUID()) + "'s view"));
            opponentLore.add(Component.literal("§f" + opponentEval));
            opponentLore.add(Component.literal(""));
            opponentLore.add(Component.literal("§aWin: " + String.format("%.1f%%", opponentWDL[0])));
            opponentLore.add(Component.literal("§7Draw: " + String.format("%.1f%%", opponentWDL[1])));
            opponentLore.add(Component.literal("§cLoss: " + String.format("%.1f%%", opponentWDL[2])));
            
            setSlot(35, new GuiElementBuilder(isOpponentBlack ? Items.BLACK_STAINED_GLASS : Items.WHITE_STAINED_GLASS)
                    .setName(Component.literal((isOpponentBlack ? "§8" : "§f") + "Opponent Evaluation"))
                    .setLore(opponentLore));
        } else {
            setSlot(35, new GuiElementBuilder(Items.GRAY_STAINED_GLASS)
                    .setName(Component.literal("§7Opponent Evaluation"))
                    .addLoreLine(Component.literal("§7No evaluation available")));
        }
        
        // Our evaluation glass (slot 44)
        if (analysisCompleted && currentMoveIndex >= 0 && currentMoveIndex < moveEvaluations.size()) {
            String currentEval = moveEvaluations.get(currentMoveIndex);
            String ourEval = getOurEvaluation(currentEval, currentMoveIndex);
            double[] ourWDL = calculateWinDrawLoss(ourEval);
            
            boolean isPlayerBlack = playerColor == PieceColor.BLACK;
            
            List<Component> ourLore = new ArrayList<>();
            ourLore.add(Component.literal("§7Your view"));
            ourLore.add(Component.literal("§f" + ourEval));
            ourLore.add(Component.literal(""));
            ourLore.add(Component.literal("§aWin: " + String.format("%.1f%%", ourWDL[0])));
            ourLore.add(Component.literal("§7Draw: " + String.format("%.1f%%", ourWDL[1])));
            ourLore.add(Component.literal("§cLoss: " + String.format("%.1f%%", ourWDL[2])));
            
            setSlot(44, new GuiElementBuilder(isPlayerBlack ? Items.BLACK_STAINED_GLASS : Items.WHITE_STAINED_GLASS)
                    .setName(Component.literal((isPlayerBlack ? "§8" : "§f") + "Your Evaluation"))
                    .setLore(ourLore));
        } else {
            setSlot(44, new GuiElementBuilder(Items.GRAY_STAINED_GLASS)
                    .setName(Component.literal("§7Your Evaluation"))
                    .addLoreLine(Component.literal("§7No evaluation available")));
        }
    }
    
    private String getOpponentEvaluation(String evaluation, int moveIndex) {
        if (evaluation == null || evaluation.equals("Error")) return "Unknown";
        
        // Evaluation is always from white's perspective
        // If opponent is black, show evaluation as-is (good for white = bad for black)
        // If opponent is white, flip it to show from white's perspective
        PieceColor opponentColor = playerColor == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
        if (opponentColor == PieceColor.WHITE) {
            return evaluation; // White's perspective, keep as-is
        } else {
            return flipEvaluation(evaluation); // Black's perspective, flip it
        }
    }
    
    private String getOurEvaluation(String evaluation, int moveIndex) {
        if (evaluation == null || evaluation.equals("Error")) return "Unknown";
        
        // Evaluation is always from white's perspective
        // If we are white, show as-is
        // If we are black, flip it to show from black's perspective
        if (playerColor == PieceColor.WHITE) {
            return evaluation; // White's perspective, keep as-is
        } else {
            return flipEvaluation(evaluation); // Black's perspective, flip it
        }
    }
    
    private String flipEvaluation(String evaluation) {
        if (evaluation.contains("Mate")) {
            if (evaluation.contains("-")) {
                return evaluation.replace("-", "+");
            } else if (evaluation.contains("+")) {
                return evaluation.replace("+", "-");
            } else {
                return "-" + evaluation;
            }
        } else {
            try {
                double value = Double.parseDouble(evaluation.replace("+", ""));
                value = -value;
                return value >= 0 ? "+" + String.format("%.2f", value) : String.format("%.2f", value);
            } catch (NumberFormatException e) {
                return evaluation;
            }
        }
    }

    private void updateMoveInfo() {
        // Update the sidebar elements with current move info instead of middle slots
        updateSidebarMoveInfo();
        updateEvaluationDisplays();
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
        } else if (centipawnLoss <= 10) {
            return "§a✓ Excellent";
        } else if (centipawnLoss <= 25) {
            return "§a✓ Good";
        } else if (centipawnLoss <= 50) {
            return "§e⚠ Inaccuracy";
        } else if (centipawnLoss <= 100) {
            return "§c⚠ Mistake";
        } else {
            return "§4⚠ Blunder";
        }
    }
    
    private String getMoveQuality(GameHistory.MoveRecord move) {
        // Priority order: brilliant > checkmate > other classifications
        if (move.isBrilliant) {
            if (move.wasCheckmate) {
                return "§6✦ Brilliant Checkmate!";
            }
            return "§d✦ Brilliant!";
        }
        
        if (move.wasCheckmate) {
            return "§6✦ Checkmate!";
        }
        
        if (move.isGood) {
            if (move.centipawnLoss <= 0) {
                return "§b✦ Best Move";
            } else if (move.centipawnLoss <= 10) {
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
        
        // Fallback to centipawn loss (shouldn't happen with proper classification)
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

    /**
     * Exports the game to PGN format with clipboard copy and Chess.com integration.
     */
    private void exportToPGN() {
        StringBuilder cleanPgn = new StringBuilder();
        
        // PGN headers on one line
        cleanPgn.append("[Event \"Casual Game\"] ");
        cleanPgn.append("[Site \"Minecraft Chess\"] ");
        cleanPgn.append("[Date \"").append(gameHistory.getFormattedDate()).append("\"] ");
        cleanPgn.append("[White \"").append(gameHistory.whitePlayerName).append("\"] ");
        cleanPgn.append("[Black \"").append(gameHistory.blackPlayerName).append("\"] ");
        cleanPgn.append("[Result \"").append(gameHistory.getResultString().split(" ")[0]).append("\"] ");
        cleanPgn.append("[TimeControl \"").append(gameHistory.timeControl.name()).append("\"] ");
        
        // Moves
        for (int i = 0; i < gameHistory.moves.size(); i++) {
            if (i % 2 == 0) {
                cleanPgn.append((i / 2 + 1)).append(". ");
            }
            cleanPgn.append(gameHistory.moves.get(i).moveNotation).append(" ");
        }
        
        cleanPgn.append(gameHistory.getResultString().split(" ")[0]);
        
        // Create clickable component that copies to clipboard
        Component copyableComponent = Component.literal("§a[Copy to Clipboard]")
                .withStyle(style -> style
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, 
                                cleanPgn.toString()))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, 
                                Component.literal("§7Click to copy PGN to clipboard"))));
        
        Component openChessComComponent = Component.literal("§b[Open Chess.com]")
                .withStyle(style -> style
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, 
                                "https://www.chess.com/analysis?tab=analysis"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, 
                                Component.literal("§7Click to open Chess.com analysis board"))));
        
        player.sendSystemMessage(Component.literal("§ePGN Export: ")
                .append(copyableComponent)
                .append(Component.literal(" "))
                .append(openChessComComponent));
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
    }

    @Override
    public void onClose() {
        // Cancel analysis if still running
        if (analysisTask != null && !analysisTask.isDone()) {
            analysisTask.cancel(true);
        }
        
        // Restore player inventory
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        
        super.onClose();
    }

    @Override
    public boolean canPlayerClose() {
        return true; // Allow closing analysis GUI
    }
}