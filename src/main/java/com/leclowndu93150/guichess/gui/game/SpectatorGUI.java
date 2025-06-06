package com.leclowndu93150.guichess.gui.game;

import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.game.core.BotVsBotGame;
import com.leclowndu93150.guichess.game.core.ChessBoard;
import com.leclowndu93150.guichess.data.models.PlayerData;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.game.core.ChessGame;
import com.leclowndu93150.guichess.game.core.GameManager;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import com.leclowndu93150.guichess.util.time.TimeHelper;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Spectator interface for watching ongoing chess games.
 * 
 * <p>Allows viewers to observe games in real-time with the ability to switch perspectives
 * between white and black views, request hints, and access game information without
 * interfering with the actual game play.
 * 
 * @author GUIChess
 * @since 1.0
 */
public class SpectatorGUI extends ChessGUI {
    private PieceColor viewPerspective = PieceColor.WHITE;

    /**
     * Creates a new spectator GUI for the given game.
     * 
     * @param spectator the player watching the game
     * @param game the game being observed
     */
    public SpectatorGUI(ServerPlayer spectator, ChessGame game) {
        super(spectator, game, PieceColor.WHITE);
        setTitle(Component.literal("§9Spectating Chess Game"));
    }

    @Override
    protected PieceColor getBoardPerspective() {
        return viewPerspective;
    }

    @Override
    protected void handleSquareClick(ChessPosition position) {
        ChessPiece clickedPiece = getBoard().getPiece(position);
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);

