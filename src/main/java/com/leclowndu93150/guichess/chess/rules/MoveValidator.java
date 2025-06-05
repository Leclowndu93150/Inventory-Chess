package com.leclowndu93150.guichess.chess.rules;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.game.core.ChessBoard;
import com.leclowndu93150.guichess.game.core.ChessGame;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/**
 * Enhanced move validation system with detailed move analysis and feedback.
 * 
 * <p>Provides comprehensive move validation with categorized feedback for illegal moves,
 * move type detection, and game state validation. Used primarily for user input validation
 * and providing educational feedback.
 * 
 * @author GUIChess
 * @since 1.0
 */
public class MoveValidator {

    /**
     * Result of a move validation operation containing success status, message, and move type.
     */
    public static class MoveResult {
        public final boolean success;
        public final String message;
        public final MoveType type;

        public MoveResult(boolean success, String message, MoveType type) {
            this.success = success;
            this.message = message;
            this.type = type;
        }

        public static MoveResult success(MoveType type) {
            return new MoveResult(true, "", type);
        }

        public static MoveResult failure(String message) {
            return new MoveResult(false, message, MoveType.INVALID);
        }
    }

    public enum MoveType {
        PIECE_SELECTION,
        NORMAL_MOVE,
        CAPTURE,
        CASTLING,
        EN_PASSANT,
        PROMOTION,
        INVALID
    }

    public static MoveResult validateSelection(ChessGame game, ServerPlayer player, ChessPosition position) {
        if (!game.isGameActive() && !game.isAnalysisMode()) {
            return MoveResult.failure("Game is not active");
        }

        ChessBoard board = game.getBoard();
        ChessPiece piece = board.getPiece(position);
        PieceColor playerColor = game.getPlayerColor(player);

        if (game.isAnalysisMode()) {
            if (piece != null) {
                return MoveResult.success(MoveType.PIECE_SELECTION);
            }
            if (game.getSelectedSquare() != null && game.getValidMoves().contains(position)) {
                return validateMove(game, game.getSelectedSquare(), position);
            }
            return MoveResult.failure("No piece to select or invalid move");
        }

        if (!game.isPlayerTurn(player)) {
            return MoveResult.failure("It's not your turn");
        }

        if (piece != null && isPieceOwnedByPlayer(piece, playerColor)) {
            return MoveResult.success(MoveType.PIECE_SELECTION);
        }

        if (game.getSelectedSquare() != null && game.getValidMoves().contains(position)) {
            return validateMove(game, game.getSelectedSquare(), position);
        }

        return MoveResult.failure("Select one of your pieces or make a valid move");
    }

    private static MoveResult validateMove(ChessGame game, ChessPosition from, ChessPosition to) {
        ChessBoard board = game.getBoard();
        ChessPiece movingPiece = board.getPiece(from);
        ChessPiece targetPiece = board.getPiece(to);

        if (movingPiece == null) {
            return MoveResult.failure("No piece to move");
        }

        List<ChessMove> legalMoves = board.getLegalMoves();
        ChessMove matchingMove = findMatchingMove(legalMoves, from, to);

        if (matchingMove == null) {
            return MoveResult.failure("Invalid move");
        }

        if (matchingMove.isCastling) {
            return MoveResult.success(MoveType.CASTLING);
        }

        if (matchingMove.isEnPassant) {
            return MoveResult.success(MoveType.EN_PASSANT);
        }

        if (matchingMove.promotionPiece != null) {
            return MoveResult.success(MoveType.PROMOTION);
        }

        if (matchingMove.isCapture || targetPiece != null) {
            return MoveResult.success(MoveType.CAPTURE);
        }

        return MoveResult.success(MoveType.NORMAL_MOVE);
    }

    private static ChessMove findMatchingMove(List<ChessMove> legalMoves, ChessPosition from, ChessPosition to) {
        return legalMoves.stream()
                .filter(move -> move.from.equals(from) && move.to.equals(to))
                .findFirst()
                .orElse(null);
    }

    private static boolean isPieceOwnedByPlayer(ChessPiece piece, PieceColor playerColor) {
        if (piece == null || playerColor == null) return false;
        return (playerColor == PieceColor.WHITE && piece.isWhite()) ||
                (playerColor == PieceColor.BLACK && piece.isBlack());
    }

    public static String getMoveNotation(ChessMove move, ChessBoard board) {
        if (move == null) return "";

        ChessPiece piece = board.getPiece(move.from);
        if (piece == null) return move.from.toNotation() + move.to.toNotation();

        StringBuilder notation = new StringBuilder();

        if (move.isCastling) {
            return move.to.file == 6 ? "O-O" : "O-O-O";
        }

        if (piece.getType() != PieceType.PAWN) {
            notation.append(piece.getType().name().charAt(0));
        }

        notation.append(move.from.toNotation());

        if (move.isCapture) {
            notation.append("x");
        } else {
            notation.append("-");
        }

        notation.append(move.to.toNotation());

        if (move.promotionPiece != null) {
            notation.append("=").append(move.promotionPiece.name().charAt(0));
        }

        if (move.isCheckmate) {
            notation.append("#");
        } else if (move.isCheck) {
            notation.append("+");
        }

        return notation.toString();
    }

    public static Component getMoveFeedback(MoveResult result, ChessPosition position) {
        if (result.success) {
            return switch (result.type) {
                case PIECE_SELECTION -> Component.literal("§aPiece selected at " + position.toNotation());
                case NORMAL_MOVE -> Component.literal("§aMoved to " + position.toNotation());
                case CAPTURE -> Component.literal("§cCaptured piece at " + position.toNotation());
                case CASTLING -> Component.literal("§6Castled");
                case EN_PASSANT -> Component.literal("§eEn passant capture at " + position.toNotation());
                case PROMOTION -> Component.literal("§dPromotion at " + position.toNotation());
                default -> Component.literal("§7Move completed");
            };
        } else {
            return Component.literal("§c" + result.message);
        }
    }

    public static boolean isPromotionMove(ChessGame game, ChessPosition from, ChessPosition to) {
        ChessBoard board = game.getBoard();
        ChessPiece piece = board.getPiece(from);

        if (piece == null || piece.getType() != PieceType.PAWN) {
            return false;
        }

        return (piece.isWhite() && to.rank == 7) || (piece.isBlack() && to.rank == 0);
    }

    public static Set<ChessPosition> getValidMovesFromPosition(ChessGame game, ChessPosition from) {
        return game.getValidMoves();
    }
}