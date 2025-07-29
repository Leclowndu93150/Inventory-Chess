package com.leclowndu93150.guichess.game.core;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.game.challenge.ChessChallenge;
import com.leclowndu93150.guichess.game.players.BotPlayer;
import com.leclowndu93150.guichess.game.players.GameParticipant;
import com.leclowndu93150.guichess.game.players.HumanPlayer;
import com.leclowndu93150.guichess.util.time.TimeControl;
import com.leclowndu93150.guichess.data.storage.ChessBotDataStorage;
import com.leclowndu93150.guichess.data.storage.ChessMatchHistoryStorage;
import com.leclowndu93150.guichess.data.storage.ChessPlayerDataStorage;
import com.leclowndu93150.guichess.data.models.GameHistory;
import com.leclowndu93150.guichess.data.storage.MatchHistoryManager;
import com.leclowndu93150.guichess.data.models.PlayerData;
import com.leclowndu93150.guichess.data.models.BotProfile;
import com.leclowndu93150.guichess.gui.game.ChessGUI;
import com.leclowndu93150.guichess.gui.analysis.MatchAnalysisGUI;
import com.leclowndu93150.guichess.gui.analysis.PracticeBoardGUI;
import com.leclowndu93150.guichess.gui.game.SpectatorGUI;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

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

/**
 * Manages chess games, challenges, and player data.
 */
public class GameManager {
    private static GameManager instance;
    private ScheduledExecutorService scheduler;

    private final Map<UUID, ChessGame> activeGames = new ConcurrentHashMap<>();
    private final Map<UUID, ChessChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private ChessPlayerDataStorage playerDataStorage;
    private ChessBotDataStorage botDataStorage;
    private ChessMatchHistoryStorage matchHistoryStorage;
    private final Map<UUID, ChessGUI> playerGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, List<SpectatorGUI>> spectatorGUIs = new ConcurrentHashMap<>();

    private final Map<UUID, Set<Integer>> timeWarningsSent = new ConcurrentHashMap<>();
    
    private final Map<UUID, CompoundTag> savedInventories = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private Path dataDirectory;
    private MatchHistoryManager matchHistoryManager;

    private GameManager() {
        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    /**
     * Returns the singleton GameManager instance.
     * 
     * @return the singleton GameManager instance
     */
    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /**
     * Initializes the GameManager and starts all background services.
     * 
     * <p>Sets up data storage, scheduler, and periodic tasks for game management.
     * Should be called once during server startup.
     * 
     * @param server the MinecraftServer instance
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
        this.dataDirectory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("chess_data");

        if (this.scheduler == null || this.scheduler.isShutdown() || this.scheduler.isTerminated()) {
            System.out.println("[GUIChess] GameManager scheduler was shutdown or null. Re-creating for initialization.");
            this.scheduler = Executors.newScheduledThreadPool(4);
        }

        try {
            Files.createDirectories(dataDirectory);
            initializeSavedData();
            matchHistoryManager = new MatchHistoryManager(dataDirectory, matchHistoryStorage);
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to create/load chess data directory: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            scheduler.scheduleAtFixedRate(this::tickAllGames, 0, 1, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::cleanupExpiredChallenges, 30, 30, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::markDataDirty, 300, 300, TimeUnit.SECONDS);
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

                ServerPlayer whitePlayer = game.getWhitePlayer();
                if (whitePlayer != null) {
                    ChessGUI whiteGUI = playerGUIs.get(whitePlayer.getUUID());
                    if (whiteGUI != null && !whiteGUI.isOpen() && whiteGUI.getAutoReopen()) {
                        whiteGUI.open();
                    }
                }

                ServerPlayer blackPlayer = game.getBlackPlayer();
                if (blackPlayer != null) {
                    ChessGUI blackGUI = playerGUIs.get(blackPlayer.getUUID());
                    if (blackGUI != null && !blackGUI.isOpen() && blackGUI.getAutoReopen()) {
                        blackGUI.open();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GUIChess] Error during GUI reopen check: " + e.getMessage());
        }
    }

    /**
     * Shuts down GameManager on server stop.
     * Restores player inventories, stops scheduler, saves data.
     */
    public void shutdown() {
        // Restore all saved inventories before shutdown to prevent item loss
        if (!savedInventories.isEmpty()) {
            System.out.println("[GUIChess] Restoring " + savedInventories.size() + " saved inventories before shutdown...");
            for (Map.Entry<UUID, CompoundTag> entry : savedInventories.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && !player.hasDisconnected()) {
                    CompoundTag savedInventory = entry.getValue();
                    if (savedInventory != null) {
                        player.getInventory().load(savedInventory.getList("Inventory", 10));
                        player.inventoryMenu.broadcastChanges();
                    }
                }
            }
        }
        
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

        savedInventories.clear();
        
        markDataDirty();
        saveAllGameData();
    }

