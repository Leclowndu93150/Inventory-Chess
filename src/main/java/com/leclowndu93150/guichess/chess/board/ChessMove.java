package com.leclowndu93150.guichess.chess.board;

import com.leclowndu93150.guichess.chess.pieces.PieceType;
import net.minecraft.nbt.CompoundTag;

/**
 * Represents a chess move with all its properties and metadata.
 * Immutable data structure containing move details and game state flags.
 */
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ChessMove other = (ChessMove) obj;
        return from.equals(other.from) && 
               to.equals(other.to) && 
               java.util.Objects.equals(promotionPiece, other.promotionPiece);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(from, to, promotionPiece);
    }

    @Override
    public String toString() {
        return "ChessMove{" + from.toNotation() + "->" + to.toNotation() + 
               (promotionPiece != null ? "=" + promotionPiece : "") + "}";
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("from", from.toNBT());
        tag.put("to", to.toNBT());
        if (promotionPiece != null) {
            tag.putString("promotionPiece", promotionPiece.name());
        }
        tag.putBoolean("isCapture", isCapture);
        tag.putBoolean("isEnPassant", isEnPassant);
        tag.putBoolean("isCastling", isCastling);
        tag.putBoolean("isCheck", isCheck);
        tag.putBoolean("isCheckmate", isCheckmate);
        return tag;
    }

    public static ChessMove fromNBT(CompoundTag tag) {
        ChessPosition from = ChessPosition.fromNBT(tag.getCompound("from"));
        ChessPosition to = ChessPosition.fromNBT(tag.getCompound("to"));
        PieceType promotionPiece = tag.contains("promotionPiece") ? 
            PieceType.valueOf(tag.getString("promotionPiece")) : null;
        
        return new ChessMove(
            from, to, promotionPiece,
            tag.getBoolean("isCapture"),
            tag.getBoolean("isEnPassant"),
            tag.getBoolean("isCastling"),
            tag.getBoolean("isCheck"),
            tag.getBoolean("isCheckmate")
        );
    }
}
