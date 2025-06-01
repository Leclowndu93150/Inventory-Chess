package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.gui.ChessGUI;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameManager {
    private static GameManager instance;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Game management
    private final Map<UUID, ChessGame> activeGames = new ConcurrentHashMap<>();
    private final Map<UUID, ChessChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, ChessGUI> playerGUIs = new ConcurrentHashMap<>();

    // Server reference
    private MinecraftServer server;
    private Path dataDirectory;

    private GameManager() {}

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.dataDirectory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("chess_data");

        try {
            Files.createDirectories(dataDirectory);
            loadPlayerData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start periodic tasks
        scheduler.scheduleAtFixedRate(this::tickAllGames, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredChallenges, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::savePlayerData, 300, 300, TimeUnit.SECONDS); // Save every 5 minutes
    }

    public void shutdown() {
        scheduler.shutdown();
        savePlayerData();
        saveAllGameData();
    }

    // Game creation and management
    public ChessGame createGame(ServerPlayer whitePlayer, ServerPlayer blackPlayer, TimeControl timeControl) {
        ChessGame game = new ChessGame(whitePlayer, blackPlayer, timeControl);
        activeGames.put(game.getGameId(), game);

        // Create GUIs for both players
        ChessGUI whiteGUI = new ChessGUI(whitePlayer, game, PieceColor.WHITE);
        ChessGUI blackGUI = new ChessGUI(blackPlayer, game, PieceColor.BLACK);

        playerGUIs.put(whitePlayer.getUUID(), whiteGUI);
        playerGUIs.put(blackPlayer.getUUID(), blackGUI);

        // Open GUIs
        whiteGUI.open();
        blackGUI.open();

        return game;
    }

    public void endGame(UUID gameId) {
        ChessGame game = activeGames.remove(gameId);
        if (game != null) {
            playerGUIs.remove(game.getWhitePlayer().getUUID());
            playerGUIs.remove(game.getBlackPlayer().getUUID());
        }
    }

    public ChessChallenge createChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        // Check if players are already in a game or have pending challenges
        if (isPlayerBusy(challenger) || isPlayerBusy(challenged)) {
            return null;
        }

        ChessChallenge challenge = new ChessChallenge(challenger, challenged, timeControl);
        pendingChallenges.put(challenge.challengeId, challenge);

        // Notify challenged player
        challenged.sendSystemMessage(Component.literal(
                "§e" + challenger.getName().getString() + " challenges you to a " +
                        timeControl.displayName + " game! Use /chess accept or /chess decline"
        ));

        return challenge;
    }

    public boolean acceptChallenge(ServerPlayer player, UUID challengeId) {
        ChessChallenge challenge = pendingChallenges.remove(challengeId);
        if (challenge == null || !challenge.challenged.equals(player) || challenge.isExpired()) {
            return false;
        }

        // Create the game
        createGame(challenge.challenger, challenge.challenged, challenge.timeControl);

        challenge.challenger.sendSystemMessage(Component.literal(
                "§a" + player.getName().getString() + " accepted your challenge!"
        ));

        return true;
    }

    public boolean declineChallenge(ServerPlayer player, UUID challengeId) {
        ChessChallenge challenge = pendingChallenges.remove(challengeId);
        if (challenge == null || !challenge.challenged.equals(player)) {
            return false;
        }

        challenge.challenger.sendSystemMessage(Component.literal(
                "§c" + player.getName().getString() + " declined your challenge."
        ));

        return true;
    }

    // Player data management
    public PlayerData getPlayerData(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(),
                k -> new PlayerData(player.getUUID(), player.getName().getString()));
    }

    public List<PlayerData> getLeaderboard(int limit) {
        return playerData.values().stream()
                .filter(p -> p.gamesPlayed >= 5) // Minimum games for leaderboard
                .sorted((a, b) -> Integer.compare(b.elo, a.elo))
                .limit(limit)
                .toList();
    }

    // GUI management
    public ChessGUI getPlayerGUI(ServerPlayer player) {
        return playerGUIs.get(player.getUUID());
    }

    public ChessGame getPlayerGame(ServerPlayer player) {
        return activeGames.values().stream()
                .filter(game -> game.getWhitePlayer().equals(player) || game.getBlackPlayer().equals(player))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayerBusy(ServerPlayer player) {
        return getPlayerGame(player) != null ||
                pendingChallenges.values().stream()
                        .anyMatch(c -> c.challenger.equals(player) || c.challenged.equals(player));
    }

    // Periodic tasks
    private void tickAllGames() {
        activeGames.values().forEach(ChessGame::tickTimer);
    }

    private void cleanupExpiredChallenges() {
        pendingChallenges.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                entry.getValue().challenger.sendSystemMessage(Component.literal(
                        "§7Your challenge to " + entry.getValue().challenged.getName().getString() + " has expired."
                ));
                return true;
            }
            return false;
        });
    }

    public void scheduleGameTimer(ChessGame game) {
        scheduler.scheduleAtFixedRate(game::tickTimer, 1, 1, TimeUnit.SECONDS);
    }

    public void scheduleGameCleanup(ChessGame game, int delaySeconds) {
        scheduler.schedule(() -> endGame(game.getGameId()), delaySeconds, TimeUnit.SECONDS);
    }

    // Data persistence
    public void savePlayerData() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag players = new ListTag();

            for (PlayerData data : playerData.values()) {
                players.add(data.toNBT());
            }

            root.put("players", players);

            Path file = dataDirectory.resolve("player_data.nbt");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                net.minecraft.nbt.NbtIo.writeCompressed(root, fos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPlayerData() {
        try {
            Path file = dataDirectory.resolve("player_data.nbt");
            if (!Files.exists(file)) return;

            CompoundTag root;
            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                root = net.minecraft.nbt.NbtIo.readCompressed(fis, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            if (root.contains("players")) {
                ListTag players = root.getList("players", 10);
                for (int i = 0; i < players.size(); i++) {
                    PlayerData data = PlayerData.fromNBT(players.getCompound(i));
                    playerData.put(data.playerId, data);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveGameHistory(CompoundTag gameData) {
        try {
            Path historyDir = dataDirectory.resolve("game_history");
            Files.createDirectories(historyDir);

            String fileName = "game_" + gameData.getString("gameId") + ".nbt";
            Path file = historyDir.resolve(fileName);

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                net.minecraft.nbt.NbtIo.writeCompressed(gameData, fos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAllGameData() {
        // Save any ongoing games for recovery
        try {
            CompoundTag root = new CompoundTag();
            ListTag games = new ListTag();

            for (ChessGame game : activeGames.values()) {
                CompoundTag gameTag = new CompoundTag();
                gameTag.putString("gameId", game.getGameId().toString());
                gameTag.putString("whitePlayer", game.getWhitePlayer().getUUID().toString());
                gameTag.putString("blackPlayer", game.getBlackPlayer().getUUID().toString());
                gameTag.putString("fen", game.getBoard().toFEN());
                gameTag.putInt("whiteTime", game.getWhiteTimeLeft());
                gameTag.putInt("blackTime", game.getBlackTimeLeft());
                gameTag.putString("timeControl", game.getTimeControl().name());
                games.add(gameTag);
            }

            root.put("games", games);

            Path file = dataDirectory.resolve("active_games.nbt");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                net.minecraft.nbt.NbtIo.writeCompressed(root, fos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getters
    public Map<UUID, ChessGame> getActiveGames() { return activeGames; }
    public Map<UUID, ChessChallenge> getPendingChallenges() { return pendingChallenges; }
}