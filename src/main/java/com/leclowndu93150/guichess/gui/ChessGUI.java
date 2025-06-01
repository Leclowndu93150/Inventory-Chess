package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessBoard;
import com.leclowndu93150.guichess.game.ChessGame;
import com.leclowndu93150.guichess.game.GameManager;
import com.leclowndu93150.guichess.util.ChessSoundManager;
import com.leclowndu93150.guichess.util.PieceOverlayHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChessGUI extends SimpleGui {
    protected final ChessGame game;
    protected final PieceColor playerColor;
    protected final ServerPlayer player;

    private boolean showingPromotionDialog = false;
    private ChessPosition promotionFrom;
    private ChessPosition promotionTo;
    private boolean autoReopen = true; // Can be disabled for death/respawn scenarios

    public ChessGUI(ServerPlayer player, ChessGame game, PieceColor playerColor) {
        super(MenuType.GENERIC_9x6, player, true);
        this.player = player;
        this.game = game;
        this.playerColor = playerColor;

        setTitle(Component.literal("§0Chess Game against " + (game != null ? game.getOpponent(player).getName().getString() : "Unknown")));
        setupInitialGUI();
    }

    public boolean getAutoReopen() {
        return autoReopen;
    }

    public void setAutoReopen(boolean autoReopen) {
        this.autoReopen = autoReopen;
    }

    @Override
    public boolean canPlayerClose() {
        if (game != null && game.isGameActive()) {
            player.sendSystemMessage(Component.literal("§cYou cannot close the chess GUI during an active game! Use /chess resign to forfeit."));
            return false;
        }
        return true;
    }

    @Override
    public void onClose() {
        if (game != null && game.isGameActive() && autoReopen) {
            player.sendSystemMessage(Component.literal("§eReopening chess board..."));
            GameManager.getInstance().getServer().execute(() -> {
                try {
                    Thread.sleep(100);
                    if (this.isOpen()) return;
                    this.open();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        super.onClose();
    }

    private void setupInitialGUI() {
        updateBoard();
        setupUtilitySlots();
    }

    public void updateBoard() {
        if (!isOpen()) return;

        if (showingPromotionDialog) {
            setupPromotionGUI();
            return;
        }

        ChessBoard board = getBoard();
        Set<ChessPosition> validMoves = getValidMoves();
        ChessPosition selected = getSelectedSquare();
        List<ChessMove> moveHistory = getMoveHistory();

        checkForSoundEffects(moveHistory);

        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        // Determine pieces that can be captured this turn
        Set<ChessPosition> capturablePositions = getCapturablePositions(board, validMoves, selected);

        // Get last move positions for highlighting
        ChessPosition lastMoveFrom = null;
        ChessPosition lastMoveTo = null;
        if (!moveHistory.isEmpty()) {
            ChessMove lastMove = moveHistory.get(moveHistory.size() - 1);
            lastMoveFrom = lastMove.from;
            lastMoveTo = lastMove.to;
        }

        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;

            int chessRank, chessFile;
            if (getBoardPerspective() == PieceColor.WHITE) {
                // White perspective: rank 0 (White pieces) at bottom (row 7)
                chessRank = 7 - row;
                chessFile = col;
            } else {
                // Black perspective: rank 7 (White pieces) at top (row 0)
                chessRank = row;
                chessFile = 7 - col;
            }

            ChessPosition position = new ChessPosition(chessFile, chessRank);
            ChessPiece piece = board.getPiece(position);

            int slotIndex = i + i / 8;
            if (slotIndex >= 72) continue;

            GuiElementBuilder builder;

            if (piece != null) {
                builder = createPieceElementWithOverlay(piece, position, board, selected, validMoves,
                        capturablePositions, lastMoveFrom, lastMoveTo);
            } else {
                builder = createEmptySquareElement(position, validMoves);
            }

            final ChessPosition currentPos = position;
            builder.setCallback((index, type, action, gui) -> {
                handleSquareClick(currentPos);
            });

            setSlot(slotIndex, builder);
        }
        updateUtilitySlots();
    }

    private Set<ChessPosition> getCapturablePositions(ChessBoard board, Set<ChessPosition> validMoves, ChessPosition selected) {
        Set<ChessPosition> capturablePositions = new HashSet<>();

        if (selected != null && validMoves != null) {
            for (ChessPosition movePos : validMoves) {
                ChessPiece targetPiece = board.getPiece(movePos);
                if (targetPiece != null) {
                    capturablePositions.add(movePos);
                }
            }
        }

        return capturablePositions;
    }

    protected void handleSquareClick(ChessPosition position) {
        if (game == null) return; // Practice mode overrides this

        // Default implementation for real chess games
        ChessBoard board = getBoard();
        Set<ChessPosition> validMoves = getValidMoves();
        ChessPosition previousSelection = getSelectedSquare();
        ChessPiece pieceAtPos = board.getPiece(position);

        // Priority 1: If we have a selection and this is a valid move, make the move
        if (previousSelection != null && validMoves.contains(position)) {
            boolean actionResult = game.selectSquare(player, position);

            if (!actionResult) {
                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                return;
            }

            // Check if move was successful (selection should be cleared after a move)
            if (getSelectedSquare() == null) {
                List<ChessMove> moveHistory = getMoveHistory();
                if (!moveHistory.isEmpty()) {
                    ChessMove lastMove = moveHistory.get(moveHistory.size() - 1);
                    if (lastMove.isCapture && pieceAtPos != null) {
                        player.sendSystemMessage(Component.literal("§aCaptured " + pieceAtPos.displayName.getString() + "!"));
                    }
                    ChessSoundManager.playMoveSound(player, lastMove, getBoard().getGameState());
                }
            }
            return;
        }

        // Priority 2: Try to select piece or deselect
        boolean actionResult = game.selectSquare(player, position);

        if (!actionResult) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
            return;
        }

        // Determine what happened
        ChessPosition newSelection = getSelectedSquare();

        if (pieceAtPos != null && newSelection != null && newSelection.equals(position)) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SELECT);
        } else if (newSelection == null && previousSelection != null) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
        }
    }

    private void checkForSoundEffects(List<ChessMove> moveHistory) {
        if (moveHistory.isEmpty()) return;

        GameState gameState = getBoard().getGameState();

        if (gameState != GameState.WHITE_TURN && gameState != GameState.BLACK_TURN &&
                gameState != GameState.CHECK_WHITE && gameState != GameState.CHECK_BLACK) {
            ChessSoundManager.playGameEndSound(player, gameState);
        }
    }

    protected GuiElementBuilder createPieceElementWithOverlay(ChessPiece piece, ChessPosition position,
                                                              ChessBoard board, ChessPosition selected,
                                                              Set<ChessPosition> validMoves,
                                                              Set<ChessPosition> capturablePositions,
                                                              ChessPosition lastMoveFrom, ChessPosition lastMoveTo) {
        boolean isLightSquare = (position.file + position.rank) % 2 == 0;
        boolean isSelected = position.equals(selected);
        boolean canBeCaptured = capturablePositions.contains(position);
        boolean isLastMoved = position.equals(lastMoveFrom) || position.equals(lastMoveTo);
        boolean isInCheck = false;

        // Check if this king is in check
        if (piece.getType() == PieceType.KING) {
            PieceColor kingColor = piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
            isInCheck = board.isInCheck(kingColor);
        }

        int modelData = PieceOverlayHelper.getModelDataForPieceState(piece, isLightSquare,
                isSelected, canBeCaptured,
                isLastMoved, isInCheck);

        Component displayName = Component.empty()
                .append(piece.displayName)
                .append(Component.literal(" - " + position.toNotation()));

        // Add state indicators to the display name
        if (isInCheck) {
            displayName = Component.empty().append(displayName).append(Component.literal(" §c[CHECK]"));
        } else if (isSelected) {
            displayName = Component.empty().append(displayName).append(Component.literal(" §e[SELECTED]"));
        } else if (canBeCaptured) {
            displayName = Component.empty().append(displayName).append(Component.literal(" §c[CAN CAPTURE]"));
        } else if (isLastMoved) {
            displayName = Component.empty().append(displayName).append(Component.literal(" §6[LAST MOVE]"));
        }

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(modelData)
                .setName(displayName);
    }

    protected GuiElementBuilder createEmptySquareElement(ChessPosition position, Set<ChessPosition> validMoves) {
        boolean isLight = (position.file + position.rank) % 2 == 0;

        // Use valid move squares for empty squares that are valid moves
        if (validMoves != null && validMoves.contains(position)) {
            BoardSquare squareType = isLight ? BoardSquare.VALID_LIGHT_SQUARE : BoardSquare.VALID_DARK_SQUARE;
            return new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(squareType.modelData)
                    .setName(Component.literal("§a" + position.toNotation() + " - Valid Move"))
                    .hideDefaultTooltip();
        } else {
            // Normal empty square
            BoardSquare squareType = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;
            return new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(squareType.modelData)
                    .setName(Component.literal(position.toNotation()))
                    .hideDefaultTooltip();
        }
    }

    protected void setupUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();

        setSlot(62, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));
        setSlot(17, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));

        updateDrawButtons();
        setupAnalysisTools();
    }

    protected void updateUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();
        updateDrawButtons();
    }

    protected void updateTimerDisplays() {
        if (game == null) return; // Practice mode doesn't have a game

        GuiElementBuilder whiteTimer = new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())));

        GuiElementBuilder blackTimer = new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("§8Black: " + game.formatTime(game.getBlackTimeLeft())));

        if (playerColor == PieceColor.WHITE) {
            setSlot(53, whiteTimer);
            setSlot(26, blackTimer);
        } else {
            setSlot(53, blackTimer);
            setSlot(26, whiteTimer);
        }
    }

    protected void updateTurnIndicator() {
        if (game == null) return; // Handled by practice mode

        PieceColor currentTurn = getBoard().getCurrentTurn();
        GameState gameState = getBoard().getGameState();
        GuiElementBuilder turnIndicator;

        if (!game.isGameActive() || gameState == GameState.CHECKMATE_WHITE_WINS || gameState == GameState.CHECKMATE_BLACK_WINS ||
                gameState.name().contains("DRAW") || gameState.name().contains("RESIGN") ||
                gameState.name().contains("TIME_OUT") || gameState == GameState.STALEMATE) {
            turnIndicator = new GuiElementBuilder(Items.YELLOW_STAINED_GLASS)
                    .setName(Component.literal("§6Game Finished"));
        } else if (currentTurn == PieceColor.WHITE) {
            turnIndicator = new GuiElementBuilder(Items.WHITE_STAINED_GLASS)
                    .setName(Component.literal("§fWhite's Move"));
        } else {
            turnIndicator = new GuiElementBuilder(Items.BLACK_STAINED_GLASS)
                    .setName(Component.literal("§8Black's Move"));
        }
        setSlot(44, turnIndicator);
        setSlot(35, turnIndicator);
    }

    protected void setupAnalysisTools() {
        if (game == null) return; // Practice mode handles this separately

        setSlot(9, createUtilityButton(GameUtility.ANALYZE_POSITION, this::handleAnalyze));
        setSlot(18, createUtilityButton(GameUtility.STOCKFISH_HINT, this::handleHint));

        PlayerData playerDataObj = GameManager.getInstance().getPlayerData(player);
        setSlot(27, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .setName(Component.literal("§6ELO: " + playerDataObj.elo)));

        setSlot(36, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7" + game.getTimeControl().displayName)));
    }

    protected void updateDrawButtons() {
        if (game == null || !game.isGameActive()) {
            clearSlot(71);
            clearSlot(8);
            return;
        }

        if (game.isDrawOffered()) {
            if (game.getDrawOfferer() != null && game.getDrawOfferer().equals(player)) {
                setSlot(71, createUtilityButton(GameUtility.DRAW_DECLINE, this::handleCancelDrawOffer));
                clearSlot(8);
            } else {
                setSlot(71, createUtilityButton(GameUtility.DRAW_ACCEPT, this::handleAcceptDraw));
                setSlot(8, createUtilityButton(GameUtility.DRAW_DECLINE, this::handleDeclineDraw));
            }
        } else {
            setSlot(71, createUtilityButton(GameUtility.DRAW_OFFER, this::handleOfferDraw));
            clearSlot(8);
        }
    }

    protected GuiElementBuilder createUtilityButton(GameUtility utility, Runnable action) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(utility.modelData)
                .setName(utility.displayName)
                .setCallback((index, type, actionType, gui) -> {
                    if ((game != null && game.isGameActive()) || utility == GameUtility.EXIT_BUTTON) {
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                        action.run();
                    } else {
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                    }
                });
    }

    public void showPromotionDialog(ChessPosition from, ChessPosition to) {
        this.showingPromotionDialog = true;
        this.promotionFrom = from;
        this.promotionTo = to;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.PROMOTION_DIALOG);
        setupPromotionGUI();
    }

    private void setupPromotionGUI() {
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        setSlot(39, createPromotionOption(PieceType.QUEEN));
        setSlot(40, createPromotionOption(PieceType.ROOK));
        setSlot(41, createPromotionOption(PieceType.BISHOP));
        setSlot(42, createPromotionOption(PieceType.KNIGHT));

        setSlot(31, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§eChoose promotion piece")));
    }

    private GuiElementBuilder createPromotionOption(PieceType pieceType) {
        if (game == null) {
            // Fallback for practice mode - this shouldn't be called
            return new GuiElementBuilder(Items.BARRIER).setName(Component.literal("§cError"));
        }

        ChessPiece piece = ChessPiece.fromColorAndType(game.getPlayerColor(player), pieceType);
        GameUtility utility = switch (pieceType) {
            case QUEEN -> GameUtility.PROMOTE_QUEEN;
            case ROOK -> GameUtility.PROMOTE_ROOK;
            case BISHOP -> GameUtility.PROMOTE_BISHOP;
            case KNIGHT -> GameUtility.PROMOTE_KNIGHT;
            default -> GameUtility.PROMOTE_QUEEN;
        };

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(piece.modelData)
                .setName(utility.displayName)
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    game.makeMove(player, promotionFrom, promotionTo, pieceType);
                    showingPromotionDialog = false;
                    updateBoard();
                });
    }

    protected void handleResign() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.RESIGN);
        game.resign(player);
    }

    protected void handleOfferDraw() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.DRAW_OFFER);
        game.offerDraw(player);
    }

    protected void handleAcceptDraw() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
        game.respondToDraw(player, true);
    }

    protected void handleDeclineDraw() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        game.respondToDraw(player, false);
    }

    protected void handleCancelDrawOffer() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
        game.cancelDrawOffer(player);
    }

    protected void handleAnalyze() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ANALYSIS);
        if (game.isAnalysisMode()) {
            player.sendSystemMessage(Component.literal("§dAnalysis mode already enabled."));
        } else {
            game.enableAnalysisMode();
            player.sendSystemMessage(Component.literal("§dAnalysis mode enabled!"));
        }
    }

    protected void handleHint() {
        if (game != null && !game.isPlayerTurn(player) && !game.isAnalysisMode()) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
            player.sendSystemMessage(Component.literal("§cIt's not your turn to get a hint."));
            return;
        }
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.HINT);
        StockfishIntegration.getInstance().requestHint(getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
        });
    }

    // Methods that subclasses can override - updated to use per-player selection
    protected ChessBoard getBoard() {
        return game != null ? game.getBoard() : new ChessBoard();
    }

    protected Set<ChessPosition> getValidMoves() {
        if (game != null) {
            try {
                return game.getValidMoves(player);
            } catch (Exception e) {
                // Fallback for spectators or other edge cases
                return new HashSet<>();
            }
        }
        return new HashSet<>();
    }

    protected ChessPosition getSelectedSquare() {
        if (game != null) {
            try {
                return game.getSelectedSquare(player);
            } catch (Exception e) {
                // Fallback for spectators or other edge cases
                return null;
            }
        }
        return null;
    }

    protected List<ChessMove> getMoveHistory() {
        return getBoard().getMoveHistory();
    }

    protected PieceColor getBoardPerspective() {
        return playerColor;
    }
}