        if (clickedPiece != null) {
            player.sendSystemMessage(Component.literal("§9" + clickedPiece.getDisplayName().getString() + " at " + position.toNotation()));
        } else {
            player.sendSystemMessage(Component.literal("§9Empty square " + position.toNotation()));
        }
    }

    @Override
    protected void setupUtilitySlots() {
        setupSpectatorUtilities();
    }

    private void setupSpectatorUtilities() {
        updateSpectatorTimerDisplays();
        updateTurnIndicator();
        setSlot(62, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§7Spectator Mode"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    player.sendSystemMessage(Component.literal("§7You are spectating this game"));
                }));

        setSlot(17, new GuiElementBuilder(Items.COMPASS)
                .setName(Component.literal("§9Switch Perspective"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    viewPerspective = viewPerspective.opposite();
                    player.sendSystemMessage(Component.literal("§9Now viewing from " +
                            (viewPerspective == PieceColor.WHITE ? "White's" : "Black's") + " perspective"));
                    updateBoard();
                }));

        setSlot(71, new GuiElementBuilder(Items.ENDER_PEARL)
                .setName(Component.literal("§cStop Spectating"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    GameManager.getInstance().removeSpectator(game, player);
                    close();
                }));

        setSlot(8, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7Game Info"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    String whitePlayer = game.getWhitePlayer() != null ? game.getWhitePlayer().getName().getString() : "Bot";
                    String blackPlayer = game.getBlackPlayer() != null ? game.getBlackPlayer().getName().getString() : "Bot";
                    player.sendSystemMessage(Component.literal("§7White: " + whitePlayer + " vs Black: " + blackPlayer));
                    player.sendSystemMessage(Component.literal("§7Time Control: " + game.getTimeControl().displayName));
                }));

        // Analysis button at left corner of hotbar (slot 81)
        setSlot(81, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(Component.literal("§dRequest Analysis"))
                .setCallback((index, type, action, gui) -> {
                    handleHint();
                }));
                
        // Close spectate button at right corner of hotbar (slot 89) that ends the match
        setSlot(89, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cEnd Match"))
                .setLore(List.of(
                    Component.literal("§7Click to stop the entire match"),
                    Component.literal("§7and close spectating")
                ))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    
                    // End the entire game
                    if (game instanceof BotVsBotGame) {
                        game.endGame(GameState.DRAW_BY_AGREEMENT);
                        player.sendSystemMessage(Component.literal("§cBot vs Bot match ended"));
                    } else {
                        GameManager.getInstance().removeSpectator(game, player);
                        player.sendSystemMessage(Component.literal("§7Stopped spectating"));
                    }
                    close();
                }));
    }

    private void updateSpectatorTimerDisplays() {
        ItemStack whiteClockItem = TimeHelper.getClockItem(game.getWhiteTimeLeft());
        ItemStack blackClockItem = TimeHelper.getClockItem(game.getBlackTimeLeft());
        
        GuiElementBuilder whiteTimer = GuiElementBuilder.from(whiteClockItem)
                .setName(Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())));

        GuiElementBuilder blackTimer = GuiElementBuilder.from(blackClockItem)
                .setName(Component.literal("§8Black: " + game.formatTime(game.getBlackTimeLeft())));

        if (viewPerspective == PieceColor.WHITE) {
            setSlot(53, whiteTimer);
            setSlot(26, blackTimer);
            
            // White evaluation book at slot 62
            updatePlayerEvaluationBook(62, true);
            // Black evaluation book at slot 17
            updatePlayerEvaluationBook(17, false);
        } else {
            setSlot(53, blackTimer);
            setSlot(26, whiteTimer);
            
            // Black evaluation book at slot 62
            updatePlayerEvaluationBook(62, false);
            // White evaluation book at slot 17
            updatePlayerEvaluationBook(17, true);
        }
    }
    
    private void updatePlayerEvaluationBook(int slot, boolean isWhite) {
        String playerName = getPlayerName(isWhite);
        String playerElo = getPlayerElo(isWhite);
        String evaluation = getCurrentEvaluation();
        String whoIsWinning = getWhoIsWinning();
        String materialCount = getMaterialCount(isWhite);
        String moveCount = getMoveCount();
        
        List<Component> lore = List.of(
            Component.literal("§7Player: §f" + playerName),
            Component.literal("§7ELO: §e" + playerElo),
            Component.literal("§7Material: §b" + materialCount),
            Component.literal(""),
            Component.literal("§7Position Eval: §d" + evaluation),
            Component.literal("§7Status: §a" + whoIsWinning),
            Component.literal("§7Move: §6" + moveCount),
            Component.literal(""),
            Component.literal("§8Click to request analysis")
        );
        
        String bookTitle = isWhite ? "§fWhite Analysis" : "§8Black Analysis";
        
        // Player evaluation book
        setSlot(slot, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal(bookTitle))
                .setLore(lore)
                .setCallback((index, type, action, gui) -> {
                    handleHint();
                }));
    }
    
    private String getPlayerName(boolean isWhite) {
        if (game instanceof BotVsBotGame) {
            BotVsBotGame botGame = (BotVsBotGame) game;
            return isWhite ? botGame.getWhiteBot().getName() : botGame.getBlackBot().getName();
        } else {
            ServerPlayer player = isWhite ? game.getWhitePlayer() : game.getBlackPlayer();
            return player != null ? player.getName().getString() : "Bot";
        }
    }
    
    private String getPlayerElo(boolean isWhite) {
        if (game instanceof BotVsBotGame) {
            BotVsBotGame botGame = (BotVsBotGame) game;
            return String.valueOf(isWhite ? botGame.getWhiteBot().getElo() : botGame.getBlackBot().getElo());
        } else {
            ServerPlayer player = isWhite ? game.getWhitePlayer() : game.getBlackPlayer();
            if (player != null) {
                PlayerData data = GameManager.getInstance().getPlayerData(player);
                return String.valueOf(data.elo);
            }
            return "1500"; // Default for bot
        }
    }
    
    private String getCurrentEvaluation() {
        // This would ideally get real-time evaluation from engine
        // For now, return a placeholder based on game state
        GameState state = game.getBoard().getGameState();
        return switch (state) {
            case WHITE_TURN, CHECK_WHITE -> "Slight advantage";
            case BLACK_TURN, CHECK_BLACK -> "Slight advantage"; 
            case CHECKMATE_WHITE_WINS -> "White wins!";
            case CHECKMATE_BLACK_WINS -> "Black wins!";
            case STALEMATE -> "Draw - Stalemate";
            case DRAW_FIFTY_MOVE -> "Draw - 50 moves";
            case DRAW_THREEFOLD -> "Draw - Repetition";
            case DRAW_INSUFFICIENT -> "Draw - Material";
            default -> "Equal position";
        };
    }
    
    private String getWhoIsWinning() {
        GameState state = game.getBoard().getGameState();
        return switch (state) {
            case WHITE_TURN, CHECK_BLACK -> "White to move";
            case BLACK_TURN, CHECK_WHITE -> "Black to move";
            case CHECKMATE_WHITE_WINS -> "§aWhite wins!";
            case CHECKMATE_BLACK_WINS -> "§aBlack wins!";
            case STALEMATE, DRAW_FIFTY_MOVE, DRAW_THREEFOLD, DRAW_INSUFFICIENT -> "§7Draw";
            default -> "Game in progress";
        };
    }
    
    private String getMaterialCount(boolean isWhite) {
        // Count material value for the player
        ChessBoard board = game.getBoard();
        int materialValue = 0;
        
        for (int i = 0; i < 64; i++) {
            ChessPiece piece = board.getPiece(ChessPosition.fromIndex(i));
            if (piece != null && piece.isWhite() == isWhite) {
                materialValue += getPieceValue(piece.getType());
            }
        }
        
        return String.valueOf(materialValue);
    }
    
    private int getPieceValue(PieceType type) {
        return switch (type) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> 0; // King has no material value
        };
    }
    
    private String getMoveCount() {
        int fullMoves = game.getBoard().getFullMoveNumber();
        boolean isWhiteTurn = game.getBoard().getCurrentTurn() == PieceColor.WHITE;
        
        if (isWhiteTurn) {
            return String.valueOf(fullMoves);
        } else {
            return fullMoves + "...";
        }
    }

    @Override
    protected void updateUtilitySlots() {
        updateSpectatorTimerDisplays();
        updateTurnIndicator();
    }

    @Override
    public void updateTimerDisplays() {
        updateSpectatorTimerDisplays();
    }

    @Override
    protected void updateDrawButtons() {
    }

    @Override
    protected void setupAnalysisTools() {
    }

    @Override
    public boolean canPlayerClose() {
        return true;
    }

    @Override
    public void onClose() {
        GameManager.getInstance().removeSpectator(game, player);
        player.sendSystemMessage(Component.literal("§7Stopped spectating chess game"));
        // Don't call super.onClose() to avoid "reopening chess board" message
    }

    @Override
    protected void handleResign() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot resign!"));
    }

    @Override
    protected void handleOfferDraw() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot offer draws!"));
    }

    @Override
    protected void handleAcceptDraw() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot accept draws!"));
    }

    @Override
    protected void handleDeclineDraw() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot decline draws!"));
    }

    @Override
    protected void handleCancelDrawOffer() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot cancel draw offers!"));
    }

    @Override
    protected void handleEnableAnalysisMode() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot enable analysis mode!"));
    }

    @Override
    protected void handleHint() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.HINT);
        StockfishEngineManager.getInstance().requestHint(getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§9Spectator Analysis: " + hint));
        });
    }
}