    /**
     * Creates a new chess game between two players with standard configuration.
     * 
     * @param player1 the player who will play as white pieces
     * @param player2 the player who will play as black pieces
     * @param timeControl the time control settings for the game
     * @return the created ChessGame instance, or null if either player is busy
     */
    public ChessGame createGame(ServerPlayer player1, ServerPlayer player2, TimeControl timeControl) {
        return createGame(player1, player2, timeControl, false, 0);
    }

    public ChessGame createGameWithRandomizedSides(ServerPlayer player1, ServerPlayer player2, TimeControl timeControl) {
        return createGame(player1, player2, timeControl, true, 0);
    }
    

    /**
     * Creates a new chess game between a player and an AI bot.
     * 
     * @param player the human player participating in the game
     * @param playerColor the color the human player will play as
     * @param timeControl the time control settings for the game
     * @param botElo the ELO rating of the bot opponent (affects difficulty)
     * @param hintsAllowed the number of hints the player is allowed to use
     * @return the created ChessBotGame instance, or null if the player is busy
     */
    public ChessGame createBotGame(ServerPlayer player, PieceColor playerColor, TimeControl timeControl, int botElo, int hintsAllowed) {
        if (isPlayerBusy(player)) {
            player.sendSystemMessage(Component.literal("§cYou are already in a game!"));
            return null;
        }

        BotProfile botProfile = getBotProfile(botElo);
        HumanPlayer humanPlayer = new HumanPlayer(player);
        BotPlayer botPlayer = new BotPlayer(botProfile);
        
        GameParticipant whiteParticipant = playerColor == PieceColor.WHITE ? humanPlayer : botPlayer;
        GameParticipant blackParticipant = playerColor == PieceColor.BLACK ? humanPlayer : botPlayer;

        ChessBotGame game = new ChessBotGame(whiteParticipant, blackParticipant, timeControl, hintsAllowed);
        activeGames.put(game.getGameId(), game);

        ChessGUI gui = new ChessGUI(player, game, playerColor);
        playerGUIs.put(player.getUUID(), gui);
        gui.open();

        player.sendSystemMessage(Component.literal("§aStarting game against " + botProfile.getBotName() + " (ELO " + botElo + ")!"));

        return game;
    }
    
    /**
     * Creates a bot vs bot game with specified ELO ratings.
     * 
     * @param initiator the player who initiated the bot vs bot game
     * @param whiteElo the ELO rating for the white bot
     * @param blackElo the ELO rating for the black bot
     * @param timeControl the time control settings
     * @return the created BotVsBotGame instance
     */
    public BotVsBotGame createBotVsBotGame(ServerPlayer initiator, int whiteElo, int blackElo, TimeControl timeControl) {
        BotVsBotGame game = new BotVsBotGame(initiator, whiteElo, blackElo, timeControl);
        activeGames.put(game.getGameId(), game);
        
        // Open spectator GUI for the initiator
        game.addInitiatorAsSpectator();
        
        initiator.sendSystemMessage(Component.literal("§aStarting bot vs bot game!"));
        initiator.sendSystemMessage(Component.literal("§7White: Bot (ELO " + whiteElo + ") vs Black: Bot (ELO " + blackElo + ")"));
        
        return game;
    }
    
    /**
     * Creates chess game between two players.
     * Sets up GUIs, saves inventories, assigns sides.
     */
    public ChessGame createGame(ServerPlayer player1, ServerPlayer player2, TimeControl timeControl, boolean randomizeSides, int hintsAllowed) {
        HumanPlayer humanPlayer1 = new HumanPlayer(player1);
        HumanPlayer humanPlayer2 = new HumanPlayer(player2);
        
        GameParticipant whiteParticipant, blackParticipant;
        ServerPlayer whitePlayer, blackPlayer;

        if (randomizeSides) {
            if (Math.random() < 0.5) {
                whiteParticipant = humanPlayer1;
                blackParticipant = humanPlayer2;
                whitePlayer = player1;
                blackPlayer = player2;
            } else {
                whiteParticipant = humanPlayer2;
                blackParticipant = humanPlayer1;
                whitePlayer = player2;
                blackPlayer = player1;
            }
            whitePlayer.sendSystemMessage(Component.literal("§fYou are playing as White"));
            blackPlayer.sendSystemMessage(Component.literal("§8You are playing as Black"));
        } else {
            whiteParticipant = humanPlayer1;
            blackParticipant = humanPlayer2;
            whitePlayer = player1;
            blackPlayer = player2;
        }

        ChessGame game = new ChessGame(whiteParticipant, blackParticipant, timeControl, hintsAllowed);
        activeGames.put(game.getGameId(), game);

        ChessGUI whiteGUI = new ChessGUI(whitePlayer, game, PieceColor.WHITE);
        ChessGUI blackGUI = new ChessGUI(blackPlayer, game, PieceColor.BLACK);

        playerGUIs.put(whitePlayer.getUUID(), whiteGUI);
        playerGUIs.put(blackPlayer.getUUID(), blackGUI);

        spectatorGUIs.put(game.getGameId(), new ArrayList<>());
        timeWarningsSent.put(game.getGameId(), new HashSet<>());
        
        savePlayerInventory(whitePlayer);
        savePlayerInventory(blackPlayer);

        whiteGUI.open();
        blackGUI.open();

        return game;
    }

