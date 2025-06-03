package com.leclowndu93150.guichess.chess.pieces;

import net.minecraft.network.chat.Component;

public enum ChessPiece {
    // White pieces (1000-1005)
    WHITE_KING(1000, "white_king", "♔", Component.literal("§fWhite King")),
    WHITE_QUEEN(1001, "white_queen", "♕", Component.literal("§fWhite Queen")),
    WHITE_ROOK(1002, "white_rook", "♖", Component.literal("§fWhite Rook")),
    WHITE_BISHOP(1003, "white_bishop", "♗", Component.literal("§fWhite Bishop")),
    WHITE_KNIGHT(1004, "white_knight", "♘", Component.literal("§fWhite Knight")),
    WHITE_PAWN(1005, "white_pawn", "♙", Component.literal("§fWhite Pawn")),

    // Black pieces (1010-1015)
    BLACK_KING(1010, "black_king", "♚", Component.literal("§8Black King")),
    BLACK_QUEEN(1011, "black_queen", "♛", Component.literal("§8Black Queen")),
    BLACK_ROOK(1012, "black_rook", "♜", Component.literal("§8Black Rook")),
    BLACK_BISHOP(1013, "black_bishop", "♝", Component.literal("§8Black Bishop")),
    BLACK_KNIGHT(1014, "black_knight", "♞", Component.literal("§8Black Knight")),
    BLACK_PAWN(1015, "black_pawn", "♟", Component.literal("§8Black Pawn"));

    private final int modelData;
    private final String modelName;
    private final String symbol;
    private final Component displayName;

    ChessPiece(int modelData, String modelName, String symbol, Component displayName) {
        this.modelData = modelData;
        this.modelName = modelName;
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public boolean isWhite() {
        return this.ordinal() < 6;
    }

    public boolean isBlack() {
        return !isWhite();
    }

    public PieceType getType() {
        return PieceType.values()[this.ordinal() % 6];
    }

    public static ChessPiece fromColorAndType(PieceColor color, PieceType type) {
        int offset = color == PieceColor.WHITE ? 0 : 6;
        return values()[offset + type.ordinal()];
    }

    public int getModelData() {
        return modelData;
    }

    public String getModelName() {
        return modelName;
    }

    public String getSymbol() {
        return symbol;
    }

    public Component getDisplayName() {
        return displayName;
    }
}
