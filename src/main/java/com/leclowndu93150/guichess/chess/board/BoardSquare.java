package com.leclowndu93150.guichess.chess.board;

import net.minecraft.network.chat.Component;

public enum BoardSquare {
    // Light and dark squares (1020-1021)
    LIGHT_SQUARE(1020, "light_square", Component.literal("")),
    DARK_SQUARE(1021, "dark_square", Component.literal("")),

    // Special highlighting (1022-1027)
    SELECTED_SQUARE(1022, "selected_square", Component.literal("§e● Selected")),
    LAST_MOVE_FROM(1024, "last_move_from", Component.literal("§6● Last Move")),
    LAST_MOVE_TO(1025, "last_move_to", Component.literal("§6● Last Move")),
    CHECK_SQUARE(1026, "check_square", Component.literal("§c● Check!")),
    CAPTURE_MOVE(1027, "capture_move", Component.literal("§c● Capture")),

    // Valid move squares for different base colors (1028-1029) - these already exist
    VALID_LIGHT_SQUARE(1028, "valid_light_square", Component.literal("§a● Valid Move")),
    VALID_DARK_SQUARE(1029, "valid_dark_square", Component.literal("§a● Valid Move"));

    public final int modelData;
    public final String modelName;
    public final Component displayName;

    BoardSquare(int modelData, String modelName, Component displayName) {
        this.modelData = modelData;
        this.modelName = modelName;
        this.displayName = displayName;
    }

    // Note: Piece overlays use model data ranges 2000-2999
    // Each piece has multiple variations:
    // - piece_light (normal on light square)
    // - piece_dark (normal on dark square)
    // - piece_selected_light (selected on light square)
    // - piece_selected_dark (selected on dark square)
    // - piece_capture_light (can be captured on light square)
    // - piece_capture_dark (can be captured on dark square)
    // - piece_lastmove_light (last moved piece on light square)
    // - piece_lastmove_dark (last moved piece on dark square)
    // - piece_check_light (king in check on light square - kings only)
    // - piece_check_dark (king in check on dark square - kings only)
}