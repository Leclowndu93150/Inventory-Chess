package com.leclowndu93150.guichess.chess.board;

/**
 * Represents a position on the chess board using file-rank coordinates.
 * 
 * Coordinate system:
 * - Files: 0-7 (a-h)
 * - Ranks: 0-7 (1-8)
 * - Index mapping: a1=0, h8=63
 */
public class ChessPosition {
    public final int file; // 0-7 (a-h)
    public final int rank; // 0-7 (1-8)

    public ChessPosition(int file, int rank) {
        this.file = file;
        this.rank = rank;
    }

    public ChessPosition(String notation) {
        this.file = notation.charAt(0) - 'a';
        this.rank = notation.charAt(1) - '1';
    }

    public int toIndex() {
        return rank * 8 + file;
    }

    public static ChessPosition fromIndex(int index) {
        return new ChessPosition(index % 8, index / 8);
    }

    public String toNotation() {
        return "" + (char)('a' + file) + (char)('1' + rank);
    }

    public boolean isValid() {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ChessPosition other)) return false;
        return this.file == other.file && this.rank == other.rank;
    }

    @Override
    public int hashCode() {
        return file * 8 + rank;
    }
}
