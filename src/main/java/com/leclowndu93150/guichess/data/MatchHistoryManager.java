package com.leclowndu93150.guichess.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistence and retrieval of complete match history data.
 * Handles both individual game files and efficient querying for player statistics.
 */
public class MatchHistoryManager {
    private final Path historyDirectory;
    private final Path indexFile;
    private final Map<UUID, GameHistory> gameCache = new ConcurrentHashMap<>();
    private final ChessMatchHistoryStorage historyStorage;
    private final Map<UUID, Set<UUID>> playerGameIndex = new ConcurrentHashMap<>(); // player -> game IDs
    private final Map<String, Set<UUID>> dateIndex = new ConcurrentHashMap<>(); // date -> game IDs
    private boolean indexLoaded = false;

    public MatchHistoryManager(Path dataDirectory, ChessMatchHistoryStorage historyStorage) {
        this.historyStorage = historyStorage;
        this.historyDirectory = dataDirectory.resolve("match_history");
        this.indexFile = historyDirectory.resolve("index.nbt");
        
        try {
            Files.createDirectories(historyDirectory);
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to create match history directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Fallback constructor for compatibility
    public MatchHistoryManager(Path dataDirectory) {
        this(dataDirectory, null);
    }

    /**
     * Saves a complete game history to persistent storage and updates indices.
     */
    public void saveGameHistory(GameHistory gameHistory) {
        try {
            // Save individual game file
            Path gameFile = historyDirectory.resolve("game_" + gameHistory.gameId + ".nbt");
            CompoundTag gameTag = gameHistory.toNBT();
            
            try (FileOutputStream fos = new FileOutputStream(gameFile.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                NbtIo.writeCompressed(gameTag, bos);
            }

            // Update cache and SavedData storage
            gameCache.put(gameHistory.gameId, gameHistory);
            if (historyStorage != null) {
                historyStorage.addGame(gameHistory);
            }

            System.out.println("[GUIChess] Saved game history: " + gameHistory.gameId);
            
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to save game history for " + gameHistory.gameId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads a specific game history by ID.
     */
    public GameHistory loadGameHistory(UUID gameId) {
        // Check cache first
        if (gameCache.containsKey(gameId)) {
            return gameCache.get(gameId);
        }

        // Load from file
        try {
            Path gameFile = historyDirectory.resolve("game_" + gameId + ".nbt");
            if (!Files.exists(gameFile)) {
                return null;
            }

            CompoundTag gameTag;
            try (FileInputStream fis = new FileInputStream(gameFile.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                gameTag = NbtIo.readCompressed(bis, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            GameHistory gameHistory = GameHistory.fromNBT(gameTag);
            gameCache.put(gameId, gameHistory);
            return gameHistory;

        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to load game history for " + gameId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets all game IDs for a specific player, sorted by date (newest first).
     */
    public List<UUID> getPlayerGameIds(UUID playerId) {
        Set<UUID> gameIds;
        if (historyStorage != null) {
            gameIds = historyStorage.getPlayerGameIds(playerId);
        } else {
            ensureIndexLoaded();
            gameIds = playerGameIndex.getOrDefault(playerId, new HashSet<>());
        }
        List<UUID> sortedGameIds = new ArrayList<>(gameIds);
        
        // Sort by game start time (newest first)
        sortedGameIds.sort((id1, id2) -> {
            GameHistory game1 = loadGameHistory(id1);
            GameHistory game2 = loadGameHistory(id2);
            if (game1 == null || game2 == null) return 0;
            return game2.startTime.compareTo(game1.startTime);
        });
        
        return sortedGameIds;
    }

    /**
     * Gets recent games for a player (limited count).
     */
    public List<GameHistory> getRecentPlayerGames(UUID playerId, int limit) {
        List<UUID> gameIds = getPlayerGameIds(playerId);
        List<GameHistory> games = new ArrayList<>();
        
        for (int i = 0; i < Math.min(limit, gameIds.size()); i++) {
            GameHistory game = loadGameHistory(gameIds.get(i));
            if (game != null) {
                games.add(game);
            }
        }
        
        return games;
    }

    /**
     * Gets games between two specific players.
     */
    public List<GameHistory> getGamesBetweenPlayers(UUID player1Id, UUID player2Id) {
        Set<UUID> player1Games, player2Games;
        if (historyStorage != null) {
            player1Games = historyStorage.getPlayerGameIds(player1Id);
            player2Games = historyStorage.getPlayerGameIds(player2Id);
        } else {
            ensureIndexLoaded();
            player1Games = playerGameIndex.getOrDefault(player1Id, new HashSet<>());
            player2Games = playerGameIndex.getOrDefault(player2Id, new HashSet<>());
        }
        
        // Find intersection
        Set<UUID> commonGames = new HashSet<>(player1Games);
        commonGames.retainAll(player2Games);
        
        List<GameHistory> games = new ArrayList<>();
        for (UUID gameId : commonGames) {
            GameHistory game = loadGameHistory(gameId);
            if (game != null) {
                games.add(game);
            }
        }
        
        // Sort by start time (newest first)
        games.sort((g1, g2) -> g2.startTime.compareTo(g1.startTime));
        return games;
    }

    /**
     * Gets games played on a specific date.
     */
    public List<GameHistory> getGamesOnDate(String date) {
        Set<UUID> gameIds;
        if (historyStorage != null) {
            gameIds = historyStorage.getGamesOnDate(date);
        } else {
            ensureIndexLoaded();
            gameIds = dateIndex.getOrDefault(date, new HashSet<>());
        }
        List<GameHistory> games = new ArrayList<>();
        
        for (UUID gameId : gameIds) {
            GameHistory game = loadGameHistory(gameId);
            if (game != null) {
                games.add(game);
            }
        }
        
        return games;
    }

    /**
     * Gets total number of games for a player.
     */
    public int getPlayerGameCount(UUID playerId) {
        if (historyStorage != null) {
            return historyStorage.getPlayerGameCount(playerId);
        } else {
            ensureIndexLoaded();
            return playerGameIndex.getOrDefault(playerId, new HashSet<>()).size();
        }
    }

    /**
     * Gets statistics about games between two players.
     */
    public PlayerVsPlayerStats getPlayerVsPlayerStats(UUID player1Id, UUID player2Id) {
        List<GameHistory> games = getGamesBetweenPlayers(player1Id, player2Id);
        
        int player1Wins = 0;
        int player2Wins = 0;
        int draws = 0;
        
        for (GameHistory game : games) {
            String result = game.getResultString();
            if (result.startsWith("1-0")) {
                if (game.isPlayerWhite(player1Id)) {
                    player1Wins++;
                } else {
                    player2Wins++;
                }
            } else if (result.startsWith("0-1")) {
                if (game.isPlayerWhite(player1Id)) {
                    player2Wins++;
                } else {
                    player1Wins++;
                }
            } else {
                draws++;
            }
        }
        
        return new PlayerVsPlayerStats(player1Wins, player2Wins, draws, games.size());
    }

    /**
     * Searches for games matching specific criteria.
     */
    public List<GameHistory> searchGames(GameSearchCriteria criteria) {
        ensureIndexLoaded();
        
        List<GameHistory> allGames = new ArrayList<>();
        
        // Start with player-specific games if specified
        if (criteria.playerId != null) {
            List<UUID> gameIds = getPlayerGameIds(criteria.playerId);
            for (UUID gameId : gameIds) {
                GameHistory game = loadGameHistory(gameId);
                if (game != null) {
                    allGames.add(game);
                }
            }
        } else {
            // Load all games (expensive operation)
            try {
                Files.list(historyDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("game_"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String gameIdStr = fileName.substring(5, fileName.length() - 4); // Remove "game_" and ".nbt"
                        try {
                            UUID gameId = UUID.fromString(gameIdStr);
                            GameHistory game = loadGameHistory(gameId);
                            if (game != null) {
                                allGames.add(game);
                            }
                        } catch (IllegalArgumentException e) {
                            // Invalid UUID, skip
                        }
                    });
            } catch (IOException e) {
                System.err.println("[GUIChess] Error searching games: " + e.getMessage());
            }
        }
        
        // Apply filters
        return allGames.stream()
            .filter(game -> matchesCriteria(game, criteria))
            .sorted((g1, g2) -> g2.startTime.compareTo(g1.startTime))
            .limit(criteria.limit > 0 ? criteria.limit : Integer.MAX_VALUE)
            .toList();
    }

    private boolean matchesCriteria(GameHistory game, GameSearchCriteria criteria) {
        if (criteria.timeControl != null && !game.timeControl.equals(criteria.timeControl)) {
            return false;
        }
        
        if (criteria.result != null && !game.finalResult.equals(criteria.result)) {
            return false;
        }
        
        if (criteria.minDate != null && game.startTime.isBefore(criteria.minDate)) {
            return false;
        }
        
        if (criteria.maxDate != null && game.startTime.isAfter(criteria.maxDate)) {
            return false;
        }
        
        if (criteria.opponentId != null) {
            if (!game.whitePlayerId.equals(criteria.opponentId) && !game.blackPlayerId.equals(criteria.opponentId)) {
                return false;
            }
            // Make sure the opponent is not the same as the main player
            if (criteria.playerId != null && criteria.opponentId.equals(criteria.playerId)) {
                return false;
            }
        }
        
        return true;
    }

    private void updateIndices(GameHistory gameHistory) {
        // Update player index
        playerGameIndex.computeIfAbsent(gameHistory.whitePlayerId, k -> new HashSet<>()).add(gameHistory.gameId);
        playerGameIndex.computeIfAbsent(gameHistory.blackPlayerId, k -> new HashSet<>()).add(gameHistory.gameId);
        
        // Update date index
        String dateKey = gameHistory.startTime.toLocalDate().toString();
        dateIndex.computeIfAbsent(dateKey, k -> new HashSet<>()).add(gameHistory.gameId);
    }

    private void ensureIndexLoaded() {
        if (!indexLoaded) {
            loadIndex();
            indexLoaded = true;
        }
    }

    private void loadIndex() {
        try {
            if (!Files.exists(indexFile)) {
                rebuildIndex();
                return;
            }

            CompoundTag indexTag;
            try (FileInputStream fis = new FileInputStream(indexFile.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                indexTag = NbtIo.readCompressed(bis, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            // Load player index
            if (indexTag.contains("playerIndex")) {
                CompoundTag playerIndexTag = indexTag.getCompound("playerIndex");
                for (String playerIdStr : playerIndexTag.getAllKeys()) {
                    UUID playerId = UUID.fromString(playerIdStr);
                    ListTag gameIdsTag = playerIndexTag.getList(playerIdStr, net.minecraft.nbt.StringTag.TAG_STRING);
                    Set<UUID> gameIds = new HashSet<>();
                    for (int i = 0; i < gameIdsTag.size(); i++) {
                        gameIds.add(UUID.fromString(gameIdsTag.getString(i)));
                    }
                    playerGameIndex.put(playerId, gameIds);
                }
            }

            // Load date index
            if (indexTag.contains("dateIndex")) {
                CompoundTag dateIndexTag = indexTag.getCompound("dateIndex");
                for (String date : dateIndexTag.getAllKeys()) {
                    ListTag gameIdsTag = dateIndexTag.getList(date, net.minecraft.nbt.StringTag.TAG_STRING);
                    Set<UUID> gameIds = new HashSet<>();
                    for (int i = 0; i < gameIdsTag.size(); i++) {
                        gameIds.add(UUID.fromString(gameIdsTag.getString(i)));
                    }
                    dateIndex.put(date, gameIds);
                }
            }

        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to load match history index, rebuilding: " + e.getMessage());
            rebuildIndex();
        }
    }

    private void rebuildIndex() {
        playerGameIndex.clear();
        dateIndex.clear();
        
        try {
            Files.list(historyDirectory)
                .filter(path -> path.getFileName().toString().startsWith("game_") && 
                               path.getFileName().toString().endsWith(".nbt"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String gameIdStr = fileName.substring(5, fileName.length() - 4);
                    try {
                        UUID gameId = UUID.fromString(gameIdStr);
                        GameHistory game = loadGameHistory(gameId);
                        if (game != null) {
                            updateIndices(game);
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID, skip
                    }
                });
                
            saveIndex();
            System.out.println("[GUIChess] Rebuilt match history index with " + 
                playerGameIndex.size() + " players and " + dateIndex.size() + " dates");
                
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to rebuild match history index: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveIndex() {
        try {
            CompoundTag indexTag = new CompoundTag();
            
            // Save player index
            CompoundTag playerIndexTag = new CompoundTag();
            for (Map.Entry<UUID, Set<UUID>> entry : playerGameIndex.entrySet()) {
                ListTag gameIdsTag = new ListTag();
                for (UUID gameId : entry.getValue()) {
                    gameIdsTag.add(net.minecraft.nbt.StringTag.valueOf(gameId.toString()));
                }
                playerIndexTag.put(entry.getKey().toString(), gameIdsTag);
            }
            indexTag.put("playerIndex", playerIndexTag);
            
            // Save date index
            CompoundTag dateIndexTag = new CompoundTag();
            for (Map.Entry<String, Set<UUID>> entry : dateIndex.entrySet()) {
                ListTag gameIdsTag = new ListTag();
                for (UUID gameId : entry.getValue()) {
                    gameIdsTag.add(net.minecraft.nbt.StringTag.valueOf(gameId.toString()));
                }
                dateIndexTag.put(entry.getKey(), gameIdsTag);
            }
            indexTag.put("dateIndex", dateIndexTag);
            
            try (FileOutputStream fos = new FileOutputStream(indexFile.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                NbtIo.writeCompressed(indexTag, bos);
            }
            
        } catch (IOException e) {
            System.err.println("[GUIChess] Failed to save match history index: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears the cache to free memory.
     */
    public void clearCache() {
        gameCache.clear();
    }

    /**
     * Statistics for games between two specific players.
     */
    public static class PlayerVsPlayerStats {
        public final int player1Wins;
        public final int player2Wins;
        public final int draws;
        public final int totalGames;

        public PlayerVsPlayerStats(int player1Wins, int player2Wins, int draws, int totalGames) {
            this.player1Wins = player1Wins;
            this.player2Wins = player2Wins;
            this.draws = draws;
            this.totalGames = totalGames;
        }

        public double getPlayer1WinRate() {
            return totalGames > 0 ? (double) player1Wins / totalGames : 0.0;
        }

        public double getPlayer2WinRate() {
            return totalGames > 0 ? (double) player2Wins / totalGames : 0.0;
        }

        public double getDrawRate() {
            return totalGames > 0 ? (double) draws / totalGames : 0.0;
        }
    }

    /**
     * Search criteria for finding specific games.
     */
    public static class GameSearchCriteria {
        public UUID playerId;
        public UUID opponentId;
        public com.leclowndu93150.guichess.chess.util.TimeControl timeControl;
        public com.leclowndu93150.guichess.chess.util.GameState result;
        public java.time.LocalDateTime minDate;
        public java.time.LocalDateTime maxDate;
        public int limit = 50; // Default limit

        public GameSearchCriteria() {}

        public GameSearchCriteria forPlayer(UUID playerId) {
            this.playerId = playerId;
            return this;
        }

        public GameSearchCriteria withOpponent(UUID opponentId) {
            this.opponentId = opponentId;
            return this;
        }

        public GameSearchCriteria withTimeControl(com.leclowndu93150.guichess.chess.util.TimeControl timeControl) {
            this.timeControl = timeControl;
            return this;
        }

        public GameSearchCriteria withResult(com.leclowndu93150.guichess.chess.util.GameState result) {
            this.result = result;
            return this;
        }

        public GameSearchCriteria fromDate(java.time.LocalDateTime minDate) {
            this.minDate = minDate;
            return this;
        }

        public GameSearchCriteria toDate(java.time.LocalDateTime maxDate) {
            this.maxDate = maxDate;
            return this;
        }

        public GameSearchCriteria limit(int limit) {
            this.limit = limit;
            return this;
        }
    }
}