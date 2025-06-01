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
import com.leclowndu93150.guichess.game.ChessBoard;
import com.leclowndu93150.guichess.game.ChessGame;
import com.leclowndu93150.guichess.game.GameManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Main GUI implementation
public class ChessGUI extends SimpleGui {
    private final ChessGame game;
    private final PieceColor playerColor;
    final ServerPlayer player;

    // GUI state
    private boolean showingPromotionDialog = false;
    private ChessPosition promotionFrom;
    private ChessPosition promotionTo;

    public ChessGUI(ServerPlayer player, ChessGame game, PieceColor playerColor) {
        super(MenuType.GENERIC_9x6, player, true); // 72 slots total
        this.player = player;
        this.game = game;
        this.playerColor = playerColor;

        setTitle(Component.literal("§0chess_board§rChess Game"));
        setupInitialGUI();
    }

    private void setupInitialGUI() {
        updateBoard();
        setupUtilitySlots();
    }

    public void updateBoard() {
        if (showingPromotionDialog) {
            setupPromotionGUI();
            return;
        }

        ChessBoard board = game.getBoard();
        Set<ChessPosition> validMoves = game.getValidMoves();
        ChessPosition selected = game.getSelectedSquare();

        // Update the 8x6 chess board (48 squares)
        for (int i = 0; i < 48; i++) {
            int row = i / 8;
            int col = i % 8;

            // Convert to chess coordinates (flip for black player)
            int chessRank = playerColor == PieceColor.WHITE ? row : 7 - row;
            int chessFile = playerColor == PieceColor.WHITE ? col : 7 - col;

            ChessPosition position = new ChessPosition(chessFile, chessRank);
            ChessPiece piece = board.getPiece(position);

            GuiElementBuilder builder;

            if (piece != null) {
                // Show the piece
                builder = createPieceElement(piece, position);
            } else {
                // Empty square
                builder = createEmptySquareElement(position);
            }

            // Add highlighting
            if (position.equals(selected)) {
                builder.setCustomModelData(BoardSquare.SELECTED_SQUARE.modelData);
            } else if (validMoves.contains(position)) {
                if (piece != null) {
                    builder.setCustomModelData(BoardSquare.CAPTURE_MOVE.modelData);
                } else {
                    builder.setCustomModelData(BoardSquare.VALID_MOVE.modelData);
                }
            }

            // Add click handler
            builder.setCallback((slot, type, action, gui) -> {
                game.selectSquare(player, position);
            });

            setSlot(i, builder);
        }

        // Update utility slots
        updateUtilitySlots();
    }

