package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessBoard;
import com.leclowndu93150.guichess.util.ChessSoundManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PracticeBoardGUI extends ChessGUI {
    private ChessBoard practiceBoard;
    private ChessPosition selectedSquare = null;
    private Set<ChessPosition> validMoves = new HashSet<>();
    private boolean showingPromotionDialog = false;
    private ChessPosition promotionFrom;
    private ChessPosition promotionTo;

    public PracticeBoardGUI(ServerPlayer player) {
        super(player, null, PieceColor.WHITE);
        this.practiceBoard = new ChessBoard();
        setTitle(Component.literal("§dPractice Board"));
    }

    public PracticeBoardGUI(ServerPlayer player, String fen) {
        super(player, null, PieceColor.WHITE);
        this.practiceBoard = new ChessBoard();
        setTitle(Component.literal("§dPosition Analysis"));
    }

    @Override
    public void afterOpen() {
        super.afterOpen();
        updateBoard();
    }

    @Override
    protected ChessBoard getBoard() {
        return practiceBoard != null ? practiceBoard : new ChessBoard();
    }

    @Override
    protected Set<ChessPosition> getValidMoves() {
        return validMoves;
    }

    @Override
    protected ChessPosition getSelectedSquare() {
        return selectedSquare;
    }

    @Override
    protected List<ChessMove> getMoveHistory() {
        return practiceBoard != null ? practiceBoard.getMoveHistory() : new ArrayList<>();
    }

    @Override
    public boolean canPlayerClose() {
        return true;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    protected void handleSquareClick(ChessPosition position) {
        if (practiceBoard == null) return;

        ChessPiece piece = practiceBoard.getPiece(position);
        if (selectedSquare != null && validMoves.contains(position)) {
            ChessPiece selectedPiece = practiceBoard.getPiece(selectedSquare);

            if (selectedPiece != null && selectedPiece.getType() == PieceType.PAWN &&
                    ((selectedPiece.isWhite() && position.rank == 7) ||
                            (selectedPiece.isBlack() && position.rank == 0))) {
                showPracticePromotionDialog(selectedSquare, position);
                return;
            }

            if (makeMove(selectedSquare, position, null)) {
                if (piece != null) {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    player.sendSystemMessage(Component.literal("§aCaptured " + piece.getDisplayName().getString() + "!"));
                } else {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                }
                selectedSquare = null;
                validMoves.clear();
                updateBoard();
            }
            return;
        }

        if (piece != null) {
            selectedSquare = position;
            validMoves = getPracticeValidMovesFrom(position);
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SELECT);
            updateBoard();
            return;
        }

        selectedSquare = null;
        validMoves.clear();
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
        updateBoard();
    }

    private Set<ChessPosition> getPracticeValidMovesFrom(ChessPosition from) {
        Set<ChessPosition> moves = new HashSet<>();
        if (practiceBoard == null) return moves;

        ChessPiece piece = practiceBoard.getPiece(from);
        if (piece == null) return moves;

        PieceColor originalTurn = practiceBoard.getCurrentTurn();
        PieceColor pieceColor = piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
        practiceBoard.setCurrentTurn(pieceColor);

        List<ChessMove> legalMoves = practiceBoard.getLegalMoves();
        for (ChessMove move : legalMoves) {
            if (move.from.equals(from)) {
                moves.add(move.to);
            }
        }

        practiceBoard.setCurrentTurn(originalTurn);
        return moves;
    }

    private boolean makeMove(ChessPosition from, ChessPosition to, PieceType promotion) {
        if (practiceBoard == null) return false;

        ChessPiece piece = practiceBoard.getPiece(from);
        if (piece == null) return false;

        // Temporarily set turn to match piece color for move validation
        PieceColor originalTurn = practiceBoard.getCurrentTurn();
        PieceColor pieceColor = piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
        practiceBoard.setCurrentTurn(pieceColor);

        List<ChessMove> legalMoves = practiceBoard.getLegalMoves();
        ChessMove moveToMake = null;
        for (ChessMove move : legalMoves) {
            if (move.from.equals(from) && move.to.equals(to)) {
                if (promotion != null) {
                    if (move.promotionPiece == promotion) {
                        moveToMake = move;
                        break;
                    }
                } else {
                    if (move.promotionPiece == null) {
                        moveToMake = move;
                        break;
                    }
                }
            }
        }

        if (moveToMake != null) {
            boolean success = practiceBoard.makeMove(moveToMake);
            if (success) {
                return true;
            }
        }
        practiceBoard.setCurrentTurn(originalTurn);
        return false;
    }

    public void showPracticePromotionDialog(ChessPosition from, ChessPosition to) {
        this.showingPromotionDialog = true;
        this.promotionFrom = from;
        this.promotionTo = to;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.PROMOTION_DIALOG);
        setupPracticePromotionGUI();
    }

    private void setupPracticePromotionGUI() {
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        if (practiceBoard == null || promotionFrom == null) return;

        ChessPiece piece = practiceBoard.getPiece(promotionFrom);
        PieceColor pieceColor = piece != null && piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;

        setSlot(39, createPracticePromotionOption(PieceType.QUEEN, pieceColor));
        setSlot(40, createPracticePromotionOption(PieceType.ROOK, pieceColor));
        setSlot(41, createPracticePromotionOption(PieceType.BISHOP, pieceColor));
        setSlot(42, createPracticePromotionOption(PieceType.KNIGHT, pieceColor));

        setSlot(31, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§eChoose promotion piece")));
    }

    private GuiElementBuilder createPracticePromotionOption(PieceType pieceType, PieceColor color) {
        ChessPiece piece = ChessPiece.fromColorAndType(color, pieceType);

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(piece.getModelData())
                .setName(piece.getDisplayName())
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    makeMove(promotionFrom, promotionTo, pieceType);
                    showingPromotionDialog = false;
                    selectedSquare = null;
                    validMoves.clear();
                    updateBoard();
                });
    }

    @Override
    protected void setupUtilitySlots() {
        // Override utility slots for practice mode
        setupPracticeUtilities();
    }

    private void setupPracticeUtilities() {
        setSlot(8, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§cReset Board"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    practiceBoard = new ChessBoard();
                    selectedSquare = null;
                    validMoves.clear();
                    updateBoard();
                }));

        setSlot(17, new GuiElementBuilder(Items.COMPASS)
                .setName(Component.literal("§9Flip Board"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    player.sendSystemMessage(Component.literal("§9Board flipping not implemented yet"));
                }));

        setSlot(26, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(Component.literal("§dAnalyze Position"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ANALYSIS);
                    if (practiceBoard != null) {
                        StockfishIntegration.getInstance().analyzePosition(practiceBoard.toFEN(), analysis -> {
                            player.sendSystemMessage(Component.literal("§dAnalysis: " + analysis));
                        });
                    }
                }));

        setSlot(35, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7Show FEN"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    if (practiceBoard != null) {
                        player.sendSystemMessage(Component.literal("§7FEN: " + practiceBoard.toFEN()));
                    }
                }));

        setSlot(44, new GuiElementBuilder(Items.SPECTRAL_ARROW)
                .setName(Component.literal("§6Undo Move"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    player.sendSystemMessage(Component.literal("§6Undo not implemented yet"));
                }));

        updatePracticeTurnIndicator();

        setSlot(62, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cClose Practice Board"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    close();
                }));
    }

    private void updatePracticeTurnIndicator() {
        if (practiceBoard == null) return;

        PieceColor currentTurn = practiceBoard.getCurrentTurn();
        GuiElementBuilder turnIndicator;
        if (currentTurn == PieceColor.WHITE) {
            turnIndicator = new GuiElementBuilder(Items.LIME_STAINED_GLASS)
                    .setName(Component.literal("§fWhite to Move"));
        } else {
            turnIndicator = new GuiElementBuilder(Items.RED_STAINED_GLASS)
                    .setName(Component.literal("§8Black to Move"));
        }
        setSlot(53, turnIndicator);
    }

    @Override
    protected void updateUtilitySlots() {
        updatePracticeTurnIndicator();
    }

    @Override
    public void updateTimerDisplays() {
    }

    @Override
    protected void updateDrawButtons() {
    }

    @Override
    protected void setupAnalysisTools() {
    }

    @Override
    public void updateBoard() {
        if (showingPromotionDialog) {
            setupPracticePromotionGUI();
            return;
        }
        super.updateBoard();
    }
}