package com.leclowndu93150.guichess.data;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Player data management
public class PlayerData {
    public UUID playerId;
    public String playerName;
    public int elo = 1200; // Starting ELO
    public int gamesPlayed = 0;
    public int wins = 0;
    public int losses = 0;
    public int draws = 0;
    public long totalPlayTime = 0;
    public String favoriteTimeControl = "BLITZ_5_0";
    public Map<String, Integer> openingStats = new HashMap<>();

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public double getWinRate() {
        if (gamesPlayed == 0) return 0.0;
        return (double) wins / gamesPlayed;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("playerId", playerId.toString());
        tag.putString("playerName", playerName);
        tag.putInt("elo", elo);
        tag.putInt("gamesPlayed", gamesPlayed);
        tag.putInt("wins", wins);
        tag.putInt("losses", losses);
        tag.putInt("draws", draws);
        tag.putLong("totalPlayTime", totalPlayTime);
        tag.putString("favoriteTimeControl", favoriteTimeControl);
        return tag;
    }

    public static PlayerData fromNBT(CompoundTag tag) {
        PlayerData data = new PlayerData(
                UUID.fromString(tag.getString("playerId")),
                tag.getString("playerName")
        );
        data.elo = tag.getInt("elo");
        data.gamesPlayed = tag.getInt("gamesPlayed");
        data.wins = tag.getInt("wins");
        data.losses = tag.getInt("losses");
        data.draws = tag.getInt("draws");
        data.totalPlayTime = tag.getLong("totalPlayTime");
        data.favoriteTimeControl = tag.getString("favoriteTimeControl");
        return data;
    }
}
