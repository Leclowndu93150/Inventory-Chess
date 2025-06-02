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


    //DONE: add a mode to randomize the side when challenging someone - Added /chess challengerandom command
    //DONE: after match ended update the client inventory again otherwise the pieces are still there - Fixed with proper inventory sync
    //PARTIAL: Implement Forfeiting and make sure to add a confirm mechanic - Added confirmation dialog but resign button placement needs fixing
    //TODO: Add a sound when opponent played (fix sound effets with check etc, and put the king in check texture like how the pieces can be eaten), add breaking sound when we get eaten too, like when we eat someone
    //DONE: don't delete player's inventories when in a game or anything, just save it until game ended - Implemented inventory save/restore system
    //DONE: don't allow the king to move if it's going to put it in a check state - Already implemented in getLegalMoves()
    //TODO: Fix check detection not working properly - Added debug logging to investigate
//    Add Stockfish support
//    Can request up to 3 hints per game (configurable in the challenge menu)
//
//    Will make a new gui, simple chest, allows you to chose if against a bot or a player, let's you chose the mode, time, amount of hints, if against a bot it's elo. If it's either random or chosen sides. Other settings
//
//    Elo will probably use materials like diamond etc, hints will use the light level light bulbs item, with 0, 1, 2 or 3.
//
//    Being able to bet items also through a GUI
//
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