    /**
     * Adds a spectator to an active chess game.
     * 
     * Creates a spectator GUI for the given player to observe the specified game.
     * Players cannot spectate their own games and will receive an error message
     * if they attempt to do so.
     * 
     * @param game the active chess game to spectate
     * @param spectator the player who wants to spectate the game
     */
    public void addSpectator(ChessGame game, ServerPlayer spectator) {
        if (game == null || spectator == null) return;
        
        // Check if spectator is a player in the game (null-safe for bot vs bot games)
        ServerPlayer whitePlayer = game.getWhitePlayer();
        ServerPlayer blackPlayer = game.getBlackPlayer();
        if ((whitePlayer != null && whitePlayer.equals(spectator)) || 
            (blackPlayer != null && blackPlayer.equals(spectator))) {
            spectator.sendSystemMessage(Component.literal("§cYou cannot spectate your own game."));
            return;
        }

        SpectatorGUI specGUI = new SpectatorGUI(spectator, game);
        List<SpectatorGUI> guis = spectatorGUIs.computeIfAbsent(game.getGameId(), k -> new ArrayList<>());
        guis.add(specGUI);
        specGUI.open();
    }
    
    public void addSpectatorToBotVsBotGame(BotVsBotGame game, ServerPlayer spectator) {
        if (game == null || spectator == null) return;
        
        // For bot vs bot games, anyone can spectate since there are no human players
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

    /**
     * Ends game and cleans up.
     * Saves history, updates ELO, restores inventories.
     */
    public void endGame(UUID gameId) {
        ChessGame game = activeGames.remove(gameId);
        if (game != null) {
            saveGameHistory(game);
            
            showPostGameAnalysisOptions(game);

            ChessGUI whiteGui = playerGUIs.remove(game.getWhitePlayer().getUUID());
            ChessGUI blackGui = playerGUIs.remove(game.getBlackPlayer().getUUID());

            clearPlayerInventoryFromChessPieces(game.getWhitePlayer());
            clearPlayerInventoryFromChessPieces(game.getBlackPlayer());

            if (whiteGui != null && whiteGui.isOpen()) whiteGui.close();
            if (blackGui != null && blackGui.isOpen()) blackGui.close();

            List<SpectatorGUI> specGuis = spectatorGUIs.remove(game.getGameId());
            if (specGuis != null) {
                specGuis.forEach(gui -> {
                    if (gui.isOpen()) gui.close();
                });
            }

            timeWarningsSent.remove(gameId);
        }
    }

    private void clearPlayerInventoryFromChessPieces(ServerPlayer player) {
        if (player != null && !player.hasDisconnected()) {
            server.execute(() -> {
                CompoundTag savedInventory = savedInventories.remove(player.getUUID());
                if (savedInventory != null) {
                    player.getInventory().load(savedInventory.getList("Inventory", 10));
                }
                
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.sendAllDataToRemote();
                
                player.containerMenu.broadcastChanges();
            });
        }
    }
    
    public void savePlayerInventory(ServerPlayer player) {
        if (player != null && !player.hasDisconnected()) {
            CompoundTag tag = new CompoundTag();
            ListTag inventoryTag = new ListTag();
            player.getInventory().save(inventoryTag);
            tag.put("Inventory", inventoryTag);
            savedInventories.put(player.getUUID(), tag);
        }
    }

    /**
     * Creates a new chess challenge between two players with standard settings.
     * 
     * This convenience method creates a challenge with no side randomization
     * and no hints allowed. The challenger will play as white if accepted.
     * 
     * @param challenger the player initiating the challenge
     * @param challenged the player being challenged
     * @param timeControl the proposed time control for the game
     * @return the created ChessChallenge instance, or null if either player is busy
     * @see #createChallenge(ServerPlayer, ServerPlayer, TimeControl, boolean, int)
     */
    public ChessChallenge createChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        return createChallenge(challenger, challenged, timeControl, false, 0);
    }

    public ChessChallenge createChallengeWithRandomizedSides(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        return createChallenge(challenger, challenged, timeControl, true, 0);
    }
    
    public ChessChallenge createChallengeWithConfiguration(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl, boolean randomizeSides, int hintsAllowed) {
        return createChallenge(challenger, challenged, timeControl, randomizeSides, hintsAllowed);
    }

