package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.util.TimeControl;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ChessChallenge {
    public final UUID challengeId;
    public final ServerPlayer challenger;
    public final ServerPlayer challenged;
    public final TimeControl timeControl;
    public final long challengeTime;
    public final long expiryTime;

    public ChessChallenge(ServerPlayer challenger, ServerPlayer challenged, TimeControl timeControl) {
        this.challengeId = UUID.randomUUID();
        this.challenger = challenger;
        this.challenged = challenged;
        this.timeControl = timeControl;
        this.challengeTime = System.currentTimeMillis();
        this.expiryTime = challengeTime + 30000; // 30 second expiry
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}
