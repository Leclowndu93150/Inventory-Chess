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
import com.leclowndu93150.guichess.util.OverlayModelDataRegistry;
import com.leclowndu93150.guichess.util.PieceOverlayHelper;
import com.leclowndu93150.guichess.util.TimeHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main chess game interface GUI, responsible for rendering the interactive chess board
 * and handling all game-related user interactions during active chess games.
 * 
 * <p>This GUI serves as the primary interface between players and the chess game engine,
 * managing visual representation of the board state, piece interactions, move validation,
 * and game controls. It provides a complete chess playing experience within Minecraft's
 * inventory GUI system.
 * 
 * <h3>Key Responsibilities:</h3>
 * <ul>
 *   <li><strong>Board Rendering:</strong> Visual representation of the 8x8 chess board with proper perspective</li>
 *   <li><strong>Piece Interaction:</strong> Handles piece selection, movement, and capture mechanics</li>
 *   <li><strong>Game State Management:</strong> Displays current turn, timers, and game status</li>
 *   <li><strong>Move Validation:</strong> Provides visual feedback for valid moves and captures</li>
 *   <li><strong>Promotion Handling:</strong> Special interface for pawn promotion piece selection</li>
 *   <li><strong>Game Controls:</strong> Resignation, draw offers, and analysis tools integration</li>
 *   <li><strong>Auto-Reopening:</strong> Prevents accidental GUI closure during active games</li>
 * </ul>
 * 
 * <h3>GUI Layout:</h3>
 * The GUI uses a 9x6 inventory layout:
 * <ul>
 *   <li>Rows 1-6: Chess board (8x8 grid with gap columns for visual clarity)</li>
 *   <li>Side panels: Timer displays, turn indicators, and utility buttons</li>
 *   <li>Control area: Resign, draw offer, and other game action buttons</li>
 * </ul>
 * 
 * <h3>Interaction Patterns:</h3>
 * <ul>
 *   <li><strong>Piece Selection:</strong> Click on a piece to select it and show valid moves</li>
 *   <li><strong>Move Execution:</strong> Click on a highlighted valid move square to execute the move</li>
 *   <li><strong>Turn-based:</strong> Only allows interactions when it's the player's turn</li>
 *   <li><strong>Sound Feedback:</strong> Provides audio cues for all interactions and game events</li>
 * </ul>
 * 
 * <h3>State Management:</h3>
 * The GUI maintains minimal state, delegating game logic to the ChessGame instance:
 * <ul>
 *   <li>Board perspective (white/black orientation)</li>
 *   <li>Promotion dialog state and parameters</li>
 *   <li>Auto-reopen preferences for game continuity</li>
 * </ul>
 * 
 * @see ChessGame The underlying game logic and state management
 * @see PieceOverlayHelper For visual piece state representation
 * @see ChessSoundManager For audio feedback system
 */
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
    private boolean autoReopen = true; // Can be disabled for death/respawn scenarios
    

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
                // This is a bot game
                if (game instanceof com.leclowndu93150.guichess.game.ChessBotGame botGame) {
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
     * 
     * <p>This is the core rendering method that translates the logical chess board state
     * into visual GUI elements. It handles all aspects of board visualization including
     * piece placement, move highlighting, game state indicators, and special overlays.
     * 
     * <h3>Rendering Process:</h3>
     * <ol>
     *   <li>Checks for special states (promotion dialog)</li>
     *   <li>Retrieves current game state (board, valid moves, selection)</li>
     *   <li>Clears all GUI slots for fresh rendering</li>
     *   <li>Calculates visual highlights (capturable pieces, last move)</li>
     *   <li>Renders each board square with appropriate piece and overlay</li>
     *   <li>Updates utility controls (timers, buttons)</li>
     * </ol>
     * 
     * <h3>Visual Elements:</h3>
     * <ul>
     *   <li><strong>Piece Overlays:</strong> Shows piece state (selected, capturable, in check)</li>
     *   <li><strong>Move Highlights:</strong> Valid moves displayed with special square colors</li>
     *   <li><strong>Last Move:</strong> Previous move squares highlighted for context</li>
     *   <li><strong>Board Perspective:</strong> Automatically oriented based on player color</li>
     * </ul>
     * 
     * <p>The method ensures the GUI always reflects the authoritative game state,
     * providing real-time visual feedback for player interactions and game events.
     * 
     * @see #handleSquareClick(ChessPosition) For interaction handling
     * @see PieceOverlayHelper#getModelDataForPieceState For visual state calculation
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
     * Handles all chess board square click interactions, managing piece selection and move execution.
     * 
     * <p>This method is the core interaction handler for the chess board, implementing the standard
     * chess UI pattern of "click to select, click to move". It validates player actions,
     * coordinates with the game engine, and provides appropriate feedback.
     * 
     * <h3>Interaction Flow:</h3>
     * <ol>
     *   <li><strong>Move Execution:</strong> If a piece is selected and the clicked square is a valid move,
     *       executes the move and provides capture feedback</li>
     *   <li><strong>Piece Selection:</strong> If clicking on a new piece, selects it and highlights valid moves</li>
     *   <li><strong>Deselection:</strong> If clicking an invalid square, deselects the current piece</li>
     * </ol>
     * 
     * <h3>Player Interaction Rules:</h3>
     * <ul>
     *   <li>Players can only interact during their turn</li>
     *   <li>Only valid moves (as determined by the game engine) are accepted</li>
     *   <li>Invalid actions provide error feedback and maintain current state</li>
     *   <li>All successful actions trigger appropriate sound effects</li>
     * </ul>
     * 
     * <h3>State Management:</h3>
     * The method delegates all game state changes to the ChessGame instance, ensuring
     * consistency between the GUI and game logic. The GUI then reflects these changes
     * through automatic board updates.
     * 
     * @param position The chess board position that was clicked by the player
     * 
     * @see ChessGame#selectSquare(ServerPlayer, ChessPosition) For the underlying game logic
     * @see #updateBoard() For visual state updates following interactions
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
    }

    protected void updateTimerDisplays() {
        if (game == null) return;

        // Get clock items with proper model data
        ItemStack whiteClockItem = TimeHelper.getClockItem(game.getWhiteTimeLeft());
        ItemStack blackClockItem = TimeHelper.getClockItem(game.getBlackTimeLeft());
        
        GuiElementBuilder whiteTimer = GuiElementBuilder.from(whiteClockItem)
                .setName(Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())));

        GuiElementBuilder blackTimer = GuiElementBuilder.from(blackClockItem)
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
        StockfishIntegration.getInstance().requestHint(getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
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