package com.leclowndu93150.guichess.game.players;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Wrapper for human players in chess games.
 */
public class HumanPlayer implements GameParticipant {
    private final ServerPlayer serverPlayer;
    
    public HumanPlayer(ServerPlayer serverPlayer) {
        this.serverPlayer = serverPlayer;
    }
    
    @Override
    public UUID getId() {
        return serverPlayer.getUUID();
    }
    
    @Override
    public String getName() {
        return serverPlayer.getName().getString();
    }
    
    @Override
    public boolean isBot() {
        return false;
    }
    
    @Override
    public boolean isHuman() {
        return true;
    }
    
    public ServerPlayer getServerPlayer() {
        return serverPlayer;
    }
    
    public boolean hasDisconnected() {
        return serverPlayer.hasDisconnected();
    }
}