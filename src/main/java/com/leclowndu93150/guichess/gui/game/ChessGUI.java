package com.leclowndu93150.guichess.gui.game;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.rules.GameUtility;
import com.leclowndu93150.guichess.engine.integration.StockfishIntegration;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.game.core.ChessBoard;
import com.leclowndu93150.guichess.game.core.ChessBotGame;
import com.leclowndu93150.guichess.game.core.ChessGame;
import com.leclowndu93150.guichess.game.core.GameManager;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import com.leclowndu93150.guichess.util.visual.OverlayModelDataRegistry;
import com.leclowndu93150.guichess.util.visual.PieceOverlayHelper;
import com.leclowndu93150.guichess.util.time.TimeHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main chess game interface handling player interaction and board visualization.
 * Manages piece selection, move input, and real-time game state updates.
 */
public class ChessGUI extends SimpleGui {
    protected final ChessGame game;
    protected final PieceColor playerColor;
    protected final ServerPlayer player;

    private boolean showingPromotionDialog = false;
    private ChessPosition promotionFrom;
    private ChessPosition promotionTo;
    private boolean autoReopen = true;
    private List<String> receivedHints = new ArrayList<>();

    public ChessGUI(ServerPlayer player, ChessGame game, PieceColor playerColor) {
        super(MenuType.GENERIC_9x6, player, true);
        this.player = player;
        this.game = game;
        this.playerColor = playerColor;

        String opponentName = "Unknown";
        if (game != null) {
            ServerPlayer opponent = game.getOpponent(player);
            if (opponent != null) {
                opponentName = opponent.getName().getString();
            } else {
                if (game instanceof ChessBotGame botGame) {
                    opponentName = botGame.getBotName();
                } else {
                    opponentName = "Bot";
                }
            }
        }
        setTitle(Component.literal("§0Chess vs " + opponentName));
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

    /**
     * Updates the complete chess board display, reflecting the current game state.
     * Handles board rendering, piece placement, move highlighting, and utility controls.
     */
    public void updateBoard() {
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

        Set<ChessPosition> capturablePositions = getCapturablePositions(board, validMoves, selected);

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
                    chessRank = 7 - row;
                chessFile = col;
            } else {
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

    /**
     * Handles chess board square click interactions, managing piece selection and move execution.
     * Implements the standard "click to select, click to move" chess UI pattern.
     * 
     * @param position The chess board position that was clicked by the player
     */
    protected void handleSquareClick(ChessPosition position) {
        if (game == null) return;

        ChessBoard board = getBoard();
        Set<ChessPosition> validMoves = getValidMoves();
        ChessPosition previousSelection = getSelectedSquare();
        ChessPiece pieceAtPos = board.getPiece(position);

        if (previousSelection != null && validMoves.contains(position)) {
            boolean actionResult = game.selectSquare(player, position);

            if (!actionResult) {
                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                return;
            }

            if (getSelectedSquare() == null) {
                List<ChessMove> moveHistory = getMoveHistory();
                if (!moveHistory.isEmpty()) {
                    ChessMove lastMove = moveHistory.get(moveHistory.size() - 1);
                    if (lastMove.isCapture && pieceAtPos != null) {
                        player.sendSystemMessage(Component.literal("§aCaptured " + pieceAtPos.getDisplayName().getString() + "!"));
                    }
                }
            }
            return;
        }

        boolean actionResult = game.selectSquare(player, position);

        if (!actionResult) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
            return;
        }

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

        if (piece.getType() == PieceType.KING) {
            PieceColor kingColor = piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
            isInCheck = board.isInCheck(kingColor);
        }

        int modelData = PieceOverlayHelper.getModelDataForPieceState(piece, isLightSquare,
                isSelected, canBeCaptured,
                isLastMoved, isInCheck);

        Component displayName = Component.empty()
                .append(piece.getDisplayName())
                .append(Component.literal(" - " + position.toNotation()));

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

        if (validMoves != null && validMoves.contains(position)) {
            BoardSquare squareType = isLight ? BoardSquare.VALID_LIGHT_SQUARE : BoardSquare.VALID_DARK_SQUARE;
            return new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(squareType.getModelData())
                    .setName(Component.literal("§a" + position.toNotation() + " - Valid Move"))
                    .hideDefaultTooltip();
        } else {
            BoardSquare squareType = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;
            return new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(squareType.getModelData())
                    .setName(Component.literal(position.toNotation()))
                    .hideDefaultTooltip();
        }
    }

    protected void setupUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();
        updateResignButtons();
        updateDrawButtons();
    }
    
