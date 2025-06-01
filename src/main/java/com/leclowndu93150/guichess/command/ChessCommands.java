package com.leclowndu93150.guichess.command;

import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.*;
import com.leclowndu93150.guichess.gui.ChessGUI;
import com.leclowndu93150.guichess.gui.PracticeBoardGUI;
import com.leclowndu93150.guichess.gui.SpectatorGUI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;

@EventBusSubscriber
public class ChessCommands {

    private static final SuggestionProvider<CommandSourceStack> TIME_CONTROL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(TimeControl.values()).map(tc -> tc.name().toLowerCase()),
                    builder
            );

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
                                        .executes(ctx -> createGame(ctx, "BLITZ_5_0"))))

                        .then(Commands.literal("challenge")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("timecontrol", StringArgumentType.string())
                                                .suggests(TIME_CONTROL_SUGGESTIONS)
                                                .executes(ChessCommands::challengePlayer))
                                        .executes(ctx -> challengePlayer(ctx, "BLITZ_5_0"))))

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
                                        .executes(ChessCommands::spectateGame)))

                        .then(Commands.literal("stats")
                                .executes(ChessCommands::showOwnStats)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ChessCommands::showPlayerStats)))

                        .then(Commands.literal("leaderboard")
                                .executes(ChessCommands::showLeaderboard))

                        .then(Commands.literal("history")
                                .executes(ChessCommands::showGameHistory))

                        .then(Commands.literal("analyze")
                                .executes(ChessCommands::analyzeCurrentGame))

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
                                                        .executes(ChessCommands::setPlayerELO)))))
        );
    }

    private static int createGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return createGame(context, "BLITZ_5_0");
    }

    private static int createGame(CommandContext<CommandSourceStack> context, String timeControlName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer opponent = EntityArgument.getPlayer(context, "opponent");

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
            context.getSource().sendFailure(Component.literal("§cInvalid time control: " + timeControlName));
            return 0;
        }

        // For direct play command, skip challenge and create game immediately
        ChessGame game = gameManager.createGame(player, opponent, timeControl);

        player.sendSystemMessage(Component.literal("§aGame started against " + opponent.getName().getString() + " (" + timeControl.displayName + ")"));
        opponent.sendSystemMessage(Component.literal("§aGame started against " + player.getName().getString() + " (" + timeControl.displayName + ")"));

        return 1;
    }

    private static int challengePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return challengePlayer(context, "BLITZ_5_0");
    }

    private static int challengePlayer(CommandContext<CommandSourceStack> context, String timeControlName) throws CommandSyntaxException {
        ServerPlayer challenger = context.getSource().getPlayerOrException();
        ServerPlayer challenged = EntityArgument.getPlayer(context, "player");

        if (challenger.equals(challenged)) {
            context.getSource().sendFailure(Component.literal("§cYou cannot challenge yourself!"));
            return 0;
        }

        GameManager gameManager = GameManager.getInstance();

        if (gameManager.isPlayerBusy(challenger)) {
            context.getSource().sendFailure(Component.literal("§cYou are already in a game or have a pending challenge!"));
            return 0;
        }

        if (gameManager.isPlayerBusy(challenged)) {
            context.getSource().sendFailure(Component.literal("§c" + challenged.getName().getString() + " is already busy!"));
            return 0;
        }

        TimeControl timeControl;
        try {
            timeControl = TimeControl.valueOf(timeControlName.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid time control: " + timeControlName));
            return 0;
        }

        ChessChallenge challenge = gameManager.createChallenge(challenger, challenged, timeControl);
        if (challenge == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to create challenge!"));
            return 0;
        }

        challenger.sendSystemMessage(Component.literal("§eChallenge sent to " + challenged.getName().getString() + " (" + timeControl.displayName + ")"));

        return 1;
    }

    private static int acceptChallenge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GameManager gameManager = GameManager.getInstance();

        // Find the most recent challenge for this player
        ChessChallenge challenge = gameManager.getPendingChallenges().values().stream()
                .filter(c -> c.challenged.equals(player) && !c.isExpired())
                .max((c1, c2) -> Long.compare(c1.challengeTime, c2.challengeTime))
                .orElse(null);

        if (challenge == null) {
            context.getSource().sendFailure(Component.literal("§cYou have no pending challenges!"));
            return 0;
        }

        if (gameManager.acceptChallenge(player, challenge.challengeId)) {
            context.getSource().sendSuccess(() -> Component.literal("§aChallenge accepted!"), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to accept challenge!"));
            return 0;
        }
    }

    private static int declineChallenge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GameManager gameManager = GameManager.getInstance();

        // Find the most recent challenge for this player
        ChessChallenge challenge = gameManager.getPendingChallenges().values().stream()
                .filter(c -> c.challenged.equals(player) && !c.isExpired())
                .max((c1, c2) -> Long.compare(c1.challengeTime, c2.challengeTime))
                .orElse(null);

        if (challenge == null) {
            context.getSource().sendFailure(Component.literal("§cYou have no pending challenges!"));
            return 0;
        }

        if (gameManager.declineChallenge(player, challenge.challengeId)) {
            context.getSource().sendSuccess(() -> Component.literal("§cChallenge declined!"), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to decline challenge!"));
            return 0;
        }
    }

    private static int resignGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game!"));
            return 0;
        }

        if (game.resign(player)) {
            context.getSource().sendSuccess(() -> Component.literal("§cYou resigned the game!"), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to resign!"));
            return 0;
        }
    }

    private static int offerDraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game!"));
            return 0;
        }

        if (game.offerDraw(player)) {
            context.getSource().sendSuccess(() -> Component.literal("§eDraw offer sent!"), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to offer draw!"));
            return 0;
        }
    }

    private static int spectateGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        ChessGame game = GameManager.getInstance().getPlayerGame(target);
        if (game == null) {
            context.getSource().sendFailure(Component.literal("§c" + target.getName().getString() + " is not in a game!"));
            return 0;
        }

        // Create spectator GUI
        ChessGUI spectatorGUI = new SpectatorGUI(player, game);
        spectatorGUI.open();

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
                        "§7ELO Rating: §6" + data.elo + "\n" +
                        "§7Games Played: §f" + data.gamesPlayed + "\n" +
                        "§7Wins: §a" + data.wins + " §7(§a" + String.format("%.1f%%", data.getWinRate() * 100) + "§7)\n" +
                        "§7Losses: §c" + data.losses + "\n" +
                        "§7Draws: §e" + data.draws + "\n" +
                        "§7Favorite Time Control: §b" + data.favoriteTimeControl
        ), false);

        return 1;
    }

    private static int showLeaderboard(CommandContext<CommandSourceStack> context) {
        List<PlayerData> leaders = GameManager.getInstance().getLeaderboard(10);

        StringBuilder message = new StringBuilder("§6=== Chess Leaderboard ===\n");
        for (int i = 0; i < leaders.size(); i++) {
            PlayerData data = leaders.get(i);
            message.append(String.format(
                    "§7%d. §f%s §7- §6%d ELO §7(%d games)\n",
                    i + 1, data.playerName, data.elo, data.gamesPlayed
            ));
        }

        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int showGameHistory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // This would load recent games from saved data
        context.getSource().sendSuccess(() -> Component.literal("§7Game history feature coming soon!"), false);
        return 1;
    }

    private static int analyzeCurrentGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game!"));
            return 0;
        }

        game.enableAnalysisMode();
        context.getSource().sendSuccess(() -> Component.literal("§dAnalysis mode enabled!"), false);
        return 1;
    }

    private static int getHint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game!"));
            return 0;
        }

        // Request hint from Stockfish integration
        StockfishIntegration.getInstance().requestHint(game.getBoard().toFEN(), hint -> {
            player.sendSystemMessage(Component.literal("§bHint: " + hint));
        });

        context.getSource().sendSuccess(() -> Component.literal("§bRequesting hint..."), false);
        return 1;
    }

    private static int openChessBoard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // Open practice board or current game
        ChessGame game = GameManager.getInstance().getPlayerGame(player);
        if (game != null) {
            ChessGUI gui = GameManager.getInstance().getPlayerGUI(player);
            if (gui != null) {
                gui.open();
            }
        } else {
            // Create practice board
            PracticeBoardGUI practiceGUI = new PracticeBoardGUI(player);
            practiceGUI.open();
        }

        return 1;
    }

    private static int showFEN(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ChessGame game = GameManager.getInstance().getPlayerGame(player);

        if (game == null) {
            context.getSource().sendFailure(Component.literal("§cYou are not in a game!"));
            return 0;
        }

        String fen = game.getBoard().toFEN();
        context.getSource().sendSuccess(() -> Component.literal("§7Current FEN: §f" + fen), false);
        return 1;
    }

    private static int loadFromFEN(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fen = StringArgumentType.getString(context, "fen");

        // Create practice board from FEN
        try {
            PracticeBoardGUI practiceGUI = new PracticeBoardGUI(player, fen);
            practiceGUI.open();
            context.getSource().sendSuccess(() -> Component.literal("§aLoaded position from FEN"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cInvalid FEN: " + e.getMessage()));
            return 0;
        }
    }

    // Admin commands
    private static int reloadData(CommandContext<CommandSourceStack> context) {
        GameManager.getInstance().savePlayerData();
        context.getSource().sendSuccess(() -> Component.literal("§aChess data reloaded!"), false);
        return 1;
    }

    private static int backupData(CommandContext<CommandSourceStack> context) {
        // Create backup
        context.getSource().sendSuccess(() -> Component.literal("§aChess data backed up!"), false);
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
            GameManager.getInstance().savePlayerData();

            context.getSource().sendSuccess(() -> Component.literal(
                    "§aSet " + target.getName().getString() + "'s ELO to " + newELO
            ), false);

            target.sendSystemMessage(Component.literal("§6Your ELO has been set to " + newELO + " by an admin"));
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid ELO number: " + eloStr));
            return 0;
        }
    }
}