package com.leclowndu93150.guichess.chess.rules;

import net.minecraft.network.chat.Component;

public enum GameUtility {
    // Timer and status (1030-1039)
    TIMER_WHITE(1030, "timer_white", Component.literal("§fWhite's Time")),
    TIMER_BLACK(1031, "timer_black", Component.literal("§8Black's Time")),
    TURN_INDICATOR_WHITE(1032, "turn_white", Component.literal("§f● White to Move")),
    TURN_INDICATOR_BLACK(1033, "turn_black", Component.literal("§8● Black to Move")),

    // Game controls (1040-1049)
    RESIGN_BUTTON(1040, "resign", Component.literal("§c⚠ Resign Game")),
    DRAW_OFFER(1041, "draw_offer", Component.literal("§e🤝 Offer Draw")),
    DRAW_ACCEPT(1042, "draw_accept", Component.literal("§a✓ Accept Draw")),
    DRAW_DECLINE(1043, "draw_decline", Component.literal("§c✗ Decline Draw")),

    // Navigation (1050-1059)
    SETTINGS_BUTTON(1050, "settings", Component.literal("§7⚙ Settings")),
    EXIT_BUTTON(1051, "exit", Component.literal("§c✗ Exit Game")),
    SPECTATE_BUTTON(1052, "spectate", Component.literal("§9👁 Spectate")),

    // Analysis (1060-1069)
    UNDO_MOVE(1060, "undo", Component.literal("§6↶ Undo Move")),
    REDO_MOVE(1061, "redo", Component.literal("§6↷ Redo Move")),
    ANALYZE_POSITION(1062, "analyze", Component.literal("§d🔍 Analyze")),
    STOCKFISH_HINT(1063, "hint", Component.literal("§b💡 Hint")),

    // Duel system (1070-1079)
    CHALLENGE_PLAYER(1070, "challenge", Component.literal("§e⚔ Challenge Player")),
    ACCEPT_CHALLENGE(1071, "accept", Component.literal("§a✓ Accept Challenge")),
    DECLINE_CHALLENGE(1072, "decline", Component.literal("§c✗ Decline Challenge")),
    VIEW_LEADERBOARD(1073, "leaderboard", Component.literal("§6🏆 Leaderboard")),

    // Promotion pieces (1080-1083)
    PROMOTE_QUEEN(1080, "promote_queen", Component.literal("§e👑 Promote to Queen")),
    PROMOTE_ROOK(1081, "promote_rook", Component.literal("§e🏰 Promote to Rook")),
    PROMOTE_BISHOP(1082, "promote_bishop", Component.literal("§e⛪ Promote to Bishop")),
    PROMOTE_KNIGHT(1083, "promote_knight", Component.literal("§e🐴 Promote to Knight"));

    private final int modelData;
    private final String modelName;
    private final Component displayName;

    GameUtility(int modelData, String modelName, Component displayName) {
        this.modelData = modelData;
        this.modelName = modelName;
        this.displayName = displayName;
    }

    public int getModelData() {
        return modelData;
    }

    public String getModelName() {
        return modelName;
    }

    public Component getDisplayName() {
        return displayName;
    }
}
