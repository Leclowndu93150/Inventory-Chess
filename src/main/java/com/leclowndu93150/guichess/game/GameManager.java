package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.gui.ChessGUI;
import com.leclowndu93150.guichess.gui.SpectatorGUI;
import com.leclowndu93150.guichess.util.ChessSoundManager;
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
 * Central coordinator and manager for the entire chess system.
 * 
 * This singleton class serves as the main orchestrator for all chess-related activities,
 * managing the complete lifecycle of chess games, player challenges, spectator systems,
 * and player data persistence. It coordinates between multiple subsystems including:
 * 
 * <ul>
 *   <li>Active game state management and lifecycle</li>
 *   <li>Player challenge creation, acceptance, and expiration</li>
 *   <li>GUI management for players and spectators</li>
 *   <li>Player data persistence and ELO rating system</li>
 *   <li>Time control enforcement and warnings</li>
 *   <li>Inventory management during games</li>
 *   <li>Bot game integration</li>
 *   <li>Administrative functions</li>
 * </ul>
 * 
 * <p>The GameManager maintains thread-safe concurrent collections for all game state
 * and uses a scheduled executor service for periodic tasks like timer updates,
 * challenge cleanup, and data persistence.</p>
 * 
 * <p>Key architectural responsibilities:</p>
 * <ul>
 *   <li>Ensuring players cannot be in multiple games simultaneously</li>
 *   <li>Managing player inventories before/after games</li>
 *   <li>Coordinating GUI updates across all participants</li>
 *   <li>Handling game completion and cleanup</li>
 *   <li>Providing centralized access to player statistics</li>
 * </ul>
 * 
 * @author GUIChess Team
 * @since 1.0
 */
public class GameManager {
    private static GameManager instance;
    private ScheduledExecutorService scheduler;

    private final Map<UUID, ChessGame> activeGames = new ConcurrentHashMap<>();
    private final Map<UUID, ChessChallenge> pendingChallenges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, ChessGUI> playerGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, List<SpectatorGUI>> spectatorGUIs = new ConcurrentHashMap<>();

    // Time warning tracking
    private final Map<UUID, Set<Integer>> timeWarningsSent = new ConcurrentHashMap<>();
    
    // Saved inventories for players in games
    private final Map<UUID, CompoundTag> savedInventories = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private Path dataDirectory;

    private GameManager() {
        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    /**
     * Returns the singleton instance of the GameManager.
     * 
     * This method provides thread-safe access to the single GameManager instance
     * that coordinates all chess system operations. The instance is lazily initialized
     * on first access.
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
     * Initializes the GameManager with the Minecraft server instance and starts all background services.
     * 
     * This method sets up the complete chess system infrastructure including:
     * <ul>
     *   <li>Data directory creation and player data loading</li>
     *   <li>Scheduled executor service for periodic tasks</li>
     *   <li>Timer management for active games</li>
     *   <li>Challenge expiration cleanup</li>
     *   <li>Automatic data persistence</li>
     *   <li>GUI reopen functionality</li>
     * </ul>
     * 
     * <p>This method should be called exactly once during server startup,
     * typically from the mod's initialization phase.</p>
     * 
     * @param server the MinecraftServer instance to associate with this GameManager
     * @throws RuntimeException if initialization fails due to I/O errors or scheduler issues
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

                // Handle white player (may be null in bot games)
                ServerPlayer whitePlayer = game.getWhitePlayer();
                if (whitePlayer != null) {
                    ChessGUI whiteGUI = playerGUIs.get(whitePlayer.getUUID());
                    if (whiteGUI != null && !whiteGUI.isOpen() && whiteGUI.getAutoReopen()) {
                        whiteGUI.open();
                    }
                }

                // Handle black player (may be null in bot games)
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
     * Gracefully shuts down the GameManager and all associated services.
     * 
     * This method performs a complete cleanup of the chess system including:
     * <ul>
     *   <li>Stopping the scheduled executor service</li>
     *   <li>Saving all player data to persistent storage</li>
     *   <li>Clearing saved player inventories</li>
     *   <li>Ensuring all background tasks complete</li>
     * </ul>
     * 
     * <p>This method should be called during server shutdown to ensure
     * no data is lost and all resources are properly released.</p>
     */
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

        // Clear saved inventories
        savedInventories.clear();
        
        savePlayerData();
        saveAllGameData();
    }

