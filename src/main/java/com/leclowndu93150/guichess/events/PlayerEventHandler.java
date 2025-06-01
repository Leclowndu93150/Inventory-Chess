package com.leclowndu93150.guichess.events;

import com.leclowndu93150.guichess.game.ChessGame;
import com.leclowndu93150.guichess.game.GameManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber
public class PlayerEventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GameManager gameManager = GameManager.getInstance();
        ChessGame game = gameManager.getPlayerGame(player);

        if (game != null && game.isGameActive()) {
            ServerPlayer opponent = game.getOpponent(player);

            game.resign(player);

            if (opponent != null) {
                opponent.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " disconnected. You win!"));
            }
        }

        gameManager.adminRemoveChallengesForPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GameManager gameManager = GameManager.getInstance();
        ChessGame game = gameManager.getPlayerGame(player);

        if (game != null && game.isGameActive()) {
            player.sendSystemMessage(Component.literal("§eYou left your chess game area. Reopening chess board..."));

            gameManager.getPlayerGUI(player).open();
        }
    }
}