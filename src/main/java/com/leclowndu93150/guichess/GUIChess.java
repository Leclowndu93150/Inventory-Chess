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

    //TODO when player dies while a match is going it will flicker between the respawn screen and the chess match
    //TODO: WHY THE FUCK CAN WE EAT THE KING ? We just need to checkmate
    //TODO: improve the SquareGenerator to take all the textures necessary and the background. make new ones (red but kinda cream red you get me ?), make quares for last moved, make squared for valid moves. and then overlay those for each available chess piece. also update the datagen to reflect those changes (and also the enum). btw each chess piece is 16x16 (just in case)
    //TODO: even tho i chose a mode it always defaults to blitz 5 min
    //TODO: add a mode to randomize the side when challenging sometone
    //TODO: add warning when 1 min left, 10s left (on modes where there is a lot of time) (in chat message with a sound probably)
    //TODO: Valid move textures (for empty slots) is available at guichess/board/valid_(color)_square.png
    //TODO: after match ended update the client inventory again otherwise the pieces are still there
    //TODO: instead of "Chess Game", you have Chess Game against {Player}
    //TODO: Implement Forfeiting  and make sure to add a confirm mechanic
    //TODO:Optional but we can hear when our opponents click their gui it's annoying.

    //Class each of the issues to the class they need fixing, then apply all the fixes class by class and give them to me
}