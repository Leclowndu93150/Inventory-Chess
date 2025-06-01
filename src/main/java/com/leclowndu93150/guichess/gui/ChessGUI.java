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
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

public class ChessGUI extends SimpleGui {
    private final ChessGame game;
    private final PieceColor playerColor;
    protected final ServerPlayer player;

    private boolean showingPromotionDialog = false;
    private ChessPosition promotionFrom;
    private ChessPosition promotionTo;

    public ChessGUI(ServerPlayer player, ChessGame game, PieceColor playerColor) {
        super(MenuType.GENERIC_9x6, player, true);
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
        if (!isOpen()) return;

        if (showingPromotionDialog) {
            setupPromotionGUI();
            return;
        }

        ChessBoard board = game.getBoard();
        Set<ChessPosition> validMoves = game.getValidMoves();
        ChessPosition selected = game.getSelectedSquare();
        List<ChessMove> moveHistory = board.getMoveHistory();

        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;

            int chessRank, chessFile;
            if (playerColor == PieceColor.WHITE) {
                chessRank = row;
                chessFile = col;
            } else {
                chessRank = 7 - row;
                chessFile = col; // Corrected: files are not reversed for black's view
            }

            ChessPosition position = new ChessPosition(chessFile, chessRank);
            ChessPiece piece = board.getPiece(position);

            int slotIndex = i + i / 8;
            if (slotIndex >= 72) continue; // Should not happen if GUI is 9x8 effectively

            GuiElementBuilder builder;

            if (piece != null) {
                builder = createPieceElement(piece, position);
            } else {
                builder = createEmptySquareElement(position);
            }

            boolean isHighlighted = false;
            if (!moveHistory.isEmpty()) {
                ChessMove lastMove = moveHistory.get(moveHistory.size() - 1);
                if (position.equals(lastMove.from)) {
                    builder.setCustomModelData(BoardSquare.LAST_MOVE_FROM.modelData);
                    isHighlighted = true;
                } else if (position.equals(lastMove.to)) {
                    builder.setCustomModelData(BoardSquare.LAST_MOVE_TO.modelData);
                    isHighlighted = true;
                }
            }

            if (!isHighlighted) {
                if (position.equals(selected)) {
                    builder.setCustomModelData(BoardSquare.SELECTED_SQUARE.modelData);
                } else if (validMoves.contains(position)) {
                    if (board.getPiece(position) != null) { // Use board.getPiece for accurate capture indication
                        builder.setCustomModelData(BoardSquare.CAPTURE_MOVE.modelData);
                    } else {
                        builder.setCustomModelData(BoardSquare.VALID_MOVE.modelData);
                    }
                }
            }

            final ChessPosition currentPos = position; // effectively final for lambda
            builder.setCallback((index, type, action, gui) -> {
                if (!game.isGameActive()) return;
                game.selectSquare(player, currentPos);
            });

            setSlot(slotIndex, builder);
        }
        updateUtilitySlots();
    }

    private GuiElementBuilder createPieceElement(ChessPiece piece, ChessPosition position) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(piece.modelData)
                .setName(piece.displayName.copy().append(Component.literal(" - " + position.toNotation())));
    }

    private GuiElementBuilder createEmptySquareElement(ChessPosition position) {
        boolean isLight = (position.file + position.rank) % 2 == 0;
        BoardSquare squareType = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(squareType.modelData)
                .setName(Component.literal(position.toNotation()))
                .hideDefaultTooltip();
    }

    protected void setupUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();

        setSlot(62, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));
        setSlot(17, createUtilityButton(GameUtility.RESIGN_BUTTON, this::handleResign));

        updateDrawButtons();
        setupAnalysisTools();
    }

    private void updateUtilitySlots() {
        updateTimerDisplays();
        updateTurnIndicator();
        updateDrawButtons();
    }

    private void updateTimerDisplays() {
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

    private void updateTurnIndicator() {
        PieceColor currentTurn = game.getBoard().getCurrentTurn();
        GameState gameState = game.getBoard().getGameState();
        GuiElementBuilder turnIndicator;

        if (!game.isGameActive() || gameState == GameState.CHECKMATE_WHITE_WINS || gameState == GameState.CHECKMATE_BLACK_WINS ||
                gameState.name().contains("DRAW") || gameState.name().contains("RESIGN") ||
                gameState.name().contains("TIME_OUT") || gameState == GameState.STALEMATE) {
            turnIndicator = new GuiElementBuilder(Items.YELLOW_STAINED_GLASS)
                    .setName(Component.literal("§6Game Finished"));
        } else if (currentTurn == PieceColor.WHITE) {
            turnIndicator = new GuiElementBuilder(Items.LIME_STAINED_GLASS)
                    .setName(Component.literal("§fWhite's Move"));
        } else {
            turnIndicator = new GuiElementBuilder(Items.RED_STAINED_GLASS)
                    .setName(Component.literal("§8Black's Move"));
        }
        setSlot(44, turnIndicator);
        setSlot(35, turnIndicator);
    }

    private void setupAnalysisTools() {
        setSlot(9, createUtilityButton(GameUtility.ANALYZE_POSITION, this::handleAnalyze));
        setSlot(18, createUtilityButton(GameUtility.STOCKFISH_HINT, this::handleHint));

        PlayerData playerDataObj = GameManager.getInstance().getPlayerData(player);
        setSlot(27, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .setName(Component.literal("§6ELO: " + playerDataObj.elo)));

        setSlot(36, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7" + game.getTimeControl().displayName)));
    }

    private void updateDrawButtons() {
        if (!game.isGameActive()) {
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

    private GuiElementBuilder createUtilityButton(GameUtility utility, Runnable action) {
        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(utility.modelData)
                .setName(utility.displayName)
                .setCallback((index, type, actionType, gui) -> {
                    if (game.isGameActive() || utility == GameUtility.EXIT_BUTTON) { // Allow exit even if game inactive
                        action.run();
                    }
                });
    }

    public void showPromotionDialog(ChessPosition from, ChessPosition to) {
        this.showingPromotionDialog = true;
        this.promotionFrom = from;
        this.promotionTo = to;
        setupPromotionGUI();
    }

    private void setupPromotionGUI() {
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        setSlot(39, createPromotionOption(PieceType.QUEEN));  // Centered more (example slots)
        setSlot(40, createPromotionOption(PieceType.ROOK));
        setSlot(41, createPromotionOption(PieceType.BISHOP));
        setSlot(42, createPromotionOption(PieceType.KNIGHT));


        setSlot(31, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§eChoose promotion piece")));
    }

    private GuiElementBuilder createPromotionOption(PieceType pieceType) {
        ChessPiece piece = ChessPiece.fromColorAndType(game.getPlayerColor(player), pieceType); // Use game's current player color
        GameUtility utility = switch (pieceType) {
            case QUEEN -> GameUtility.PROMOTE_QUEEN;
            case ROOK -> GameUtility.PROMOTE_ROOK;
            case BISHOP -> GameUtility.PROMOTE_BISHOP;
            case KNIGHT -> GameUtility.PROMOTE_KNIGHT;
            default -> GameUtility.PROMOTE_QUEEN; // Should not happen
        };

        return new GuiElementBuilder(Items.GRAY_DYE)
                .setCustomModelData(piece.modelData) // Display actual piece model
                .setName(utility.displayName)
                .setCallback((index, type, action, gui) -> {
                    game.makeMove(player, promotionFrom, promotionTo, pieceType);
                    showingPromotionDialog = false;
                    updateBoard();
                });
    }

    private void handleResign() {
        game.resign(player);
    }

    private void handleOfferDraw() {
        game.offerDraw(player);
    }

    private void handleAcceptDraw() {
        game.respondToDraw(player, true);
    }

    private void handleDeclineDraw() {
        game.respondToDraw(player, false);
    }

    private void handleCancelDrawOffer() {
        game.cancelDrawOffer(player);
    }

    private void handleAnalyze() {
        if (game.isAnalysisMode()) {
            player.sendSystemMessage(Component.literal("§dAnalysis mode already enabled."));
        } else {
            game.enableAnalysisMode();
            player.sendSystemMessage(Component.literal("§dAnalysis mode enabled!"));
        }
    }

    private void handleHint() {
        if (!game.isPlayerTurn(player) && !game.isAnalysisMode()) {
            player.sendSystemMessage(Component.literal("§cIt's not your turn to get a hint."));
            return;
        }
        StockfishIntegration.getInstance().requestHint(game.getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
        });
    }
}