    private GuiElementBuilder createPieceElement(ChessPiece piece, ChessPosition position) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(piece.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, piece.displayName.copy().append(Component.literal(" - " + position.toNotation())));
    }

    private GuiElementBuilder createEmptySquareElement(ChessPosition position) {
        boolean isLight = (position.file + position.rank) % 2 == 0;
        BoardSquare squareType = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(squareType.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal(position.toNotation()))
                .hideDefaultTooltip();
    }

    void setupUtilitySlots() {
        // Timer displays (slots 48-49)
        updateTimerDisplays();

        // Turn indicator (slot 50)
        updateTurnIndicator();

        // Game controls (slots 51-53)
        setSlot(51, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));
        setSlot(52, createDrawButton());
        setSlot(53, createUtilityButton(GameUtility.EXIT_BUTTON, this::handleExit));

        // Extended board area for analysis info (slots 54-71)
        setupAnalysisArea();

        // Hotbar utilities (slots 72-89)
        setupHotbarUtilities();
    }

    private void updateUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();
        setSlot(52, createDrawButton()); // Update draw button state
    }

    private void updateTimerDisplays() {
        // White timer
        GuiElementBuilder whiteTimer = new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(GameUtility.TIMER_WHITE.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())));
        setSlot(48, whiteTimer);

        // Black timer
        GuiElementBuilder blackTimer = new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(GameUtility.TIMER_BLACK.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§8Black: " + game.formatTime(game.getBlackTimeLeft())));
        setSlot(49, blackTimer);
    }

    private void updateTurnIndicator() {
        PieceColor currentTurn = game.getBoard().getCurrentTurn();
        GameUtility indicator = currentTurn == PieceColor.WHITE ?
                GameUtility.TURN_INDICATOR_WHITE : GameUtility.TURN_INDICATOR_BLACK;

        GuiElementBuilder turnIndicator = new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(indicator.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, indicator.displayName);
        setSlot(50, turnIndicator);
    }

    private GuiElementBuilder createDrawButton() {
        if (game.isDrawOffered()) {
            if (game.getDrawOfferer() == player) {
                return createUtilityButton(GameUtility.DRAW_DECLINE, this::handleCancelDraw);
            } else {
                return createUtilityButton(GameUtility.DRAW_ACCEPT, this::handleAcceptDraw);
            }
        } else {
            return createUtilityButton(GameUtility.DRAW_OFFER, this::handleOfferDraw);
        }
    }

    private void setupAnalysisArea() {
        if (game.isAnalysisMode()) {
            Map<String, Object> analysis = game.getAnalysisData();
            // Display analysis information
            for (int i = 54; i < 72; i++) {
                setSlot(i, new GuiElementBuilder(Items.GRAY_DYE)
                        .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(GameUtility.ANALYZE_POSITION.modelData))
                        .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§dAnalysis Data")));
            }
        } else {
            // Show move history or other info
            List<ChessMove> moves = game.getBoard().getMoveHistory();
            for (int i = 54; i < 72 && i - 54 < moves.size(); i++) {
                ChessMove move = moves.get(i - 54);
                setSlot(i, new GuiElementBuilder(Items.PAPER)
                        .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§7" + (i - 53) + ". " + move.toNotation())));
            }
        }
    }

    private void setupHotbarUtilities() {
        // Game analysis tools
        setSlot(72, createUtilityButton(GameUtility.ANALYZE_POSITION, this::handleAnalyze));
        setSlot(73, createUtilityButton(GameUtility.STOCKFISH_HINT, this::handleHint));
        setSlot(74, createUtilityButton(GameUtility.UNDO_MOVE, this::handleUndo));
        setSlot(75, createUtilityButton(GameUtility.REDO_MOVE, this::handleRedo));

        // Social features
        setSlot(76, createUtilityButton(GameUtility.SPECTATE_BUTTON, this::handleSpectate));
        setSlot(77, createUtilityButton(GameUtility.VIEW_LEADERBOARD, this::handleLeaderboard));
        setSlot(78, createUtilityButton(GameUtility.CHALLENGE_PLAYER, this::handleChallenge));
        setSlot(79, createUtilityButton(GameUtility.SETTINGS_BUTTON, this::handleSettings));

        // Game state info
        GameState state = game.getBoard().getGameState();
        setSlot(80, new GuiElementBuilder(Items.BOOK)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§7Game State: " + state.name())));

        setSlot(81, new GuiElementBuilder(Items.CLOCK)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§7Time Control: " + game.getTimeControl().displayName)));

        PlayerData playerData = GameManager.getInstance().getPlayerData(player);
        setSlot(82, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§6ELO: " + playerData.elo)));

        // Fill remaining slots
        for (int i = 83; i < 90; i++) {
            setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setComponent(DataComponents.CUSTOM_NAME, Component.literal("")));
        }
    }

    private GuiElementBuilder createUtilityButton(GameUtility utility, Runnable action) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(utility.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, utility.displayName)
                .setCallback((slot, type, actionType, gui) -> action.run());
    }

    public void showPromotionDialog(ChessPosition from, ChessPosition to) {
        this.showingPromotionDialog = true;
        this.promotionFrom = from;
        this.promotionTo = to;
        setupPromotionGUI();
    }

    private void setupPromotionGUI() {
        // Clear the board area and show promotion options
        for (int i = 0; i < 48; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                    .setComponent(DataComponents.CUSTOM_NAME, Component.literal("")));
        }

        // Show promotion options in center
        setSlot(20, createPromotionOption(PieceType.QUEEN));
        setSlot(21, createPromotionOption(PieceType.ROOK));
        setSlot(22, createPromotionOption(PieceType.BISHOP));
        setSlot(23, createPromotionOption(PieceType.KNIGHT));

        // Instructions
        setSlot(28, new GuiElementBuilder(Items.PAPER)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§eChoose promotion piece")));
    }

    private GuiElementBuilder createPromotionOption(PieceType pieceType) {
        ChessPiece piece = ChessPiece.fromColorAndType(playerColor, pieceType);
        GameUtility utility = switch (pieceType) {
            case QUEEN -> GameUtility.PROMOTE_QUEEN;
            case ROOK -> GameUtility.PROMOTE_ROOK;
            case BISHOP -> GameUtility.PROMOTE_BISHOP;
            case KNIGHT -> GameUtility.PROMOTE_KNIGHT;
            default -> GameUtility.PROMOTE_QUEEN;
        };

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(utility.modelData))
                .setComponent(DataComponents.CUSTOM_NAME, utility.displayName)
                .setCallback((slot, type, action, gui) -> {
                    game.makeMove(player, promotionFrom, promotionTo, pieceType);
                    showingPromotionDialog = false;
                    updateBoard();
                });
    }

    // Event handlers
    private void handleResign() {
        game.resign(player);
    }

    private void handleOfferDraw() {
        game.offerDraw(player);
    }

    private void handleAcceptDraw() {
        game.respondToDraw(player, true);
    }

    private void handleCancelDraw() {
        game.respondToDraw(player, false);
    }

    private void handleExit() {
        close();
    }

    private void handleAnalyze() {
        game.enableAnalysisMode();
    }

    private void handleHint() {
        // Request hint from analysis
        player.sendSystemMessage(Component.literal("§bAnalyzing position..."));
    }

    private void handleUndo() {
        player.sendSystemMessage(Component.literal("§7Undo not available in live games"));
    }

    private void handleRedo() {
        player.sendSystemMessage(Component.literal("§7Redo not available in live games"));
    }

    private void handleSpectate() {
        player.sendSystemMessage(Component.literal("§9Spectator mode not yet implemented"));
    }

    private void handleLeaderboard() {
        showLeaderboard();
    }

    private void handleChallenge() {
        player.sendSystemMessage(Component.literal("§eUse /chess challenge <player> to challenge someone"));
    }

    private void handleSettings() {
        player.sendSystemMessage(Component.literal("§7Settings GUI not yet implemented"));
    }

    private void showLeaderboard() {
        List<PlayerData> leaders = GameManager.getInstance().getLeaderboard(10);
        player.sendSystemMessage(Component.literal("§6=== Chess Leaderboard ==="));
        for (int i = 0; i < leaders.size(); i++) {
            PlayerData data = leaders.get(i);
            player.sendSystemMessage(Component.literal(String.format(
                    "§7%d. §f%s §7- §6%d ELO §7(%d games, %.1f%% win rate)",
                    i + 1, data.playerName, data.elo, data.gamesPlayed, data.getWinRate() * 100
            )));
        }
    }
}