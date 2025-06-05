package com.leclowndu93150.guichess.gui.game;

import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
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

        // Move analysis request to hotbar (slot 76)
        setSlot(76, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(Component.literal("§dRequest Analysis"))
                .setCallback((index, type, action, gui) -> {
                    handleHint();
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
        } else {
            setSlot(53, blackTimer);
            setSlot(26, whiteTimer);
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
        super.onClose();
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