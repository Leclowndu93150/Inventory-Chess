package com.leclowndu93150.guichess.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * SavedData wrapper for chess match history indices.
 * Stores game indices and metadata while individual games are stored as separate files.
 */
public class ChessMatchHistoryStorage extends SavedData {
    private static final String DATA_NAME = "chess_match_history";
    
    private final Map<UUID, Set<UUID>> playerGameIndex = new HashMap<>(); // player -> game IDs
    private final Map<String, Set<UUID>> dateIndex = new HashMap<>(); // date -> game IDs
    private final Set<UUID> allGameIds = new HashSet<>();

    public ChessMatchHistoryStorage() {
        super();
    }

    public ChessMatchHistoryStorage(CompoundTag tag, HolderLookup.Provider registries) {
        this();
        
        // Load player index
        if (tag.contains("playerIndex")) {
            CompoundTag playerIndexTag = tag.getCompound("playerIndex");
            for (String playerIdStr : playerIndexTag.getAllKeys()) {
                UUID playerId = UUID.fromString(playerIdStr);
                ListTag gameIdsTag = playerIndexTag.getList(playerIdStr, StringTag.TAG_STRING);
                Set<UUID> gameIds = new HashSet<>();
                for (int i = 0; i < gameIdsTag.size(); i++) {
                    gameIds.add(UUID.fromString(gameIdsTag.getString(i)));
                }
                playerGameIndex.put(playerId, gameIds);
            }
        }

        // Load date index
        if (tag.contains("dateIndex")) {
            CompoundTag dateIndexTag = tag.getCompound("dateIndex");
            for (String date : dateIndexTag.getAllKeys()) {
                ListTag gameIdsTag = dateIndexTag.getList(date, StringTag.TAG_STRING);
                Set<UUID> gameIds = new HashSet<>();
                for (int i = 0; i < gameIdsTag.size(); i++) {
                    gameIds.add(UUID.fromString(gameIdsTag.getString(i)));
                }
                dateIndex.put(date, gameIds);
            }
        }

        // Load all game IDs
        if (tag.contains("allGameIds")) {
            ListTag allGameIdsTag = tag.getList("allGameIds", StringTag.TAG_STRING);
            for (int i = 0; i < allGameIdsTag.size(); i++) {
                allGameIds.add(UUID.fromString(allGameIdsTag.getString(i)));
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Save player index
        CompoundTag playerIndexTag = new CompoundTag();
        for (Map.Entry<UUID, Set<UUID>> entry : playerGameIndex.entrySet()) {
            ListTag gameIdsTag = new ListTag();
            for (UUID gameId : entry.getValue()) {
                gameIdsTag.add(StringTag.valueOf(gameId.toString()));
            }
            playerIndexTag.put(entry.getKey().toString(), gameIdsTag);
        }
        tag.put("playerIndex", playerIndexTag);

        // Save date index
        CompoundTag dateIndexTag = new CompoundTag();
        for (Map.Entry<String, Set<UUID>> entry : dateIndex.entrySet()) {
            ListTag gameIdsTag = new ListTag();
            for (UUID gameId : entry.getValue()) {
                gameIdsTag.add(StringTag.valueOf(gameId.toString()));
            }
            dateIndexTag.put(entry.getKey(), gameIdsTag);
        }
        tag.put("dateIndex", dateIndexTag);

        // Save all game IDs
        ListTag allGameIdsTag = new ListTag();
        for (UUID gameId : allGameIds) {
            allGameIdsTag.add(StringTag.valueOf(gameId.toString()));
        }
        tag.put("allGameIds", allGameIdsTag);

        return tag;
    }

    public void addGame(GameHistory gameHistory) {
        allGameIds.add(gameHistory.gameId);
        
        // Update player index
        playerGameIndex.computeIfAbsent(gameHistory.whitePlayerId, k -> new HashSet<>()).add(gameHistory.gameId);
        playerGameIndex.computeIfAbsent(gameHistory.blackPlayerId, k -> new HashSet<>()).add(gameHistory.gameId);
        
        // Update date index
        String dateKey = gameHistory.startTime.toLocalDate().toString();
        dateIndex.computeIfAbsent(dateKey, k -> new HashSet<>()).add(gameHistory.gameId);
        
        setDirty();
    }

    public Set<UUID> getPlayerGameIds(UUID playerId) {
        return new HashSet<>(playerGameIndex.getOrDefault(playerId, new HashSet<>()));
    }

    public Set<UUID> getGamesOnDate(String date) {
        return new HashSet<>(dateIndex.getOrDefault(date, new HashSet<>()));
    }

    public Set<UUID> getAllGameIds() {
        return new HashSet<>(allGameIds);
    }

    public int getPlayerGameCount(UUID playerId) {
        return playerGameIndex.getOrDefault(playerId, new HashSet<>()).size();
    }

    public void removeGame(UUID gameId) {
        if (allGameIds.remove(gameId)) {
            // Remove from player indices
            for (Set<UUID> playerGames : playerGameIndex.values()) {
                playerGames.remove(gameId);
            }
            
            // Remove from date indices
            for (Set<UUID> dateGames : dateIndex.values()) {
                dateGames.remove(gameId);
            }
            
            setDirty();
        }
    }

    /**
     * Factory for creating and loading chess match history storage.
     */
    public static Factory<ChessMatchHistoryStorage> factory() {
        return new Factory<>(
            ChessMatchHistoryStorage::new,
            ChessMatchHistoryStorage::new,
            null
        );
    }

    public static String getDataName() {
        return DATA_NAME;
    }
}