    protected void updateResignButtons() {
        if (game == null || !game.isGameActive()) {
            clearSlot(62);
            return;
        }

        if (game.isResignOffered() && game.getResignOfferer() != null && game.getResignOfferer().equals(player)) {
            setSlot(62, new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(GameUtility.RESIGN_BUTTON.getModelData())
                    .setName(Component.literal("§c§lCONFIRM RESIGN"))
                    .addLoreLine(Component.literal("§7Click again to confirm resignation"))
                    .setCallback((index, type, action, gui) -> handleResign()));
        } else {
            setSlot(62, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));
        }
    }

    protected void updateUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();
        updateDrawButtons();
        updateResignButtons();
        updateAnalyzeButton();
        updateHintButton();
    }
    
    protected void updateAnalyzeButton() {
        if (game == null) {
            clearSlot(80);
            return;
        }
        
        if (game.isGameActive()) {
            setSlot(80, new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(GameUtility.ANALYZE_POSITION.getModelData())
                    .setName(Component.literal("§7🔍 Analyze"))
                    .addLoreLine(Component.literal("§7Available when game ends"))
                    .setCallback((index, type, actionType, gui) -> {
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                        player.sendSystemMessage(Component.literal("§cAnalysis is only available after the game ends!"));
                    }));
        } else {
            setSlot(80, new GuiElementBuilder(Items.GRAY_DYE)
                    .setCustomModelData(GameUtility.ANALYZE_POSITION.getModelData())
                    .setName(Component.literal("§d🔍 Analyze"))
                    .addLoreLine(Component.literal("§7Click to review this game"))
                    .setCallback((index, type, actionType, gui) -> {
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                        handleAnalyze();
                    }));
        }
    }
    
    protected void updateHintButton() {
        if (game == null || !game.isGameActive() || game.getHintsAllowed() <= 0) {
            clearSlot(17);
            return;
        }
        
        PieceColor playerColor = game.getPlayerColor(player);
        int hintsUsed = playerColor == PieceColor.WHITE ? game.getWhiteHintsUsed() : game.getBlackHintsUsed();
        int hintsRemaining = game.getHintsAllowed() - hintsUsed;
        
        GuiElementBuilder hintButton = new GuiElementBuilder(Items.LIGHT)
                .setCount(Math.max(1, hintsRemaining)) // Show remaining hints as stack count
                .setName(Component.literal("§b💡 Hint (" + hintsRemaining + "/" + game.getHintsAllowed() + ")"))
                .addLoreLine(Component.literal("§7Click to get a move suggestion"))
                .addLoreLine(Component.literal("§7Hints remaining: §e" + hintsRemaining));
        
        if (hintsUsed > 0) {
            hintButton.addLoreLine(Component.literal("§7Previous hints:"));
            for (int i = 0; i < hintsUsed; i++) {
                hintButton.addLoreLine(Component.literal("§8  " + (i + 1) + ". " + getHintText(i)));
            }
        }
        
        if (hintsRemaining > 0 && (game.isPlayerTurn(player) || game.isAnalysisMode())) {
            hintButton.setCallback((index, type, actionType, gui) -> {
                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                handleHint();
            });
        } else {
            hintButton.addLoreLine(Component.literal(""))
                     .addLoreLine(Component.literal(hintsRemaining <= 0 ? "§cNo hints remaining!" : "§cNot your turn!"))
                     .setCallback((index, type, actionType, gui) -> {
                         ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                         if (hintsRemaining <= 0) {
                             player.sendSystemMessage(Component.literal("§cYou have no hints remaining!"));
                         } else {
                             player.sendSystemMessage(Component.literal("§cIt's not your turn to get a hint."));
                         }
                     });
        }
        
        setSlot(17, hintButton);
    }
    
    private String getHintText(int hintIndex) {
        if (hintIndex < receivedHints.size()) {
            return receivedHints.get(hintIndex);
        }
        return "Loading hint...";
    }
    
    private void handleAnalyze() {
        if (game == null || game.isGameActive()) {
            player.sendSystemMessage(Component.literal("§cAnalysis is only available after the game ends!"));
            return;
        }
        
        GameManager.getInstance().openMatchAnalysis(player, game.getGameId());
    }

    public void updateTimerDisplays() {
        if (game == null) return;

        int playerTimerSlot = 53;
        int opponentTimerSlot = 26;
        
        ItemStack whiteClockItem = TimeHelper.getClockItem(game.getWhiteTimeLeft());
        ItemStack blackClockItem = TimeHelper.getClockItem(game.getBlackTimeLeft());
        
        GuiElementBuilder whiteTimer = GuiElementBuilder.from(whiteClockItem)
                .setName(Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())))
                .hideDefaultTooltip();

        GuiElementBuilder blackTimer = GuiElementBuilder.from(blackClockItem)
                .setName(Component.literal("§8Black: " + game.formatTime(game.getBlackTimeLeft())))
                .hideDefaultTooltip();

        if (playerColor == PieceColor.WHITE) {
            setSlot(playerTimerSlot, whiteTimer);
            setSlot(opponentTimerSlot, blackTimer);
        } else {
            setSlot(playerTimerSlot, blackTimer);
            setSlot(opponentTimerSlot, whiteTimer);
        }
    }

    protected void updateTurnIndicator() {
        if (game == null) return;

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
            } else {
                setSlot(71, createUtilityButton(GameUtility.DRAW_ACCEPT, this::handleAcceptDraw));
            }
        } else {
            setSlot(71, createUtilityButton(GameUtility.DRAW_OFFER, this::handleOfferDraw));
        }
    }

    protected GuiElementBuilder createUtilityButton(GameUtility utility, Runnable action) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(utility.getModelData())
                .setName(utility.getDisplayName())
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
                .setCustomModelData(piece.getModelData())
                .setName(utility.getDisplayName())
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    game.makeMove(player, promotionFrom, promotionTo, pieceType);
                    showingPromotionDialog = false;
                    updateBoard();
                });
    }

    protected void handleResign() {
        if (game == null) return;
        
        if (game.isResignOffered() && game.getResignOfferer() != null && game.getResignOfferer().equals(player)) {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.RESIGN);
            game.confirmResign(player);
        } else {
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
            game.offerResign(player);
        }
    }
    
    protected void handleCancelResign() {
        if (game == null) return;
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
        game.cancelResign(player);
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

    protected void handleEnableAnalysisMode() {
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
        
        if (game != null && game.getHintsAllowed() > 0) {
            PieceColor playerColor = game.getPlayerColor(player);
            int hintsUsed = playerColor == PieceColor.WHITE ? game.getWhiteHintsUsed() : game.getBlackHintsUsed();
            
            if (hintsUsed >= game.getHintsAllowed()) {
                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                player.sendSystemMessage(Component.literal("§cYou have no hints remaining!"));
                return;
            }
            
            if (playerColor == PieceColor.WHITE) {
                game.incrementWhiteHints();
            } else {
                game.incrementBlackHints();
            }
        }
        
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.HINT);
        StockfishEngineManager.getInstance().requestHint(getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
            receivedHints.add(hint);
            updateHintButton();
        });
    }

    protected ChessBoard getBoard() {
        return game != null ? game.getBoard() : new ChessBoard();
    }

    protected Set<ChessPosition> getValidMoves() {
        if (game != null) {
            try {
                return game.getValidMoves(player);
            } catch (Exception e) {
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

    private void debugModelData(ChessPiece piece, ChessPosition position, int modelData) {
        if (player.getName().getString().equals("Dev")) {
            String overlayKey = piece.getModelName();
            boolean isLightSquare = (position.file + position.rank) % 2 == 0;

            if (position.equals(getSelectedSquare())) {
                overlayKey += isLightSquare ? "_selected_light" : "_selected_dark";
            } else {
                overlayKey += isLightSquare ? "_light" : "_dark";
            }

            Integer expectedModelData = OverlayModelDataRegistry.getModelData(overlayKey);

            if (expectedModelData != null && expectedModelData != modelData) {
                player.sendSystemMessage(Component.literal(
                        "§cModel data mismatch for " + overlayKey +
                                ": expected " + expectedModelData + " but got " + modelData
                ));
            }

            if (Math.random() < 0.01) {
                player.sendSystemMessage(Component.literal("§7Available overlays for " + piece.getModelName() + ":"));
                OverlayModelDataRegistry.getAllModelData().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(piece.getModelName()))
                        .forEach(e -> player.sendSystemMessage(Component.literal(
                                "§7  " + e.getKey() + " -> " + e.getValue()
                        )));
            }
        }
    }
}