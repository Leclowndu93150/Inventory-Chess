package com.leclowndu93150.guichess.chess.board;

import com.leclowndu93150.guichess.chess.pieces.PieceType;

public class ChessMove {
    public final ChessPosition from;
    public final ChessPosition to;
    public final PieceType promotionPiece;
    public final boolean isCapture;
    public final boolean isEnPassant;
    public final boolean isCastling;
    public final boolean isCheck;
    public final boolean isCheckmate;

    public ChessMove(ChessPosition from, ChessPosition to) {
        this(from, to, null, false, false, false, false, false);
    }

    public ChessMove(ChessPosition from, ChessPosition to, PieceType promotionPiece,
                     boolean isCapture, boolean isEnPassant, boolean isCastling,
                     boolean isCheck, boolean isCheckmate) {
        this.from = from;
        this.to = to;
        this.promotionPiece = promotionPiece;
        this.isCapture = isCapture;
        this.isEnPassant = isEnPassant;
        this.isCastling = isCastling;
        this.isCheck = isCheck;
        this.isCheckmate = isCheckmate;
    }

    public String toNotation() {
        if (isCastling) {
            return to.file == 6 ? "O-O" : "O-O-O";
        }

        String move = from.toNotation() + to.toNotation();
        if (promotionPiece != null) {
            move += "=" + promotionPiece.name().charAt(0);
        }
        if (isCheck) move += "+";
        if (isCheckmate) move += "#";

        return move;
    }
}
