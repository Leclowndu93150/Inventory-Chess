package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.gui.ChessGUI;
import com.leclowndu93150.guichess.gui.SpectatorGUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GameManager {
    private static GameManager instance;
    private ScheduledExecutorService scheduler;

    private final Map<UUID, ChessGame> activeGames = new ConcurrentHashMap<>();
    private final Map<UUID, ChessChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, ChessGUI> playerGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, List<SpectatorGUI>> spectatorGUIs = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private Path dataDirectory;

    private GameManager() {
        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        this.dataDirectory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("chess_data");

        if (this.scheduler == null || this.scheduler.isShutdown() || this.scheduler.isTerminated()) {
            System.out.println("[GUIChess] GameManager scheduler was shutdown or null. Re-creating for initialization.");
            this.scheduler = Executors.newScheduledThreadPool(4);
        }

        try {
            Files.createDirectories(dataDirectory);
            loadPlayerData();
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to create/load chess data directory: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            scheduler.scheduleAtFixedRate(this::tickAllGames, 0, 1, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::cleanupExpiredChallenges, 30, 30, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::savePlayerData, 300, 300, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::reopenClosedGameGUIs, 2, 2, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            System.err.println("[GUIChess] Failed to schedule GameManager tasks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void reopenClosedGameGUIs() {
        try {
            for (ChessGame game : activeGames.values()) {
                if (!game.isGameActive()) continue;

                ChessGUI whiteGUI = playerGUIs.get(game.getWhitePlayer().getUUID());
                ChessGUI blackGUI = playerGUIs.get(game.getBlackPlayer().getUUID());

                if (whiteGUI != null && !whiteGUI.isOpen()) {
                    whiteGUI.open();
                }

                if (blackGUI != null && !blackGUI.isOpen()) {
                    blackGUI.open();
                }
            }
        } catch (Exception e) {
            System.err.println("[GUIChess] Error during GUI reopen check: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            System.out.println("[GUIChess] Shutting down GameManager scheduler...");
            this.scheduler.shutdown();
            try {
                if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.scheduler.shutdownNow();
                    if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("[GUIChess] GameManager scheduler did not terminate.");
                    }
                }
            } catch (InterruptedException e) {
                this.scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                System.err.println("[GUIChess] GameManager scheduler shutdown interrupted.");
            }
            System.out.println("[GUIChess] GameManager scheduler shutdown complete.");
        } else {
            System.out.println("[GUIChess] GameManager scheduler already shutdown or null.");
        }

        savePlayerData();
        saveAllGameData();
    }

    public ChessGame createGame(ServerPlayer whitePlayer, ServerPlayer blackPlayer, TimeControl timeControl) {
        ChessGame game = new ChessGame(whitePlayer, blackPlayer, timeControl);
        activeGames.put(game.getGameId(), game);

        ChessGUI whiteGUI = new ChessGUI(whitePlayer, game, PieceColor.WHITE);
        ChessGUI blackGUI = new ChessGUI(blackPlayer, game, PieceColor.BLACK);

        playerGUIs.put(whitePlayer.getUUID(), whiteGUI);
        playerGUIs.put(blackPlayer.getUUID(), blackGUI);

        spectatorGUIs.put(game.getGameId(), new ArrayList<>());

        whiteGUI.open();
        blackGUI.open();

        return game;
    }

    public void addSpectator(ChessGame game, ServerPlayer spectator) {
        if (game == null || spectator == null) return;
        if (game.getWhitePlayer().equals(spectator) || game.getBlackPlayer().equals(spectator)) {
            spectator.sendSystemMessage(Component.literal("§cYou cannot spectate your own game."));
            return;
        }

        SpectatorGUI specGUI = new SpectatorGUI(spectator, game);
        List<SpectatorGUI> guis = spectatorGUIs.computeIfAbsent(game.getGameId(), k -> new ArrayList<>());
        guis.add(specGUI);
        specGUI.open();
    }

    public void removeSpectator(ChessGame game, ServerPlayer spectator) {
        if (game == null || spectator == null) return;
        List<SpectatorGUI> guis = spectatorGUIs.get(game.getGameId());
        if (guis != null) {
            guis.removeIf(gui -> gui.getPlayer().equals(spectator));
        }
    }

    public void updateSpectatorGUIs(ChessGame game) {
        if (game == null) return;
        List<SpectatorGUI> guis = spectatorGUIs.get(game.getGameId());
        if (guis != null) {
            guis.forEach(gui -> {
                if (gui.isOpen()) {
                    gui.updateBoard();
                }
            });
        }
    }

    public void endGame(UUID gameId) {
        ChessGame game = activeGames.remove(gameId);
        if (game != null) {
            ChessGUI whiteGui = playerGUIs.remove(game.getWhitePlayer().getUUID());
            ChessGUI blackGui = playerGUIs.remove(game.getBlackPlayer().getUUID());

            if (whiteGui != null && whiteGui.isOpen()) whiteGui.close();
            if (blackGui != null && blackGui.isOpen()) blackGui.close();

            List<SpectatorGUI> specGuis = spectatorGUIs.remove(game.getGameId());
            if (specGuis != null) {
                specGuis.forEach(gui -> {
                    if (gui.isOpen()) gui.close();
                });
            }
        }
    }

    public ChessChallenge createChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        if (isPlayerBusy(challenger) || isPlayerBusy(challenged)) {
            return null;
        }

        ChessChallenge challenge = new ChessChallenge(challenger, challenged, timeControl);
        pendingChallenges.put(challenge.challengeId, challenge);

        challenged.sendSystemMessage(Component.literal(
                "§e" + challenger.getName().getString() + " challenges you to a " +
                        timeControl.displayName + " game! Use /chess accept or /chess decline"
        ));
        return challenge;
    }

    public boolean acceptChallenge(ServerPlayer player, UUID challengeId) {
        ChessChallenge challenge = pendingChallenges.get(challengeId);
        if (challenge == null || !challenge.challenged.equals(player) || challenge.isExpired()) {
            if (challenge != null && challenge.isExpired()) pendingChallenges.remove(challengeId);
            return false;
        }

        if (isPlayerBusyExcluding(challenge.challenger, challengeId) || isPlayerBusyExcluding(challenge.challenged, challengeId)) {
            player.sendSystemMessage(Component.literal("§cOne of the players became busy. Cannot start game."));
            if (!challenge.challenger.equals(player)) {
                challenge.challenger.sendSystemMessage(Component.literal("§cCould not start game with " + player.getName().getString() + " as one of you became busy."));
            }
            pendingChallenges.remove(challengeId);
            return false;
        }

        pendingChallenges.remove(challengeId);
        createGame(challenge.challenger, challenge.challenged, challenge.timeControl);

        challenge.challenger.sendSystemMessage(Component.literal(
                "§a" + player.getName().getString() + " accepted your challenge!"
        ));
        player.sendSystemMessage(Component.literal("§aChallenge accepted!"));

        return true;
    }

    private boolean isPlayerBusyExcluding(ServerPlayer player, UUID excludeChallenge) {
        if (player == null) return true;
        if (getPlayerGame(player) != null) return true;
        return pendingChallenges.values().stream()
                .anyMatch(c -> !c.isExpired() &&
                        !c.challengeId.equals(excludeChallenge) &&
                        (c.challenger.equals(player) || c.challenged.equals(player)));
    }

    public boolean declineChallenge(ServerPlayer player, UUID challengeId) {
        ChessChallenge challenge = pendingChallenges.remove(challengeId);
        if (challenge == null || !challenge.challenged.equals(player)) {
            return false;
        }

        challenge.challenger.sendSystemMessage(Component.literal(
                "§c" + player.getName().getString() + " declined your challenge."
        ));
        player.sendSystemMessage(Component.literal("§cChallenge declined."));
        return true;
    }

    public PlayerData getPlayerData(ServerPlayer player) {
        return playerDataMap.computeIfAbsent(player.getUUID(),
                k -> new PlayerData(player.getUUID(), player.getName().getString()));
    }

    public List<PlayerData> getLeaderboard(int limit) {
        return playerDataMap.values().stream()
                .filter(p -> p.gamesPlayed >= 1)
                .sorted(Comparator.comparingInt(PlayerData::getElo).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public ChessGUI getPlayerGUI(ServerPlayer player) {
        if (player == null) return null;
        return playerGUIs.get(player.getUUID());
    }

    public ChessGame getPlayerGame(ServerPlayer player) {
        if (player == null) return null;
        return activeGames.values().stream()
                .filter(ChessGame::isGameActive)
                .filter(game -> (game.getWhitePlayer() != null && game.getWhitePlayer().equals(player)) ||
                        (game.getBlackPlayer() != null && game.getBlackPlayer().equals(player)))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayerBusy(ServerPlayer player) {
        if (player == null) return true;
        if (getPlayerGame(player) != null) return true;
        return pendingChallenges.values().stream()
                .anyMatch(c -> !c.isExpired() && (c.challenger.equals(player) || c.challenged.equals(player)));
    }

    private void tickAllGames() {
        try {
            activeGames.values().forEach(ChessGame::tickTimer);
        } catch (Exception e) {
            System.err.println("[GUIChess] Error during game ticking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupExpiredChallenges() {
        pendingChallenges.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                entry.getValue().challenger.sendSystemMessage(Component.literal(
                        "§7Your challenge to " + entry.getValue().challenged.getName().getString() + " has expired."
                ));
                entry.getValue().challenged.sendSystemMessage(Component.literal(
                        "§7The challenge from " + entry.getValue().challenger.getName().getString() + " has expired."
                ));
                return true;
            }
            return false;
        });
    }

    public void scheduleGameCleanup(ChessGame game, int delaySeconds) {
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.schedule(() -> endGame(game.getGameId()), delaySeconds, TimeUnit.SECONDS);
            } catch (RejectedExecutionException e) {
                System.err.println("[GUIChess] Could not schedule game cleanup for " + game.getGameId() + " (scheduler rejected): " + e.getMessage());
                endGame(game.getGameId());
            }
        } else {
            System.err.println("[GUIChess] Could not schedule game cleanup for " + game.getGameId() + " as scheduler is shutdown/null. Cleaning up immediately.");
            endGame(game.getGameId());
        }
    }

    public void savePlayerData() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag playersNBT = new ListTag();

            for (PlayerData data : playerDataMap.values()) {
                playersNBT.add(data.toNBT());
            }

            root.put("players", playersNBT);

            Path file = dataDirectory.resolve("player_data.nbt");
            try (FileOutputStream fos = new FileOutputStream(file.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                net.minecraft.nbt.NbtIo.writeCompressed(root, bos);
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
            try (FileInputStream fis = new FileInputStream(file.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                root = net.minecraft.nbt.NbtIo.readCompressed(bis, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            if (root.contains("players", ListTag.TAG_COMPOUND)) {
                ListTag playersNBT = root.getList("players", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < playersNBT.size(); i++) {
                    PlayerData data = PlayerData.fromNBT(playersNBT.getCompound(i));
                    playerDataMap.put(data.playerId, data);
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

            try (FileOutputStream fos = new FileOutputStream(file.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                net.minecraft.nbt.NbtIo.writeCompressed(gameData, bos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAllGameData() {
    }

    public void adminForceEndGameForPlayer(ServerPlayer player) {
        ChessGame game = getPlayerGame(player);
        if (game != null) {
            ServerPlayer opponent = game.getOpponent(player);
            game.endGame(GameState.ABANDONED);

            player.sendSystemMessage(Component.literal("§eAn admin has ended your current game."));
            if (opponent != null) {
                opponent.sendSystemMessage(Component.literal("§eAn admin has ended your game against " + player.getName().getString() + "."));
            }
        }
    }

    public void adminRemoveChallengesForPlayer(ServerPlayer player) {
        List<UUID> challengesToRemove = new ArrayList<>();
        for (ChessChallenge challenge : pendingChallenges.values()) {
            if (challenge.challenger.equals(player) || challenge.challenged.equals(player)) {
                challengesToRemove.add(challenge.challengeId);
                if (challenge.challenger.equals(player) && challenge.challenged != null) {
                    challenge.challenged.sendSystemMessage(Component.literal("§eA challenge from " + player.getName().getString() + " was cancelled by an admin."));
                } else if (challenge.challenged.equals(player) && challenge.challenger != null) {
                    challenge.challenger.sendSystemMessage(Component.literal("§eYour challenge to " + player.getName().getString() + " was cancelled by an admin."));
                }
            }
        }
        challengesToRemove.forEach(pendingChallenges::remove);
        if (!challengesToRemove.isEmpty()) {
            player.sendSystemMessage(Component.literal("§eYour pending chess challenges have been cleared by an admin."));
        }
    }

    public Map<UUID, ChessGame> getActiveGames() { return activeGames; }
    public Map<UUID, ChessChallenge> getPendingChallenges() { return pendingChallenges; }
}