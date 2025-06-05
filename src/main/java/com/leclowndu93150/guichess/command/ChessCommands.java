package com.leclowndu93150.guichess.command;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.GameHistory;
import com.leclowndu93150.guichess.data.MatchHistoryManager;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.engine.StockfishWebIntegration;
import com.leclowndu93150.guichess.game.*;
import com.leclowndu93150.guichess.gui.ChessGUI;
import com.leclowndu93150.guichess.gui.ChallengeFlowGUI;
import com.leclowndu93150.guichess.gui.ChallengeAcceptGUI;
import com.leclowndu93150.guichess.gui.PracticeBoardGUI;
import com.leclowndu93150.guichess.gui.DebugSimpleGUI;
import com.leclowndu93150.guichess.gui.DebugDoubleGUI;
import com.leclowndu93150.guichess.util.OverlayModelDataRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines all chess-related commands for player interaction with the chess system.
 * Includes game management, challenges, spectating, and administrative functions.
 */
@EventBusSubscriber
public class ChessCommands {

    private static final SuggestionProvider<CommandSourceStack> TIME_CONTROL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(TimeControl.values()).map(tc -> tc.name().toLowerCase()),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    context.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(p -> p.getName().getString()),
                    builder
            );


    /**
     * Registers all chess commands with the command dispatcher.
     * 
     * @param event the command registration event
     */
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("chess")
                        .then(Commands.literal("play")
                                .then(Commands.argument("opponent", EntityArgument.player())
                                        .then(Commands.argument("timecontrol", StringArgumentType.string())
                                                .suggests(TIME_CONTROL_SUGGESTIONS)
                                                .executes(ChessCommands::createGame))
                                        .executes(ctx -> createGameWithDefaultTime(ctx))))

                        .then(Commands.literal("challenge")
                                .executes(ChessCommands::openChallengeGUI))
                                
                        .then(Commands.literal("bot")
                                .then(Commands.argument("elo", IntegerArgumentType.integer(500, 3000))
                                        .executes(ChessCommands::playBot))
                                .executes(ctx -> playBotDefaultElo(ctx)))

                        .then(Commands.literal("accept")
                                .executes(ChessCommands::acceptChallenge))

                        .then(Commands.literal("decline")
                                .executes(ChessCommands::declineChallenge))

                        .then(Commands.literal("resign")
                                .executes(ChessCommands::resignGame))

                        .then(Commands.literal("draw")
                                .executes(ChessCommands::offerDraw))

                        .then(Commands.literal("spectate")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                        .executes(ChessCommands::spectateGame)))

                        .then(Commands.literal("stats")
                                .executes(ChessCommands::showOwnStats)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                        .executes(ChessCommands::showPlayerStats)))

                        .then(Commands.literal("leaderboard")
                                .executes(ChessCommands::showLeaderboard))

                        .then(Commands.literal("history")
                                .executes(ChessCommands::showGameHistory))

                        .then(Commands.literal("analyze")
                                .executes(ChessCommands::analyzeCurrentGame)
                                .then(Commands.argument("match_number", IntegerArgumentType.integer(1))
                                        .executes(ChessCommands::analyzeHistoryGame)))

                        .then(Commands.literal("hint")
                                .executes(ChessCommands::getHint))

                        .then(Commands.literal("board")
                                .executes(ChessCommands::openChessBoard))

                        .then(Commands.literal("fen")
                                .executes(ChessCommands::showFEN)
                                .then(Commands.argument("fen", StringArgumentType.greedyString())
                                        .executes(ChessCommands::loadFromFEN)))

                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.literal("reload")
                                        .executes(ChessCommands::reloadData))
                                .then(Commands.literal("backup")
                                        .executes(ChessCommands::backupData))
                                .then(Commands.literal("setelo")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("elo", StringArgumentType.string())
                                                        .executes(ChessCommands::setPlayerELO))))
                                .then(Commands.literal("unbusy")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                                .executes(ChessCommands::adminUnbusyPlayer)))
                                .then(Commands.literal("testoverlay")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.literal("§6=== Chess Overlay Model Data Test ==="));

                                            // Test a few overlay model data values
                                            Map<String, Integer> overlayData = OverlayModelDataRegistry.getAllModelData();
                                            int count = 0;
                                            for (Map.Entry<String, Integer> entry : overlayData.entrySet()) {
                                                if (count++ < 10) { // Show first 10
                                                    ItemStack testItem = new ItemStack(Items.GRAY_DYE);
                                                    testItem.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(entry.getValue()));

                                                    player.sendSystemMessage(Component.literal(
                                                            "§7" + entry.getKey() + " -> Model Data: " + entry.getValue()
                                                    ));

                                                    // Give the player one of each to test
                                                    if (count <= 5) {
                                                        testItem.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + entry.getKey()));
                                                        player.getInventory().add(testItem);
                                                    }
                                                }
                                            }

                                            player.sendSystemMessage(Component.literal("§aGave you 5 test overlay items. Total overlays: " + overlayData.size()));
                                            return 1;
                                        }))
                                .then(Commands.literal("debug")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.literal("simple")
                                                .executes(ChessCommands::openDebugSimpleGUI))
                                        .then(Commands.literal("double")
                                                .executes(ChessCommands::openDebugDoubleGUI)))));

    }

    private static int createGameWithDefaultTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer opponent = EntityArgument.getPlayer(context, "opponent");

        // Use player's favorite time control or default to BLITZ_5_0
        PlayerData playerData = GameManager.getInstance().getPlayerData(player);
        String timeControlName = playerData.favoriteTimeControl != null ? playerData.favoriteTimeControl : "BLITZ_5_0";

        return createGameInternal(context, opponent, timeControlName);
    }

    private static int createGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer opponent = EntityArgument.getPlayer(context, "opponent");
        String timeControlName = StringArgumentType.getString(context, "timecontrol");

        return createGameInternal(context, opponent, timeControlName);
    }

    private static int createGameInternal(CommandContext<CommandSourceStack> context, ServerPlayer opponent, String timeControlName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        if (player.equals(opponent)) {
            context.getSource().sendFailure(Component.literal("§cYou cannot play against yourself!"));
            return 0;
        }

        GameManager gameManager = GameManager.getInstance();

        if (gameManager.isPlayerBusy(player)) {
            context.getSource().sendFailure(Component.literal("§cYou are already in a game or have a pending challenge!"));
            return 0;
        }

        if (gameManager.isPlayerBusy(opponent)) {
            context.getSource().sendFailure(Component.literal("§c" + opponent.getName().getString() + " is already busy!"));
            return 0;
        }

        TimeControl timeControl;
        try {
            timeControl = TimeControl.valueOf(timeControlName.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid time control: " + timeControlName + ". Valid: " +
                    Arrays.stream(TimeControl.values()).map(tc -> tc.name().toLowerCase()).collect(Collectors.joining(", "))));
            return 0;
        }

        ChessGame game = gameManager.createGame(player, opponent, timeControl);

        player.sendSystemMessage(Component.literal("§aGame started against " + opponent.getName().getString() + " (" + timeControl.displayName + ")"));
        opponent.sendSystemMessage(Component.literal("§aGame started against " + player.getName().getString() + " (" + timeControl.displayName + ")"));

        return 1;
    }

    private static int openChallengeGUI(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        
        GameManager gameManager = GameManager.getInstance();
        if (gameManager.isPlayerBusy(player)) {
            context.getSource().sendFailure(Component.literal("§cYou are already in a game or have a pending challenge!"));
            return 0;
        }
        
        ChallengeFlowGUI challengeGUI = new ChallengeFlowGUI(player);
        challengeGUI.open();
        
        return 1;
    }

    private static int acceptChallenge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GameManager gameManager = GameManager.getInstance();

        ChessChallenge challengeToAccept = gameManager.getPendingChallenges().values().stream()
                .filter(c -> c.challenged.equals(player) && !c.isExpired())
                .max(Comparator.comparingLong(c -> c.challengeTime))
                .orElse(null);

        if (challengeToAccept == null) {
            context.getSource().sendFailure(Component.literal("§cYou have no pending challenges or the most recent one expired!"));
            return 0;
        }

        ChallengeAcceptGUI acceptGUI = new ChallengeAcceptGUI(player, challengeToAccept);
        acceptGUI.open();
        
        return 1;
    }

    private static int declineChallenge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GameManager gameManager = GameManager.getInstance();

        ChessChallenge challengeToDecline = gameManager.getPendingChallenges().values().stream()
                .filter(c -> c.challenged.equals(player) && !c.isExpired())
                .max(Comparator.comparingLong(c -> c.challengeTime))
                .orElse(null);

        if (challengeToDecline == null) {
            context.getSource().sendFailure(Component.literal("§cYou have no pending challenges to decline!"));
            return 0;
        }

        if (gameManager.declineChallenge(player, challengeToDecline)) {
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to decline challenge."));
            return 0;
        }
    }

    private static int resignGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null || !game.isGameActive()) {
            context.getSource().sendFailure(Component.literal("§cYou are not in an active game!"));
            return 0;
        }

        if (game.resign(player)) {
            context.getSource().sendSuccess(() -> Component.literal("§cYou resigned the game!"), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to resign (game might have just ended)."));
            return 0;
        }
    }

    private static int offerDraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null || !game.isGameActive()) {
            context.getSource().sendFailure(Component.literal("§cYou are not in an active game!"));
            return 0;
        }

        if (game.isDrawOffered() && game.getDrawOfferer().equals(player)) {
            context.getSource().sendFailure(Component.literal("§eYou have already offered a draw. /chess draw cancel to cancel."));
            return 0;
        } else if (game.isDrawOffered() && !game.getDrawOfferer().equals(player)) {
            context.getSource().sendFailure(Component.literal("§eYour opponent has offered a draw. Use /chess accept or /chess decline."));
            return 0;
        }


        if (game.offerDraw(player)) {
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to offer draw (e.g., already offered, or game state issues)."));
            return 0;
        }
    }

    private static int spectateGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer spectator = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        ChessGame game = GameManager.getInstance().getPlayerGame(target);
        if (game == null) {
            context.getSource().sendFailure(Component.literal("§c" + target.getName().getString() + " is not in a game!"));
            return 0;
        }

        if (GameManager.getInstance().getPlayerGame(spectator) != null) {
            context.getSource().sendFailure(Component.literal("§cYou cannot spectate while in a game. Resign or finish your game first."));
            return 0;
        }

        GameManager.getInstance().addSpectator(game, spectator);
        context.getSource().sendSuccess(() -> Component.literal("§9Now spectating " + target.getName().getString() + "'s game"), false);
        return 1;
    }

    private static int showOwnStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return showStatsFor(context, player);
    }

    private static int showPlayerStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        return showStatsFor(context, target);
    }

    private static int showStatsFor(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        PlayerData data = GameManager.getInstance().getPlayerData(target);

        context.getSource().sendSuccess(() -> Component.literal(
                "§6=== " + target.getName().getString() + "'s Chess Stats ===\n" +
                        "§7ELO Rating: §6" + data.getElo() + "\n" +
                        "§7Games Played: §f" + data.gamesPlayed + "\n" +
                        "§7Wins: §a" + data.wins + " §7(§a" + String.format("%.1f%%", data.getWinRate() * 100) + "§7)\n" +
                        "§7Losses: §c" + data.losses + "\n" +
                        "§7Draws: §e" + data.draws + "\n" +
                        "§7Favorite Time Control: §b" + (data.favoriteTimeControl != null ? data.favoriteTimeControl : "N/A")
        ), false);

        return 1;
    }

    private static int showLeaderboard(CommandContext<CommandSourceStack> context) {
        List<PlayerData> leaders = GameManager.getInstance().getLeaderboard(10);
        if (leaders.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7The leaderboard is empty or no players have played enough games yet."), false);
            return 1;
        }

        StringBuilder message = new StringBuilder("§6=== Chess Leaderboard (Top 10) ===\n");
        for (int i = 0; i < leaders.size(); i++) {
            PlayerData data = leaders.get(i);
            message.append(String.format(
                    "§7%d. §f%s §7- §6%d ELO §7(%d W / %d L / %d D - %d games)\n",
                    i + 1, data.playerName, data.getElo(), data.wins, data.losses, data.draws, data.gamesPlayed
            ));
        }

        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int showGameHistory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MatchHistoryManager historyManager = GameManager.getInstance().getMatchHistoryManager();
        
        if (historyManager == null) {
            context.getSource().sendFailure(Component.literal("§cMatch history not available."));
            return 0;
        }
        
        List<GameHistory> recentGames = historyManager.getRecentPlayerGames(player.getUUID(), 5);
        
        if (recentGames.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7You haven't played any games yet."), false);
            return 1;
        }
        
        StringBuilder message = new StringBuilder("§6=== Your Recent Games ===\n");
        for (int i = 0; i < recentGames.size(); i++) {
            GameHistory game = recentGames.get(i);
            String opponent = game.getOpponentName(player.getUUID());
            String result = game.getResultString();
            String date = game.getFormattedDate();
            
            message.append(String.format("§7%d. §f%s vs %s §7- %s §7(%s)\n",
                i + 1, game.isPlayerWhite(player.getUUID()) ? "You" : opponent, 
                game.isPlayerWhite(player.getUUID()) ? opponent : "You", result, date));
        }
        
        message.append("\n§7Use §e/chess analyze §7for your most recent game or §e/chess analyze <number> §7for a specific match!");
        
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int analyzeCurrentGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame currentGame = GameManager.getInstance().getPlayerGame(player);
        MatchHistoryManager historyManager = GameManager.getInstance().getMatchHistoryManager();

        // Check if player is in an active game
        if (currentGame != null && currentGame.isGameActive()) {
            context.getSource().sendFailure(Component.literal("§cCannot analyze during an active game. Finish your game first."));
            return 0;
        }

        if (historyManager == null) {
            context.getSource().sendFailure(Component.literal("§cMatch history not available."));
            return 0;
        }

        // Get player's most recent game for analysis
        List<GameHistory> recentGames = historyManager.getRecentPlayerGames(player.getUUID(), 1);
        
        if (recentGames.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNo games found to analyze."));
            return 0;
        }

        GameHistory mostRecentGame = recentGames.get(0);
        GameManager.getInstance().openMatchAnalysis(player, mostRecentGame.gameId);
        
        context.getSource().sendSuccess(() -> Component.literal("§aOpening analysis for your most recent game..."), false);
        return 1;
    }
    
    private static int analyzeHistoryGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int matchNumber = IntegerArgumentType.getInteger(context, "match_number");
        ChessGame currentGame = GameManager.getInstance().getPlayerGame(player);
        MatchHistoryManager historyManager = GameManager.getInstance().getMatchHistoryManager();

        // Check if player is in an active game
        if (currentGame != null && currentGame.isGameActive()) {
            context.getSource().sendFailure(Component.literal("§cCannot analyze during an active game. Finish your game first."));
            return 0;
        }

        if (historyManager == null) {
            context.getSource().sendFailure(Component.literal("§cMatch history not available."));
            return 0;
        }

        // Get enough games to find the requested match number
        List<GameHistory> recentGames = historyManager.getRecentPlayerGames(player.getUUID(), Math.max(matchNumber, 10));
        
        if (recentGames.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNo games found in your history."));
            return 0;
        }
        
        if (matchNumber > recentGames.size()) {
            context.getSource().sendFailure(Component.literal("§cMatch " + matchNumber + " not found. You only have " + recentGames.size() + " games in your recent history."));
            return 0;
        }

        // Games are indexed from 1 in the display, but 0-based in the list
        GameHistory selectedGame = recentGames.get(matchNumber - 1);
        GameManager.getInstance().openMatchAnalysis(player, selectedGame.gameId);
        
        String opponent = selectedGame.getOpponentName(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("§aOpening analysis for match " + matchNumber + " vs " + opponent + "..."), false);
        return 1;
    }

    private static int getHint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game to get a hint for!"));
            return 0;
        }

        if (!game.isGameActive() && !game.isAnalysisMode()) {
            context.getSource().sendFailure(Component.literal("§cThe game is over. Use /chess analyze to review."));
            return 0;
        }

        if (!game.isPlayerTurn(player) && !game.isAnalysisMode()) {
            context.getSource().sendFailure(Component.literal("§cIt's not your turn to get a hint."));
            return 0;
        }


        StockfishWebIntegration.getInstance().requestHint(game.getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
        });

        context.getSource().sendSuccess(() -> Component.literal("§bRequesting hint..."), false);
        return 1;
    }

    private static int openChessBoard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game != null) {
            ChessGUI gui = GameManager.getInstance().getPlayerGUI(player);
            if (gui != null) {
                if (!gui.isOpen()) gui.open();
                gui.updateBoard(); // Ensure it's up-to-date
            } else { // Should not happen if in game
                context.getSource().sendFailure(Component.literal("§cError: In game but no GUI found. Re-opening."));
                GameManager.getInstance().createGame(game.getWhitePlayer(), game.getBlackPlayer(), game.getTimeControl()); // Re-create to fix
            }
        } else {
            GameManager.getInstance().openPracticeBoard(player);
        }
        return 1;
    }

    private static int showFEN(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        String fen;
        if (game != null) {
            fen = game.getBoard().toFEN();
        } else {
            // If not in a game, maybe show FEN of a practice board if one is open?
            // For now, just require being in a game.
            context.getSource().sendFailure(Component.literal("§cYou are not in a game. Open a practice board or start a game to see FEN."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("§7Current FEN: §f" + fen), false);
        return 1;
    }

    private static int loadFromFEN(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fen = StringArgumentType.getString(context, "fen");

        if (GameManager.getInstance().isPlayerBusy(player)) {
            context.getSource().sendFailure(Component.literal("§cYou are busy. Finish your game/challenge before loading FEN."));
            return 0;
        }

        try {
            GameManager.getInstance().openPracticeBoardWithFEN(player, fen);
            context.getSource().sendSuccess(() -> Component.literal("§aLoaded position from FEN into practice board."), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid FEN string: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError loading FEN: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int reloadData(CommandContext<CommandSourceStack> context) {
        // GameManager.getInstance().loadPlayerData(); // Already saved periodically and on shutdown/load
        GameManager.getInstance().markDataDirty(); // Force a save
        context.getSource().sendSuccess(() -> Component.literal("§aPlayer data saved. It loads automatically on server start."), false);
        return 1;
    }

    private static int backupData(CommandContext<CommandSourceStack> context) {
        // Actual backup logic would involve copying files, possibly to a timestamped folder
        context.getSource().sendSuccess(() -> Component.literal("§aManual backup feature not fully implemented. Data is saved to chess_data regularly."), false);
        return 1;
    }

    private static int setPlayerELO(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String eloStr = StringArgumentType.getString(context, "elo");

        try {
            int newELO = Integer.parseInt(eloStr);
            if (newELO < 0 || newELO > 4000) {
                context.getSource().sendFailure(Component.literal("§cELO must be between 0 and 4000"));
                return 0;
            }

            PlayerData data = GameManager.getInstance().getPlayerData(target);
            data.elo = newELO;
            GameManager.getInstance().markDataDirty(); // Save immediately after admin change

            context.getSource().sendSuccess(() -> Component.literal(
                    "§aSet " + target.getName().getString() + "'s ELO to " + newELO
            ), true);

            target.sendSystemMessage(Component.literal("§6Your ELO has been set to " + newELO + " by an admin."));
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid ELO number: " + eloStr));
            return 0;
        }
    }

    private static int adminUnbusyPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        GameManager gm = GameManager.getInstance();

        boolean wasBusyGame = gm.getPlayerGame(target) != null;
        boolean hadPendingChallenges = gm.getPendingChallenges().values().stream()
                .anyMatch(c -> c.challenger.equals(target) || c.challenged.equals(target));

        gm.adminForceEndGameForPlayer(target);
        gm.adminRemoveChallengesForPlayer(target);

        if (wasBusyGame || hadPendingChallenges) {
            context.getSource().sendSuccess(() -> Component.literal("§a" + target.getName().getString() + " is no longer busy with chess activities."), true);
            target.sendSystemMessage(Component.literal("§eAn admin has cleared your chess activity status."));
        } else {
            context.getSource().sendSuccess(() -> Component.literal("§7" + target.getName().getString() + " was not busy with chess activities."), false);
        }
        return 1;
    }
    
    private static int playBot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int elo = IntegerArgumentType.getInteger(context, "elo");
        
        GameManager gameManager = GameManager.getInstance();
        if (gameManager.isPlayerBusy(player)) {
            context.getSource().sendFailure(Component.literal("§cYou are already in a game or have a pending challenge!"));
            return 0;
        }
        
        // Random color
        PieceColor playerColor = Math.random() < 0.5 ? PieceColor.WHITE : PieceColor.BLACK;
        
        gameManager.createBotGame(player, playerColor, TimeControl.RAPID_10_0, elo, 3); // 3 hints by default
        
        return 1;
    }
    
    private static int playBotDefaultElo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        
        GameManager gameManager = GameManager.getInstance();
        if (gameManager.isPlayerBusy(player)) {
            context.getSource().sendFailure(Component.literal("§cYou are already in a game or have a pending challenge!"));
            return 0;
        }
        
        // Use player's ELO for bot difficulty
        PlayerData playerData = gameManager.getPlayerData(player);
        int botElo = Math.max(500, Math.min(3000, playerData.elo));
        
        // Random color
        PieceColor playerColor = Math.random() < 0.5 ? PieceColor.WHITE : PieceColor.BLACK;
        
        gameManager.createBotGame(player, playerColor, TimeControl.RAPID_10_0, botElo, 3);
        
        return 1;
    }
    
    private static int openDebugSimpleGUI(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DebugSimpleGUI debugGUI = new DebugSimpleGUI(player);
        debugGUI.open();
        return 1;
    }
    
    private static int openDebugDoubleGUI(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DebugDoubleGUI debugGUI = new DebugDoubleGUI(player);
        debugGUI.open();
        return 1;
    }
}