package com.leclowndu93150.guichess.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData wrapper for chess player statistics and data.
 * Integrates with Minecraft's world save system for automatic persistence.
 */
public class ChessPlayerDataStorage extends SavedData {
    private static final String DATA_NAME = "chess_player_data";
    
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    public ChessPlayerDataStorage() {
        super();
    }

    public ChessPlayerDataStorage(CompoundTag tag, HolderLookup.Provider registries) {
        this();
        if (tag.contains("players", ListTag.TAG_COMPOUND)) {
            ListTag playersNBT = tag.getList("players", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < playersNBT.size(); i++) {
                PlayerData data = PlayerData.fromNBT(playersNBT.getCompound(i));
                playerDataMap.put(data.playerId, data);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersNBT = new ListTag();
        for (PlayerData data : playerDataMap.values()) {
            playersNBT.add(data.toNBT());
        }
        tag.put("players", playersNBT);
        return tag;
    }

    public PlayerData getPlayerData(UUID playerId, String playerName) {
        return playerDataMap.computeIfAbsent(playerId, k -> {
            PlayerData data = new PlayerData(playerId, playerName);
            setDirty();
            return data;
        });
    }

    public void updatePlayerData(PlayerData playerData) {
        playerDataMap.put(playerData.playerId, playerData);
        setDirty();
    }

    public Map<UUID, PlayerData> getAllPlayerData() {
        return new HashMap<>(playerDataMap);
    }

    public void removePlayerData(UUID playerId) {
        if (playerDataMap.remove(playerId) != null) {
            setDirty();
        }
    }

    /**
     * Factory for creating and loading chess player data storage.
     */
    public static Factory<ChessPlayerDataStorage> factory() {
        return new Factory<>(
            ChessPlayerDataStorage::new,
            ChessPlayerDataStorage::new,
            null
        );
    }

    public static String getDataName() {
        return DATA_NAME;
    }
}