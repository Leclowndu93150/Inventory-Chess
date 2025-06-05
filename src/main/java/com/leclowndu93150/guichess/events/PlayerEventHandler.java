package com.leclowndu93150.guichess.events;

import com.leclowndu93150.guichess.game.core.ChessGame;
import com.leclowndu93150.guichess.game.core.GameManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GameManager gameManager = GameManager.getInstance();
        ChessGame game = gameManager.getPlayerGame(player);

        if (game != null && game.isGameActive()) {
            gameManager.getPlayerGUI(player).setAutoReopen(false);

            // Schedule GUI reopen after respawn (3 seconds delay)
            gameManager.getServer().execute(() -> {
                gameManager.getServer().execute(() -> {
                    try {
                        Thread.sleep(3000);
                        if (game.isGameActive()) {
                            gameManager.getPlayerGUI(player).setAutoReopen(true);
                            gameManager.getPlayerGUI(player).open();
                            player.sendSystemMessage(Component.literal("§eChess game restored after respawn."));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });
        }
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