    private ChessChallenge createChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl, boolean randomizeSides, int hintsAllowed) {
        if (isPlayerBusy(challenger) || isPlayerBusy(challenged)) {
            return null;
        }

        ChessChallenge challenge = new ChessChallenge(challenger, challenged, timeControl);
        pendingChallenges.put(challenge.challengeId, challenge);

        String sideInfo = randomizeSides ? " (randomized sides)" : "";
        String hintInfo = hintsAllowed > 0 ? " | " + hintsAllowed + " hints" : "";
        challenged.sendSystemMessage(Component.literal(
                "§e" + challenger.getName().getString() + " challenges you to a " +
                        timeControl.displayName + " game" + sideInfo + hintInfo + "! Use /chess accept or /chess decline"
        ));
        return challenge;
    }
    
    /**
     * Registers a pre-configured chess challenge in the system.
     * 
     * This method accepts a fully configured ChessChallenge object and registers it
     * with the pending challenges system. It handles notification of both players
     * and validates that neither player is currently busy.
     * 
     * @param challenge the pre-configured challenge to register
     */
    public void createChallenge(ChessChallenge challenge) {
        if (challenge == null || isPlayerBusy(challenge.challenger) || isPlayerBusy(challenge.challenged)) {
            if (challenge != null) {
                challenge.challenger.sendSystemMessage(Component.literal("§cFailed to create challenge - one or both players are busy."));
            }
            return;
        }

        pendingChallenges.put(challenge.challengeId, challenge);

        String sideInfo = challenge.getChallengerPreferredSide() != null ? 
                " (" + challenge.challenger.getName().getString() + " wants " + challenge.getChallengerPreferredSide() + ")" : 
                " (random sides)";
        String betInfo = challenge.hasBet() ? " | " + challenge.getChallengerBet().size() + " items bet" : "";
        
        challenge.challenged.sendSystemMessage(Component.literal(
                "§e" + challenge.challenger.getName().getString() + " challenges you to a " +
                        challenge.timeControl.displayName + " game" + sideInfo + betInfo +
                        "! Use /chess accept or /chess decline"
        ));
        
        challenge.challenger.sendSystemMessage(Component.literal("§aChallenge sent to " + challenge.challenged.getName().getString()));
    }

    /**
     * Accepts challenge and creates game.
     * Validates expiry, creates game, handles betting.
     */
    public boolean acceptChallenge(ServerPlayer player, ChessChallenge challenge) {
        if (challenge == null || !challenge.challenged.equals(player) || challenge.isExpired()) {
            if (challenge != null && challenge.isExpired()) pendingChallenges.remove(challenge.challengeId);
            return false;
        }

        if (isPlayerBusyExcluding(challenge.challenger, challenge.challengeId) || isPlayerBusyExcluding(challenge.challenged, challenge.challengeId)) {
            player.sendSystemMessage(Component.literal("§cOne of the players became busy. Cannot start game."));
            if (!challenge.challenger.equals(player)) {
                challenge.challenger.sendSystemMessage(Component.literal("§cCould not start game with " + player.getName().getString() + " as one of you became busy."));
            }
            pendingChallenges.remove(challenge.challengeId);
            return false;
        }

        pendingChallenges.remove(challenge.challengeId);

        
        PieceColor challengerColor;
        if (challenge.getChallengerPreferredSide() != null) {
            challengerColor = challenge.getChallengerPreferredSide();
        } else {
            challengerColor = Math.random() < 0.5 ? PieceColor.WHITE : PieceColor.BLACK;
        }

        ChessGame game;
        if (challengerColor == PieceColor.WHITE) {
            game = createGame(challenge.challenger, challenge.challenged, challenge.timeControl, false, challenge.hintsAllowed);
        } else {
            game = createGame(challenge.challenged, challenge.challenger, challenge.timeControl, false, challenge.hintsAllowed);
        }
        
        if (challenge.hasBet() && game != null) {
            game.setBetItems(challenge.getAllBetItems());
        }

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

    /**
     * Declines a pending chess challenge.
     * 
     * This method handles challenge rejection including removal from pending challenges,
     * notification of both players, and return of any bet items to the challenger.
     * 
     * @param player the player declining the challenge (must be the challenged player)
     * @param challenge the challenge being declined
     * @return true if the challenge was successfully declined, false if invalid
     */
    public boolean declineChallenge(ServerPlayer player, ChessChallenge challenge) {
        if (challenge == null || !challenge.challenged.equals(player)) {
            return false;
        }
        
        pendingChallenges.remove(challenge.challengeId);

        if (challenge.hasBet()) {
            for (ItemStack item : challenge.getChallengerBet()) {
                if (!challenge.challenger.getInventory().add(item)) {
                    challenge.challenger.drop(item, false);
                }
            }
            challenge.challenger.sendSystemMessage(Component.literal(
                    "§c" + player.getName().getString() + " declined your challenge. Your bet items have been returned."
            ));
        } else {
            challenge.challenger.sendSystemMessage(Component.literal(
                    "§c" + player.getName().getString() + " declined your challenge."
            ));
        }
        
        player.sendSystemMessage(Component.literal("§cChallenge declined."));
        return true;
    }

    /**
     * Retrieves or creates player data for the specified player.
     * 
     * This method provides access to persistent player statistics including
     * ELO rating, games played, wins, losses, and other tracked metrics.
     * If no data exists for the player, new PlayerData is created and stored.
     * 
     * @param player the player whose data to retrieve
     * @return the PlayerData instance for the specified player, never null
     */
    public PlayerData getPlayerData(ServerPlayer player) {
        if (playerDataStorage == null) {
            return new PlayerData(player.getUUID(), player.getName().getString());
        }
        return playerDataStorage.getPlayerData(player.getUUID(), player.getName().getString());
    }
    
    public BotProfile getBotProfile(int targetElo) {
        if (botDataStorage == null) {
            return new BotProfile(targetElo);
        }
        return botDataStorage.getBotProfile(targetElo);
    }

    /**
     * Retrieves the top players sorted by ELO rating for leaderboard display.
     * 
     * Only includes players who have played at least one game and returns them
     * sorted by ELO rating in descending order (highest first).
     * 
     * @param limit the maximum number of players to return
     * @return a list of PlayerData objects sorted by ELO rating, limited to the specified count
     */
    public List<PlayerData> getLeaderboard(int limit) {
        if (playerDataStorage == null) {
            return new ArrayList<>();
        }
        return playerDataStorage.getAllPlayerData().values().stream()
                .filter(p -> p.gamesPlayed >= 1)
                .sorted(Comparator.comparingInt(PlayerData::getElo).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the active chess GUI for the specified player.
     * 
     * Returns the ChessGUI instance if the player is currently in an active game,
     * or null if the player has no active GUI.
     * 
     * @param player the player whose GUI to retrieve
     * @return the active ChessGUI for the player, or null if none exists
     */
    public ChessGUI getPlayerGUI(ServerPlayer player) {
        if (player == null) return null;
        return playerGUIs.get(player.getUUID());
    }

    /**
     * Retrieves the active chess game for the specified player.
     * 
     * Searches through all active games to find one where the specified player
     * is participating as either white or black player.
     * 
     * @param player the player whose active game to find
     * @return the ChessGame instance where the player is participating, or null if none found
     */
    public ChessGame getPlayerGame(ServerPlayer player) {
        if (player == null) return null;
        return activeGames.values().stream()
                .filter(ChessGame::isGameActive)
                .filter(game -> (game.getWhitePlayer() != null && game.getWhitePlayer().equals(player)) ||
                        (game.getBlackPlayer() != null && game.getBlackPlayer().equals(player)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if player is in game or has pending challenge.
     */
    public boolean isPlayerBusy(ServerPlayer player) {
        if (player == null) return true;
        if (getPlayerGame(player) != null) return true;
        return pendingChallenges.values().stream()
                .anyMatch(c -> !c.isExpired() && (c.challenger.equals(player) || c.challenged.equals(player)));
    }

    private void tickAllGames() {
        try {
            for (ChessGame game : activeGames.values()) {
                // Check for time warnings before ticking
                checkTimeWarnings(game);

                game.tickTimer();
                
                // Update timer displays in GUIs
                updateGameGUITimers(game);
            }
        } catch (Exception e) {
            System.err.println("[GUIChess] Error during game ticking: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateGameGUITimers(ChessGame game) {
        // Update player GUIs
        if (game.getWhitePlayer() != null) {
            ChessGUI whiteGUI = playerGUIs.get(game.getWhitePlayer().getUUID());
            if (whiteGUI != null && whiteGUI.isOpen()) {
                whiteGUI.updateTimerDisplays();
            }
        }
        
        if (game.getBlackPlayer() != null) {
            ChessGUI blackGUI = playerGUIs.get(game.getBlackPlayer().getUUID());
            if (blackGUI != null && blackGUI.isOpen()) {
                blackGUI.updateTimerDisplays();
            }
        }
        
        // Update spectator GUIs
        List<SpectatorGUI> specGuis = spectatorGUIs.get(game.getGameId());
        if (specGuis != null) {
            for (SpectatorGUI gui : specGuis) {
                if (gui.isOpen()) {
                    gui.updateTimerDisplays();
                }
            }
        }
    }

    private void checkTimeWarnings(ChessGame game) {
        if (game == null || !game.isGameActive()) return;

        Set<Integer> warnings = timeWarningsSent.get(game.getGameId());
        if (warnings == null) return;

        int whiteTime = game.getWhiteTimeLeft();
        int blackTime = game.getBlackTimeLeft();

        if (game.getTimeControl().initialSeconds <= 120) return;

        
        if (whiteTime <= 60 && whiteTime > 59 && !warnings.contains(60)) {
            sendTimeWarning(game.getWhitePlayer(), "§c⚠ 1 minute remaining!");
            warnings.add(60);
        }
        if (blackTime <= 60 && blackTime > 59 && !warnings.contains(-60)) {
            sendTimeWarning(game.getBlackPlayer(), "§c⚠ 1 minute remaining!");
            warnings.add(-60);
        }

        if (game.getTimeControl().initialSeconds > 180) {
            if (whiteTime <= 10 && whiteTime > 9 && !warnings.contains(10)) {
                sendTimeWarning(game.getWhitePlayer(), "§4⚠⚠ 10 SECONDS LEFT! ⚠⚠");
                warnings.add(10);
            }
            if (blackTime <= 10 && blackTime > 9 && !warnings.contains(-10)) {
                sendTimeWarning(game.getBlackPlayer(), "§4⚠⚠ 10 SECONDS LEFT! ⚠⚠");
                warnings.add(-10);
            }
        }
    }

    private void sendTimeWarning(ServerPlayer player, String message) {
        if (player != null && !player.hasDisconnected()) {
            player.sendSystemMessage(Component.literal(message));

            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    private void cleanupExpiredChallenges() {
        pendingChallenges.entrySet().removeIf(entry -> {
            ChessChallenge challenge = entry.getValue();
            if (challenge.isExpired()) {
                if (challenge.hasBet()) {
                    for (ItemStack item : challenge.getChallengerBet()) {
                        if (!challenge.challenger.getInventory().add(item)) {
                            challenge.challenger.drop(item, false);
                        }
                    }
                    challenge.challenger.sendSystemMessage(Component.literal(
                            "§7Your challenge to " + challenge.challenged.getName().getString() + " has expired. Your bet items have been returned."
                    ));
                } else {
                    challenge.challenger.sendSystemMessage(Component.literal(
                            "§7Your challenge to " + challenge.challenged.getName().getString() + " has expired."
                    ));
                }
                
                challenge.challenged.sendSystemMessage(Component.literal(
                        "§7The challenge from " + challenge.challenger.getName().getString() + " has expired."
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

    /**
     * Initializes SavedData instances for player data and match history.
     */
    private void initializeSavedData() {
        if (server != null) {
            playerDataStorage = server.overworld().getDataStorage()
                    .computeIfAbsent(ChessPlayerDataStorage.factory(), ChessPlayerDataStorage.getDataName());
            
            botDataStorage = server.overworld().getDataStorage()
                    .computeIfAbsent(ChessBotDataStorage.factory(), ChessBotDataStorage.getDataName());
            
            matchHistoryStorage = server.overworld().getDataStorage()
                    .computeIfAbsent(ChessMatchHistoryStorage.factory(), ChessMatchHistoryStorage.getDataName());
        }
    }
    
    /**
     * Marks SavedData as dirty to trigger automatic persistence.
     */
    public void markDataDirty() {
        if (playerDataStorage != null) {
            playerDataStorage.setDirty();
        }
        if (botDataStorage != null) {
            botDataStorage.setDirty();
        }
        if (matchHistoryStorage != null) {
            matchHistoryStorage.setDirty();
        }
    }

    /**
     * Saves individual game data to the game history archive.
     * 
     * Stores completed game information including moves, results, and metadata
     * to separate files in the game_history directory for future analysis or replay.
     * 
     * @param gameData the NBT data containing complete game information
     */
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

    /**
     * Administrative function to forcibly end a player's current game.
     * 
     * This method allows administrators to terminate games when necessary,
     * such as when dealing with problematic players or system issues.
     * Both players are notified that the game was ended by an admin.
     * 
     * @param player the player whose game should be forcibly ended
     */
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

    /**
     * Administrative function to remove all pending challenges involving a player.
     * 
     * This method cancels all challenges where the specified player is either
     * the challenger or the challenged party. Both players involved in each
     * cancelled challenge are notified of the administrative action.
     * 
     * @param player the player whose challenges should be removed
     */
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

    /**
     * Saves complete game history and updates player statistics.
     */
    private void saveGameHistory(ChessGame game) {
        try {
            List<GameHistory.MoveRecord> moveRecords = new ArrayList<>();
            List<ChessMove> moves = game.getBoard().getMoveHistory();
            List<String> fenHistory = game.getBoard().getFenHistory();
            List<Long> moveTimestamps = game.getMoveTimestamps();
            
            for (int i = 0; i < moves.size(); i++) {
                ChessMove move = moves.get(i);
                
                String fenBefore = i < fenHistory.size() ? fenHistory.get(i) : "unknown";
                String fenAfter = (i + 1) < fenHistory.size() ? fenHistory.get(i + 1) : game.getBoard().toFEN();
                
                long moveTime = i < moveTimestamps.size() ? moveTimestamps.get(i) : System.currentTimeMillis();
                
                long previousMoveTime = i > 0 && (i - 1) < moveTimestamps.size() ? 
                    moveTimestamps.get(i - 1) : game.getStartTime();
                int moveTimeMs = (int)(moveTime - previousMoveTime);
                
                GameHistory.MoveRecord record = new GameHistory.MoveRecord(
                    move,
                    move.toNotation(),
                    fenBefore,
                    fenAfter,
                    game.getWhiteTimeLeft(),
                    game.getBlackTimeLeft(),
                    moveTimeMs,
                    move.isCheck,
                    move.isCheckmate,
                    move.isCapture,
                    move.isCastling,
                    move.isEnPassant,
                    move.promotionPiece != null,
                    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(moveTime), java.time.ZoneOffset.UTC)
                );
                moveRecords.add(record);
            }
            
            PlayerData whiteData = game.getWhitePlayer() != null ? getPlayerData(game.getWhitePlayer()) : null;
            PlayerData blackData = game.getBlackPlayer() != null ? getPlayerData(game.getBlackPlayer()) : null;
            int originalWhiteElo = whiteData != null ? whiteData.elo : 1200;
            int originalBlackElo = blackData != null ? blackData.elo : 1200;
            
            boolean whiteWon = game.getBoard().getGameState().name().contains("WHITE_WINS");
            boolean blackWon = game.getBoard().getGameState().name().contains("BLACK_WINS");
            boolean isDraw = game.getBoard().getGameState().name().contains("DRAW") || 
                           game.getBoard().getGameState().name().contains("STALEMATE");
            
            double result = whiteWon ? 1.0 : (blackWon ? 0.0 : 0.5);
            
            int whiteEloChange = 0;
            int blackEloChange = 0;
            if (whiteData != null && blackData != null) {
                double[] newElos = calculateELO(whiteData.elo, blackData.elo, whiteData.gamesPlayed, blackData.gamesPlayed, result);
                whiteEloChange = (int)(newElos[0] - whiteData.elo);
                blackEloChange = (int)(newElos[1] - blackData.elo);
            }
            
            GameHistory gameHistory = new GameHistory(
                game.getGameId(),
                game.getWhitePlayer() != null ? game.getWhitePlayer().getUUID() : UUID.randomUUID(),
                game.getBlackPlayer() != null ? game.getBlackPlayer().getUUID() : UUID.randomUUID(),
                game.getWhitePlayer() != null ? game.getWhitePlayer().getName().getString() : "Chess Bot",
                game.getBlackPlayer() != null ? game.getBlackPlayer().getName().getString() : "Chess Bot",
                game.getTimeControl(),
                java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(game.getStartTime()), java.time.ZoneOffset.UTC),
                java.time.LocalDateTime.now(),
                game.getBoard().getGameState(),
                fenHistory.isEmpty() ? "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" : fenHistory.get(0),
                moveRecords,
                originalWhiteElo,
                originalBlackElo,
                whiteEloChange,
                blackEloChange,
                true,
                game.getHintsAllowed(),
                game.getWhiteHintsUsed(),
                game.getBlackHintsUsed(),
                !game.getBetItems().isEmpty(),
                game.getBoard().getGameState().name()
            );
            
            if (matchHistoryManager != null) {
                matchHistoryManager.saveGameHistory(gameHistory);
            }
            
            updatePlayerStatistics(gameHistory);
            
        } catch (Exception e) {
            System.err.println("[GUIChess] Failed to save game history for " + game.getGameId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Updates player statistics based on completed game.
     */
    private void updatePlayerStatistics(GameHistory gameHistory) {
        ServerPlayer whitePlayer = server.getPlayerList().getPlayer(gameHistory.whitePlayerId);
        ServerPlayer blackPlayer = server.getPlayerList().getPlayer(gameHistory.blackPlayerId);
        
        PlayerData whiteData = whitePlayer != null ? getPlayerData(whitePlayer) : null;
        PlayerData blackData = blackPlayer != null ? getPlayerData(blackPlayer) : null;
        
        if (whiteData != null || blackData != null) {
            boolean whiteWon = gameHistory.finalResult.name().contains("WHITE_WINS");
            boolean blackWon = gameHistory.finalResult.name().contains("BLACK_WINS");
            boolean isDraw = gameHistory.finalResult.name().contains("DRAW") || 
                           gameHistory.finalResult.name().contains("STALEMATE");
            
            double result = whiteWon ? 1.0 : (blackWon ? 0.0 : 0.5);
            
            if (whiteData != null && blackData != null) {
                double[] newElos = calculateELO(whiteData.elo, blackData.elo, whiteData.gamesPlayed, blackData.gamesPlayed, result);
                
                whiteData.elo = (int)newElos[0];
                blackData.elo = (int)newElos[1];
                
                whiteData.updateAfterGame(gameHistory, whiteWon, isDraw);
                blackData.updateAfterGame(gameHistory, blackWon, isDraw);
                
                if (playerDataStorage != null) {
                    playerDataStorage.updatePlayerData(whiteData);
                    playerDataStorage.updatePlayerData(blackData);
                }
            } else if (whiteData != null) {
                whiteData.updateAfterGame(gameHistory, whiteWon, isDraw);
                if (playerDataStorage != null) {
                    playerDataStorage.updatePlayerData(whiteData);
                }
            } else if (blackData != null) {
                blackData.updateAfterGame(gameHistory, blackWon, isDraw);
                if (playerDataStorage != null) {
                    playerDataStorage.updatePlayerData(blackData);
                }
            }
        }
    }
    
    /**
     * Calculates expected score for a player against an opponent.
     */
    private double expectedScore(int playerRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (double)(opponentRating - playerRating) / 400.0));
    }
    
    /**
     * Calculates new rating for a player.
     */
    private double newRating(double expectedScore, double actualScore, int playerRating, double kFactor) {
        return playerRating + kFactor * (actualScore - expectedScore);
    }
    
    /**
     * Determines K-factor based on FIDE rules.
     */
    private double playerKFactor(int playerRating, int totalGamesPlayed) {
        if (playerRating < 2400 && totalGamesPlayed < 30) {
            return 40.0;
        } else if (playerRating < 2400 && totalGamesPlayed >= 30) {
            return 20.0;
        }
        return 10.0;
    }

    /**
     * Calculates ELO rating changes for both players based on game result using FIDE rules.
     * 
     * @param whiteELO current white player ELO
     * @param blackELO current black player ELO  
     * @param whiteGamesPlayed number of games white player has played
     * @param blackGamesPlayed number of games black player has played
     * @param result game result (1.0 = white win, 0.5 = draw, 0.0 = black win)
     * @return array with new white ELO and new black ELO
     */
    private double[] calculateELO(int whiteELO, int blackELO, int whiteGamesPlayed, int blackGamesPlayed, double result) {
        double expectedWhite = expectedScore(whiteELO, blackELO);
        double expectedBlack = expectedScore(blackELO, whiteELO);
        
        double whiteKFactor = playerKFactor(whiteELO, whiteGamesPlayed);
        double blackKFactor = playerKFactor(blackELO, blackGamesPlayed);
        
        double newWhiteELO = newRating(expectedWhite, result, whiteELO, whiteKFactor);
        double newBlackELO = newRating(expectedBlack, 1.0 - result, blackELO, blackKFactor);

        return new double[]{newWhiteELO, newBlackELO};
    }
    
    /**
     * Shows post-game analysis options to players.
     */
    private void showPostGameAnalysisOptions(ChessGame game) {
        if (game.getWhitePlayer() != null && !game.getWhitePlayer().hasDisconnected()) {
            game.getWhitePlayer().sendSystemMessage(Component.literal("§aGame finished! Use §e/chess analyze §ato review your game with computer analysis."));
        }
        
        if (game.getBlackPlayer() != null && !game.getBlackPlayer().hasDisconnected()) {
            game.getBlackPlayer().sendSystemMessage(Component.literal("§aGame finished! Use §e/chess analyze §ato review your game with computer analysis."));
        }
        
        scheduler.schedule(() -> {
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Opens match analysis GUI for a player's recent game.
     */
    public void openMatchAnalysis(ServerPlayer player, UUID gameId) {
        if (matchHistoryManager == null) {
            player.sendSystemMessage(Component.literal("§cMatch history not available."));
            return;
        }
        
        GameHistory gameHistory = matchHistoryManager.loadGameHistory(gameId);
        if (gameHistory == null) {
            player.sendSystemMessage(Component.literal("§cGame not found."));
            return;
        }
        
        if (!gameHistory.whitePlayerId.equals(player.getUUID()) && 
            !gameHistory.blackPlayerId.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou were not a participant in this game."));
            return;
        }
        
        // Save player inventory before opening analysis GUI
        savePlayerInventory(player);
        
        MatchAnalysisGUI analysisGUI = new MatchAnalysisGUI(player, gameHistory);
        analysisGUI.open();
    }
    
    /**
     * Restores player inventory after closing analysis GUI.
     */
    public void restoreInventoryAfterAnalysis(ServerPlayer player) {
        clearPlayerInventoryFromChessPieces(player);
    }
    
    /**
     * Opens practice board GUI for a player with inventory management.
     */
    public void openPracticeBoard(ServerPlayer player) {
        savePlayerInventory(player);
        PracticeBoardGUI practiceGUI = new PracticeBoardGUI(player);
        practiceGUI.open();
    }
    
    /**
     * Opens practice board GUI with a specific FEN position.
     */
    public void openPracticeBoardWithFEN(ServerPlayer player, String fen) {
        savePlayerInventory(player);
        PracticeBoardGUI practiceGUI = new PracticeBoardGUI(player, fen);
        practiceGUI.open();
    }
    
    public MatchHistoryManager getMatchHistoryManager() {
        return matchHistoryManager;
    }

    public Map<UUID, ChessGame> getActiveGames() { return activeGames; }
    public Map<UUID, ChessChallenge> getPendingChallenges() { return pendingChallenges; }
}