package com.leclowndu93150.guichess.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class PlayerData {
    public UUID playerId;
    public String playerName;
    public int elo = 1200;
    public int gamesPlayed = 0;
    public int wins = 0;
    public int losses = 0;
    public int draws = 0;
    public long totalPlayTime = 0;
    public String favoriteTimeControl = "BLITZ_5_0";
    public Map<String, Integer> openingStats = new HashMap<>();
    
    // Enhanced statistics for detailed tracking
    public int highestElo = 1200;
    public int lowestElo = 1200;
    public LocalDateTime lastGamePlayed;
    public int currentWinStreak = 0;
    public int currentLossStreak = 0;
    public int longestWinStreak = 0;
    public int longestLossStreak = 0;
    public int totalMovesPlayed = 0;
    public long averageMoveTime = 0; // in milliseconds
    public int blunders = 0;
    public int mistakes = 0;
    public int inaccuracies = 0;
    public int brilliantMoves = 0;
    public int goodMoves = 0;
    
    // Recent game references for quick access
    public List<UUID> recentGames = new ArrayList<>(); // Last 10 games
    public Map<String, Integer> timeControlStats = new HashMap<>(); // Games per time control
    public Map<String, Integer> opponentStats = new HashMap<>(); // Win/loss against specific opponents

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public int getElo() {
        return elo;
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
        
        // Enhanced statistics
        tag.putInt("highestElo", highestElo);
        tag.putInt("lowestElo", lowestElo);
        if (lastGamePlayed != null) {
            tag.putLong("lastGamePlayed", lastGamePlayed.toEpochSecond(ZoneOffset.UTC));
        }
        tag.putInt("currentWinStreak", currentWinStreak);
        tag.putInt("currentLossStreak", currentLossStreak);
        tag.putInt("longestWinStreak", longestWinStreak);
        tag.putInt("longestLossStreak", longestLossStreak);
        tag.putInt("totalMovesPlayed", totalMovesPlayed);
        tag.putLong("averageMoveTime", averageMoveTime);
        tag.putInt("blunders", blunders);
        tag.putInt("mistakes", mistakes);
        tag.putInt("inaccuracies", inaccuracies);
        tag.putInt("brilliantMoves", brilliantMoves);
        tag.putInt("goodMoves", goodMoves);
        
        // Recent games
        ListTag recentGamesTag = new ListTag();
        for (UUID gameId : recentGames) {
            recentGamesTag.add(StringTag.valueOf(gameId.toString()));
        }
        tag.put("recentGames", recentGamesTag);
        
        // Time control stats
        CompoundTag timeControlTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : timeControlStats.entrySet()) {
            timeControlTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("timeControlStats", timeControlTag);
        
        // Opponent stats
        CompoundTag opponentTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : opponentStats.entrySet()) {
            opponentTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("opponentStats", opponentTag);
        
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
        
        // Enhanced statistics
        data.highestElo = tag.getInt("highestElo");
        data.lowestElo = tag.getInt("lowestElo");
        if (tag.contains("lastGamePlayed")) {
            data.lastGamePlayed = LocalDateTime.ofEpochSecond(tag.getLong("lastGamePlayed"), 0, ZoneOffset.UTC);
        }
        data.currentWinStreak = tag.getInt("currentWinStreak");
        data.currentLossStreak = tag.getInt("currentLossStreak");
        data.longestWinStreak = tag.getInt("longestWinStreak");
        data.longestLossStreak = tag.getInt("longestLossStreak");
        data.totalMovesPlayed = tag.getInt("totalMovesPlayed");
        data.averageMoveTime = tag.getLong("averageMoveTime");
        data.blunders = tag.getInt("blunders");
        data.mistakes = tag.getInt("mistakes");
        data.inaccuracies = tag.getInt("inaccuracies");
        data.brilliantMoves = tag.getInt("brilliantMoves");
        data.goodMoves = tag.getInt("goodMoves");
        
        // Recent games
        if (tag.contains("recentGames")) {
            ListTag recentGamesTag = tag.getList("recentGames", StringTag.TAG_STRING);
            for (int i = 0; i < recentGamesTag.size(); i++) {
                data.recentGames.add(UUID.fromString(recentGamesTag.getString(i)));
            }
        }
        
        // Time control stats
        if (tag.contains("timeControlStats")) {
            CompoundTag timeControlTag = tag.getCompound("timeControlStats");
            for (String key : timeControlTag.getAllKeys()) {
                data.timeControlStats.put(key, timeControlTag.getInt(key));
            }
        }
        
        // Opponent stats
        if (tag.contains("opponentStats")) {
            CompoundTag opponentTag = tag.getCompound("opponentStats");
            for (String key : opponentTag.getAllKeys()) {
                data.opponentStats.put(key, opponentTag.getInt(key));
            }
        }
        
        return data;
    }
    
    /**
     * Updates statistics after a game is completed with analysis data
     */
    public void updateAfterGame(GameHistory gameHistory, boolean won, boolean drew) {
        gamesPlayed++;
        if (won) {
            wins++;
            currentWinStreak++;
            currentLossStreak = 0;
            if (currentWinStreak > longestWinStreak) {
                longestWinStreak = currentWinStreak;
            }
        } else if (!drew) {
            losses++;
            currentLossStreak++;
            currentWinStreak = 0;
            if (currentLossStreak > longestLossStreak) {
                longestLossStreak = currentLossStreak;
            }
        } else {
            draws++;
            currentWinStreak = 0;
            currentLossStreak = 0;
        }
        
        // Update ELO tracking
        if (elo > highestElo) highestElo = elo;
        if (elo < lowestElo) lowestElo = elo;
        
        lastGamePlayed = gameHistory.endTime;
        
        // Add to recent games (keep only last 10)
        recentGames.add(0, gameHistory.gameId);
        if (recentGames.size() > 10) {
            recentGames = recentGames.subList(0, 10);
        }
        
        // Update time control stats
        String timeControlName = gameHistory.timeControl.name();
        timeControlStats.put(timeControlName, timeControlStats.getOrDefault(timeControlName, 0) + 1);
        
        // Update move statistics from game analysis
        for (GameHistory.MoveRecord move : gameHistory.moves) {
            totalMovesPlayed++;
            if (move.isBlunder) blunders++;
            if (move.isMistake) mistakes++;
            if (move.isInaccuracy) inaccuracies++;
            if (move.isBrilliant) brilliantMoves++;
            if (move.isGood) goodMoves++;
        }
        
        // Update average move time
        if (totalMovesPlayed > 0) {
            long totalTime = 0;
            for (GameHistory.MoveRecord move : gameHistory.moves) {
                totalTime += move.moveTimeMs;
            }
            averageMoveTime = (averageMoveTime * (totalMovesPlayed - gameHistory.moves.size()) + totalTime) / totalMovesPlayed;
        }
    }
    
    public double getAccuracy() {
        if (totalMovesPlayed == 0) return 100.0;
        int goodMovesTotal = totalMovesPlayed - blunders - mistakes - inaccuracies;
        return (double) goodMovesTotal / totalMovesPlayed * 100.0;
    }
    
    public String getFormattedAverageMoveTime() {
        if (averageMoveTime < 1000) {
            return averageMoveTime + "ms";
        } else {
            return String.format("%.1fs", averageMoveTime / 1000.0);
        }
    }
}