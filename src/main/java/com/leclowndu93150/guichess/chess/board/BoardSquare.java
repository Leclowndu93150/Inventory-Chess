package com.leclowndu93150.guichess.chess.board;

import net.minecraft.network.chat.Component;

public enum BoardSquare {
    // Light and dark squares (1020-1021)
    LIGHT_SQUARE(1020, "light_square", Component.literal("")),
    DARK_SQUARE(1021, "dark_square", Component.literal("")),

    // Special highlighting (1022-1027)
    SELECTED_SQUARE(1022, "selected_square", Component.literal("§e● Selected")),
    VALID_MOVE(1023, "valid_move", Component.literal("§a● Valid Move")),
    LAST_MOVE_FROM(1024, "last_move_from", Component.literal("§6● Last Move")),
    LAST_MOVE_TO(1025, "last_move_to", Component.literal("§6● Last Move")),
    CHECK_SQUARE(1026, "check_square", Component.literal("§c● Check!")),
    CAPTURE_MOVE(1027, "capture_move", Component.literal("§c● Capture"));

    public final int modelData;
    public final String modelName;
    public final Component displayName;

    BoardSquare(int modelData, String modelName, Component displayName) {
        this.modelData = modelData;
        this.modelName = modelName;
        this.displayName = displayName;
    }
}
