package com.leclowndu93150.guichess;

import com.leclowndu93150.guichess.command.ChessCommands;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.events.PlayerEventHandler;
import com.leclowndu93150.guichess.game.core.GameManager;
import com.leclowndu93150.guichess.util.time.TimeHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Main mod class for GUIChess - a comprehensive chess implementation for Minecraft.
 * 
 * <p>Provides a complete chess gaming experience including:
 * <ul>
 * <li>Player vs Player games with ELO rating system</li>
 * <li>Player vs AI games with configurable difficulty levels</li>
 * <li>Real-time spectator system</li>
 * <li>Post-game analysis with Stockfish integration</li>
 * <li>Challenge system with optional betting</li>
 * <li>Statistics tracking and match history</li>
 * </ul>
 * 
 * @author GUIChess
 * @since 1.0
 */
@Mod(GUIChess.MODID)
public class GUIChess {

    public static final String MODID = "guichess";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Initializes the GUIChess mod and registers event handlers.
     * 
     * @param modEventBus the mod event bus
     * @param modContainer the mod container
     */
    public GUIChess(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(PlayerEventHandler.class);
    }

    public void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new ServerResourcePackConfigurationTask(
                new MinecraftServer.ServerResourcePackInfo(
                        UUID.fromString("55859871-ba02-4215-8828-cc7a45099eb6"),
                        "https://github.com/Leclowndu93150/leclowndu93150.github.io/raw/refs/heads/main/serversidesummer%20assets.zip",
                        "a94e7fca07a680ffdb8abc44e28dcde63c0f79f7",
                        true,
                        null
                )));
    }

    /**
     * Handles server startup - initializes the game manager and chess engine.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        GameManager.getInstance().initialize(server);
        LOGGER.info("GUIChess initialized with server");
        
        StockfishEngineManager.getInstance().waitUntilReady().thenAccept(ready -> {
            if (ready) {
                LOGGER.info("Stockfish engine initialized successfully");
            } else {
                LOGGER.warn("Stockfish engine failed to initialize - hints and analysis will be unavailable");
            }
        });
    }

    /**
     * Handles server shutdown - cleans up the game manager and chess engine.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GameManager.getInstance().shutdown();
        StockfishEngineManager.getInstance().shutdown();
        LOGGER.info("GUIChess shutdown complete");
    }

    /**
     * Registers chess commands when the server starts.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChessCommands.registerCommands(event);
        LOGGER.info("Chess commands registered");
    }



//    Elo will be in a saved data format along with the match history, with the wins, loses etc
//
//    Add a new set of pieces overlay for captured pieces which will be in the hotbar or on top probably
//
//
//    Add an item in the bottom corner of the inventory, it will have z coordinates, so that it loads a gui texture (on top of the actual screen)
//    All of the other items should load on top of that gui item. (Everything handled in datagen)
//
//    If wanting to be fancy we can add actual number textures. From 0 to 9
}