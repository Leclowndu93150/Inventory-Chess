package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.data.BotProfile;
import java.util.UUID;

/**
 * Represents a chess bot player with its own profile and statistics.
 */
public class BotPlayer implements GameParticipant {
    private final BotProfile profile;
    
    public BotPlayer(BotProfile profile) {
        this.profile = profile;
    }
    
    @Override
    public UUID getId() {
        return profile.getBotId();
    }
    
    @Override
    public String getName() {
        return profile.getBotName();
    }
    
    @Override
    public boolean isBot() {
        return true;
    }
    
    @Override
    public boolean isHuman() {
        return false;
    }
    
    public BotProfile getProfile() {
        return profile;
    }
    
    public int getElo() {
        return profile.getCurrentElo();
    }
    
    public int getTargetElo() {
        return profile.getTargetElo();
    }
}