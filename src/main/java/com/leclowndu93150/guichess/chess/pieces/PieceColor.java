package com.leclowndu93150.guichess.chess.pieces;

public enum PieceColor {
    WHITE, BLACK;

    public PieceColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