    /**
     * Creates a new chess game between two players with standard configuration.
     * 
     * This is a convenience method that creates a game with player1 as white,
     * player2 as black, no side randomization, and no hints allowed.
     * 
     * @param player1 the player who will play as white pieces
     * @param player2 the player who will play as black pieces
     * @param timeControl the time control settings for the game
     * @return the created ChessGame instance, or null if either player is busy
     * @see #createGame(ServerPlayer, ServerPlayer, TimeControl, boolean, int)
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
     * This method creates a single-player game where the human player competes
     * against an AI opponent with the specified ELO rating. The bot's difficulty
     * is determined by the ELO parameter.
     * 
     * @param player the human player participating in the game
     * @param playerColor the color (WHITE or BLACK) the human player will play as
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

        ChessBotGame game = new ChessBotGame(player, playerColor, timeControl, botElo, hintsAllowed);
        activeGames.put(game.getGameId(), game);

        ChessGUI gui = new ChessGUI(player, game, playerColor);
        playerGUIs.put(player.getUUID(), gui);
        gui.open();

        player.sendSystemMessage(Component.literal("§aStarting game against bot (ELO " + botElo + ")!"));

        return game;
    }
    
    /**
     * Creates a new chess game between two players with full configuration options.
     * 
     * This is the core game creation method that handles all game setup including:
     * <ul>
     *   <li>Side assignment (with optional randomization)</li>
     *   <li>GUI initialization for both players</li>
     *   <li>Spectator system setup</li>
     *   <li>Player inventory preservation</li>
     *   <li>Time warning system initialization</li>
     * </ul>
     * 
     * @param player1 the first player (white if not randomized)
     * @param player2 the second player (black if not randomized)
     * @param timeControl the time control settings for the game
     * @param randomizeSides whether to randomly assign white/black to players
     * @param hintsAllowed the number of hints each player is allowed to use
     * @return the created ChessGame instance with all systems initialized
     */
    public ChessGame createGame(ServerPlayer player1, ServerPlayer player2, TimeControl timeControl, boolean randomizeSides, int hintsAllowed) {
        ServerPlayer whitePlayer, blackPlayer;

        if (randomizeSides) {
            if (Math.random() < 0.5) {
                whitePlayer = player1;
                blackPlayer = player2;
            } else {
                whitePlayer = player2;
                blackPlayer = player1;
            }
            // Notify players of their colors
            whitePlayer.sendSystemMessage(Component.literal("§fYou are playing as White"));
            blackPlayer.sendSystemMessage(Component.literal("§8You are playing as Black"));
        } else {
            whitePlayer = player1;
            blackPlayer = player2;
        }

        ChessGame game = new ChessGame(whitePlayer, blackPlayer, timeControl, hintsAllowed);
        activeGames.put(game.getGameId(), game);

        ChessGUI whiteGUI = new ChessGUI(whitePlayer, game, PieceColor.WHITE);
        ChessGUI blackGUI = new ChessGUI(blackPlayer, game, PieceColor.BLACK);

        playerGUIs.put(whitePlayer.getUUID(), whiteGUI);
        playerGUIs.put(blackPlayer.getUUID(), blackGUI);

        spectatorGUIs.put(game.getGameId(), new ArrayList<>());
        timeWarningsSent.put(game.getGameId(), new HashSet<>());
        
        // Save player inventories before game starts
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

    /**
     * Ends an active chess game and performs complete cleanup.
     * 
     * This method handles the complete game termination process including:
     * <ul>
     *   <li>Removing the game from active games collection</li>
     *   <li>Closing all player and spectator GUIs</li>
     *   <li>Restoring player inventories to pre-game state</li>
     *   <li>Cleaning up time warning tracking</li>
     *   <li>Removing spectator GUI references</li>
     * </ul>
     * 
     * @param gameId the unique identifier of the game to end
     */
    public void endGame(UUID gameId) {
        ChessGame game = activeGames.remove(gameId);
        if (game != null) {
            ChessGUI whiteGui = playerGUIs.remove(game.getWhitePlayer().getUUID());
            ChessGUI blackGui = playerGUIs.remove(game.getBlackPlayer().getUUID());

            // Clear inventory after match ended
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

            // Clean up time warnings
            timeWarningsSent.remove(gameId);
        }
    }

    private void clearPlayerInventoryFromChessPieces(ServerPlayer player) {
        if (player != null && !player.hasDisconnected()) {
            server.execute(() -> {
                // Restore saved inventory if we have one
                CompoundTag savedInventory = savedInventories.remove(player.getUUID());
                if (savedInventory != null) {
                    player.getInventory().load(savedInventory.getList("Inventory", 10));
                }
                
                // Force a full inventory update to ensure client syncs properly
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.sendAllDataToRemote();
                
                // Send container update packet to force client refresh
                player.containerMenu.broadcastChanges();
            });
        }
    }
    
    private void savePlayerInventory(ServerPlayer player) {
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
     * Accepts a pending chess challenge and initiates the game.
     * 
     * This method handles the complete challenge acceptance process including:
     * <ul>
     *   <li>Validation that the challenge is still valid and not expired</li>
     *   <li>Verification that both players are still available</li>
     *   <li>Side assignment based on challenge preferences</li>
     *   <li>Game creation with proper configuration</li>
     *   <li>Bet item management if applicable</li>
     *   <li>Notification of both players</li>
     * </ul>
     * 
     * @param player the player accepting the challenge (must be the challenged player)
     * @param challenge the challenge being accepted
     * @return true if the challenge was successfully accepted and game created, false otherwise
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

        // Items were already taken by the GUIs, so we don't need to take them again
        // The challenge already contains the bet items from both players

        // Determine sides
        PieceColor challengerColor;
        if (challenge.getChallengerPreferredSide() != null) {
            challengerColor = challenge.getChallengerPreferredSide();
        } else {
            // Random sides
            challengerColor = Math.random() < 0.5 ? PieceColor.WHITE : PieceColor.BLACK;
        }

        // Create game with proper sides
        ChessGame game;
        if (challengerColor == PieceColor.WHITE) {
            game = createGame(challenge.challenger, challenge.challenged, challenge.timeControl, false, challenge.hintsAllowed);
        } else {
            game = createGame(challenge.challenged, challenge.challenger, challenge.timeControl, false, challenge.hintsAllowed);
        }
        
        // Store bet items in the game
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

        // Return bet items to challenger
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
        return playerDataMap.computeIfAbsent(player.getUUID(),
                k -> new PlayerData(player.getUUID(), player.getName().getString()));
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
        return playerDataMap.values().stream()
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
     * Checks whether a player is currently busy and unavailable for new games or challenges.
     * 
     * A player is considered busy if they are:
     * <ul>
     *   <li>Currently participating in an active game</li>
     *   <li>Have a pending challenge (either sent or received)</li>
     * </ul>
     * 
     * @param player the player to check availability for
     * @return true if the player is busy and unavailable, false if they can participate in new activities
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
            }
        } catch (Exception e) {
            System.err.println("[GUIChess] Error during game ticking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkTimeWarnings(ChessGame game) {
        if (game == null || !game.isGameActive()) return;

        Set<Integer> warnings = timeWarningsSent.get(game.getGameId());
        if (warnings == null) return;

        int whiteTime = game.getWhiteTimeLeft();
        int blackTime = game.getBlackTimeLeft();

        // Only warn for games with substantial time (more than 2 minutes initially)
        if (game.getTimeControl().initialSeconds <= 120) return;

        // 1 minute warning
        if (whiteTime <= 60 && whiteTime > 59 && !warnings.contains(60)) {
            sendTimeWarning(game.getWhitePlayer(), "§c⚠ 1 minute remaining!");
            warnings.add(60);
        }
        if (blackTime <= 60 && blackTime > 59 && !warnings.contains(-60)) {
            sendTimeWarning(game.getBlackPlayer(), "§c⚠ 1 minute remaining!");
            warnings.add(-60);
        }

        // 10 second warning (for games with more than 3 minutes initially)
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

            // Play warning sound
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    private void cleanupExpiredChallenges() {
        pendingChallenges.entrySet().removeIf(entry -> {
            ChessChallenge challenge = entry.getValue();
            if (challenge.isExpired()) {
                // Return bet items to challenger if any
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
     * Saves all player data to persistent storage.
     * 
     * This method serializes all PlayerData objects to NBT format and writes them
     * to the player_data.nbt file in the chess data directory. This is called
     * periodically by the scheduler and during shutdown to ensure data persistence.
     */
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

    public Map<UUID, ChessGame> getActiveGames() { return activeGames; }
    public Map<UUID, ChessChallenge> getPendingChallenges() { return pendingChallenges; }
}