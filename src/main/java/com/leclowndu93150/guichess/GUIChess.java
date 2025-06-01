package com.leclowndu93150.guichess;

import com.leclowndu93150.guichess.command.ChessCommands;
import com.leclowndu93150.guichess.events.PlayerEventHandler;
import com.leclowndu93150.guichess.game.GameManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(GUIChess.MODID)
public class GUIChess {

    public static final String MODID = "guichess";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GUIChess(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(PlayerEventHandler.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        GameManager.getInstance().initialize(server);
        LOGGER.info("GUIChess initialized with server");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GameManager.getInstance().shutdown();
        LOGGER.info("GUIChess shutdown complete");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChessCommands.registerCommands(event);
        LOGGER.info("Chess commands registered");
    }

    //TODO Weird behaviour, basicaly our pieces are in the opposite side, for both players, timers are placed correctly but not the pieces
}