package com.leclowndu93150.guichess.data.models;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/**
 * Represents a chess bot profile with persistent statistics and configuration.
 * Each bot ELO level has its own profile to track performance over time.
 */
public class BotProfile {
    private final UUID botId;
    private final String botName;
    private final int targetElo;
    private int currentElo;
    private int gamesPlayed;
    private int humanWins;
    private int botWins;
    private int draws;
    private long totalPlayTime;
    
    public BotProfile(int targetElo) {
        this.botId = generateBotUUID(targetElo);
        this.targetElo = targetElo;
        this.currentElo = targetElo;
        this.botName = generateBotName(targetElo);
        this.gamesPlayed = 0;
        this.humanWins = 0;
        this.botWins = 0;
        this.draws = 0;
        this.totalPlayTime = 0;
    }
    
    private BotProfile(UUID botId, String botName, int targetElo, int currentElo, 
                      int gamesPlayed, int humanWins, int botWins, int draws, long totalPlayTime) {
        this.botId = botId;
        this.botName = botName;
        this.targetElo = targetElo;
        this.currentElo = currentElo;
        this.gamesPlayed = gamesPlayed;
        this.humanWins = humanWins;
        this.botWins = botWins;
        this.draws = draws;
        this.totalPlayTime = totalPlayTime;
    }
    
    private static UUID generateBotUUID(int elo) {
        return UUID.nameUUIDFromBytes(("ChessBot_" + elo).getBytes());
    }
    
    private static String generateBotName(int elo) {
        if (elo < 800) return "Beginner Bot";
        if (elo < 1200) return "Novice Bot";
        if (elo < 1600) return "Intermediate Bot";
        if (elo < 2000) return "Advanced Bot";
        if (elo < 2400) return "Expert Bot";
        return "Master Bot";
    }
    
    public void recordGameResult(boolean botWon, boolean draw, long gameTime) {
        gamesPlayed++;
        totalPlayTime += gameTime;
        
        if (draw) {
            draws++;
        } else if (botWon) {
            botWins++;
        } else {
            humanWins++;
        }
    }
    
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("botId", botId);
        tag.putString("botName", botName);
        tag.putInt("targetElo", targetElo);
        tag.putInt("currentElo", currentElo);
        tag.putInt("gamesPlayed", gamesPlayed);
        tag.putInt("humanWins", humanWins);
        tag.putInt("botWins", botWins);
        tag.putInt("draws", draws);
        tag.putLong("totalPlayTime", totalPlayTime);
        return tag;
    }
    
    public static BotProfile fromNBT(CompoundTag tag) {
        return new BotProfile(
            tag.getUUID("botId"),
            tag.getString("botName"),
            tag.getInt("targetElo"),
            tag.getInt("currentElo"),
            tag.getInt("gamesPlayed"),
            tag.getInt("humanWins"),
            tag.getInt("botWins"),
            tag.getInt("draws"),
            tag.getLong("totalPlayTime")
        );
    }
    
    public double getWinRateAgainstHumans() {
        if (gamesPlayed == 0) return 0.0;
        return (double) botWins / gamesPlayed;
    }
    
    public double getAverageGameTime() {
        if (gamesPlayed == 0) return 0.0;
        return (double) totalPlayTime / gamesPlayed;
    }
    
    public UUID getBotId() { return botId; }
    public String getBotName() { return botName; }
    public int getTargetElo() { return targetElo; }
    public int getCurrentElo() { return currentElo; }
    public void setCurrentElo(int elo) { this.currentElo = elo; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getHumanWins() { return humanWins; }
    public int getBotWins() { return botWins; }
    public int getDraws() { return draws; }
}