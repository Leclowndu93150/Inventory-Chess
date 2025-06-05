package com.leclowndu93150.guichess.game;

import java.util.UUID;

/**
 * Common interface for all chess game participants (human players and bots).
 */
public interface GameParticipant {
    UUID getId();
    String getName();
    boolean isBot();
    boolean isHuman();
}