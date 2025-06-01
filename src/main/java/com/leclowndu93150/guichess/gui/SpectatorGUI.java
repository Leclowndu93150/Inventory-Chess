package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessGame;
import com.leclowndu93150.guichess.game.GameManager;
import com.leclowndu93150.guichess.util.ChessSoundManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public class SpectatorGUI extends ChessGUI {
    private PieceColor viewPerspective = PieceColor.WHITE;

    public SpectatorGUI(ServerPlayer spectator, ChessGame game) {
        super(spectator, game, PieceColor.WHITE); // Initial perspective is white
        setTitle(Component.literal("§9Spectating Chess Game"));
    }

    @Override
    protected PieceColor getBoardPerspective() {
        return viewPerspective; // Use our custom perspective instead of playerColor
    }

    @Override
    protected void handleSquareClick(ChessPosition position) {
        // Spectators can't make moves, just provide info
        ChessPiece clickedPiece = getBoard().getPiece(position);
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);

        if (clickedPiece != null) {
            player.sendSystemMessage(Component.literal("§9" + clickedPiece.displayName.getString() + " at " + position.toNotation()));
        } else {
            player.sendSystemMessage(Component.literal("§9Empty square " + position.toNotation()));
        }
    }

    @Override
    protected void setupUtilitySlots() {
        // Override utility slots for spectator mode
        setupSpectatorUtilities();
    }

    private void setupSpectatorUtilities() {
        // Show timers (but not as part of regular timer display)
        updateSpectatorTimerDisplays();
        updateTurnIndicator();

        // Spectator mode indicator
        setSlot(62, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§7Spectator Mode"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    player.sendSystemMessage(Component.literal("§7You are spectating this game"));
                }));

        // Switch perspective
        setSlot(17, new GuiElementBuilder(Items.COMPASS)
                .setName(Component.literal("§9Switch Perspective"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    viewPerspective = viewPerspective.opposite();
                    player.sendSystemMessage(Component.literal("§9Now viewing from " +
                            (viewPerspective == PieceColor.WHITE ? "White's" : "Black's") + " perspective"));
                    updateBoard();
                }));

        // Stop spectating
        setSlot(71, new GuiElementBuilder(Items.ENDER_PEARL)
                .setName(Component.literal("§cStop Spectating"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    GameManager.getInstance().removeSpectator(game, player);
                    close();
                }));

        // Game info
        setSlot(8, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7Game Info"))
                .setCallback((index, type, action, gui) -> {
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    String whitePlayer = game.getWhitePlayer() != null ? game.getWhitePlayer().getName().getString() : "Unknown";
                    String blackPlayer = game.getBlackPlayer() != null ? game.getBlackPlayer().getName().getString() : "Unknown";
                    player.sendSystemMessage(Component.literal("§7White: " + whitePlayer + " vs Black: " + blackPlayer));
                    player.sendSystemMessage(Component.literal("§7Time Control: " + game.getTimeControl().displayName));
                }));

        // Show analysis if available
        setSlot(9, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(Component.literal("§dRequest Analysis"))
                .setCallback((index, type, action, gui) -> {
                    handleHint(); // Reuse the hint functionality for spectators
                }));
    }

    private void updateSpectatorTimerDisplays() {
        GuiElementBuilder whiteTimer = new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("§fWhite: " + game.formatTime(game.getWhiteTimeLeft())));

        GuiElementBuilder blackTimer = new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("§8Black: " + game.formatTime(game.getBlackTimeLeft())));

        // Always show from spectator's perspective
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
    protected void updateTimerDisplays() {
        updateSpectatorTimerDisplays();
    }

    @Override
    protected void updateDrawButtons() {
        // Spectators don't see draw buttons - do nothing
    }

    @Override
    protected void setupAnalysisTools() {
        // Analysis tools are handled in setupSpectatorUtilities
    }

    @Override
    public boolean canPlayerClose() {
        return true; // Spectators can always close
    }

    @Override
    public void onClose() {
        GameManager.getInstance().removeSpectator(game, player);
        // Don't call ChessGUI's onClose which tries to reopen for active games
        super.onClose(); // Call SimpleGui.onClose()
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
    protected void handleAnalyze() {
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        player.sendSystemMessage(Component.literal("§cSpectators cannot enable analysis mode!"));
    }

    @Override
    protected void handleHint() {
        // Allow spectators to get hints/analysis
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.HINT);
        StockfishIntegration.getInstance().requestHint(getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§9Spectator Analysis: " + hint));
        });
    }
}