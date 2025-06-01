package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.game.ChessGame;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

// Spectator GUI implementation
public class SpectatorGUI extends ChessGUI {
    public SpectatorGUI(ServerPlayer spectator, ChessGame game) {
        super(spectator, game, PieceColor.WHITE); // Always show from white's perspective for spectators
        setTitle(Component.literal("§9Spectating Chess Game"));
    }

    @Override
    protected void setupUtilitySlots() {
        super.setupUtilitySlots();

        // Override some buttons for spectator mode
        setSlot(51, new GuiElementBuilder(Items.BARRIER)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§7Spectator Mode"))
                .setCallback((slot, type, action, gui) -> {
                    player.sendSystemMessage(Component.literal("§7You are spectating this game"));
                }));

        setSlot(52, new GuiElementBuilder(Items.COMPASS)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§9Switch Perspective"))
                .setCallback((slot, type, action, gui) -> {
                    // Toggle perspective between white/black
                    // Implementation would flip the board view
                }));
    }
}