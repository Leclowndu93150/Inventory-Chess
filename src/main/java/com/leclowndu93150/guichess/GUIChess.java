package com.leclowndu93150.guichess;

import com.leclowndu93150.guichess.command.ChessCommands;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.events.PlayerEventHandler;
import com.leclowndu93150.guichess.game.GameManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import org.slf4j.Logger;

import java.util.UUID;

@Mod(GUIChess.MODID)
public class GUIChess {

    public static final String MODID = "guichess";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GUIChess(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(PlayerEventHandler.class);
        //modEventBus.addListener(this::registerConfigurationTasks);
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

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        GameManager.getInstance().initialize(server);
        LOGGER.info("GUIChess initialized with server");
        
        // Initialize Stockfish asynchronously
        StockfishIntegration.getInstance().waitUntilReady().thenAccept(ready -> {
            if (ready) {
                LOGGER.info("Stockfish engine initialized successfully");
            } else {
                LOGGER.warn("Stockfish engine failed to initialize - hints and analysis will be unavailable");
            }
        });
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GameManager.getInstance().shutdown();
        StockfishIntegration.getInstance().shutdown();
        LOGGER.info("GUIChess shutdown complete");
    }

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