package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;

import java.util.*;

public class ChessBoard {
    private ChessPiece[] board = new ChessPiece[64];
    private PieceColor currentTurn = PieceColor.WHITE;
    private GameState gameState = GameState.WHITE_TURN;

    private boolean whiteKingSideCastle = true;
    private boolean whiteQueenSideCastle = true;
    private boolean blackKingSideCastle = true;
    private boolean blackQueenSideCastle = true;

    private ChessPosition enPassantTarget = null;
    private List<ChessMove> moveHistory = new ArrayList<>();
    private List<String> positionHistoryFenOnly = new ArrayList<>(); // Store only board state for threefold

    private int halfMoveClock = 0;
    private int fullMoveNumber = 1;

    public ChessBoard() {
        setupInitialPosition();
    }

    private void setupInitialPosition() {
        Arrays.fill(board, null);

        board[0] = ChessPiece.WHITE_ROOK; board[1] = ChessPiece.WHITE_KNIGHT; board[2] = ChessPiece.WHITE_BISHOP; board[3] = ChessPiece.WHITE_QUEEN; board[4] = ChessPiece.WHITE_KING; board[5] = ChessPiece.WHITE_BISHOP; board[6] = ChessPiece.WHITE_KNIGHT; board[7] = ChessPiece.WHITE_ROOK;
        for (int i = 8; i < 16; i++) board[i] = ChessPiece.WHITE_PAWN;
        for (int i = 48; i < 56; i++) board[i] = ChessPiece.BLACK_PAWN;
        board[56] = ChessPiece.BLACK_ROOK; board[57] = ChessPiece.BLACK_KNIGHT; board[58] = ChessPiece.BLACK_BISHOP; board[59] = ChessPiece.BLACK_QUEEN; board[60] = ChessPiece.BLACK_KING; board[61] = ChessPiece.BLACK_BISHOP; board[62] = ChessPiece.BLACK_KNIGHT; board[63] = ChessPiece.BLACK_ROOK;

        positionHistoryFenOnly.add(boardToFENBoardPart());
    }

    public ChessPiece getPiece(ChessPosition pos) {
        if (pos == null || !pos.isValid()) return null;
        return board[pos.toIndex()];
    }

    public ChessPiece getPiece(int index) {
        if (index < 0 || index >= 64) return null;
        return board[index];
    }

    public void setPiece(ChessPosition pos, ChessPiece piece) {
        if (pos != null && pos.isValid()) {
            board[pos.toIndex()] = piece;
        }
    }

    public boolean makeMove(ChessMove move) {
        // Assumes move is already validated as legal by ChessGame calling getLegalMoves
        ChessPiece movingPiece = getPiece(move.from);
        ChessPiece capturedPiece = getPiece(move.to); // Could be null

        // FIXED: Prevent king capture - this should never happen in legal chess
        if (capturedPiece != null && capturedPiece.getType() == PieceType.KING) {
            throw new IllegalStateException("Illegal move: Cannot capture the king! This should never happen in legal chess.");
        }

        // Handle en passant capture: remove the actual captured pawn
        if (move.isEnPassant) {
            ChessPosition capturedPawnPos = new ChessPosition(move.to.file, move.from.rank);
            setPiece(capturedPawnPos, null);
        }

        // Make the move on the board
        setPiece(move.to, movingPiece);
        setPiece(move.from, null);

        // Handle castling: move the rook
        if (move.isCastling) {
            if (move.to.file == 6) { // King-side
                ChessPosition rookFrom = new ChessPosition(7, move.from.rank);
                ChessPosition rookTo = new ChessPosition(5, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            } else { // Queen-side (move.to.file == 2)
                ChessPosition rookFrom = new ChessPosition(0, move.from.rank);
                ChessPosition rookTo = new ChessPosition(3, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            }
        }

        // Handle promotion
        if (move.promotionPiece != null) {
            setPiece(move.to, ChessPiece.fromColorAndType(currentTurn, move.promotionPiece));
        }

        updateCastlingRights(move, movingPiece);
        updateEnPassantTarget(move, movingPiece);

        if (capturedPiece != null || (movingPiece != null && movingPiece.getType() == PieceType.PAWN)) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        if (currentTurn == PieceColor.BLACK) {
            fullMoveNumber++;
        }

        currentTurn = currentTurn.opposite();
        moveHistory.add(move);
        positionHistoryFenOnly.add(boardToFENBoardPart());
        updateGameState();
        return true;
    }


    private void updateCastlingRights(ChessMove move, ChessPiece movingPiece) {
        if (movingPiece == null) return;

        if (movingPiece.getType() == PieceType.KING) {
            if (movingPiece.isWhite()) {
                whiteKingSideCastle = false;
                whiteQueenSideCastle = false;
            } else {
                blackKingSideCastle = false;
                blackQueenSideCastle = false;
            }
        }

        if (movingPiece.getType() == PieceType.ROOK) {
            if (movingPiece.isWhite()) {
                if (move.from.equals(new ChessPosition(0, 0))) whiteQueenSideCastle = false;
                if (move.from.equals(new ChessPosition(7, 0))) whiteKingSideCastle = false;
            } else {
                if (move.from.equals(new ChessPosition(0, 7))) blackQueenSideCastle = false;
                if (move.from.equals(new ChessPosition(7, 7))) blackKingSideCastle = false;
            }
        }

        // If a rook is captured on its starting square
        if (move.to.equals(new ChessPosition(0,0))) whiteQueenSideCastle = false;
        if (move.to.equals(new ChessPosition(7,0))) whiteKingSideCastle = false;
        if (move.to.equals(new ChessPosition(0,7))) blackQueenSideCastle = false;
        if (move.to.equals(new ChessPosition(7,7))) blackKingSideCastle = false;
    }

    private void updateEnPassantTarget(ChessMove move, ChessPiece movingPiece) {
        enPassantTarget = null; // Reset en passant target by default
        if (movingPiece != null && movingPiece.getType() == PieceType.PAWN && Math.abs(move.to.rank - move.from.rank) == 2) {
            enPassantTarget = new ChessPosition(move.from.file, (move.from.rank + move.to.rank) / 2);
        }
    }

    public boolean isLegalMove(ChessMove move) {
        ChessPiece piece = getPiece(move.from);
        if (piece == null) return false;
        if ((piece.isWhite() && currentTurn != PieceColor.WHITE) || (piece.isBlack() && currentTurn != PieceColor.BLACK)) return false;

        if (!isPseudoLegalMove(move, piece)) return false;

        // For castling, ensure king doesn't pass through check
        if (move.isCastling) {
            int rank = move.from.rank;
            int kingFile = move.from.file;
            int rookFile = (move.to.file == 6) ? 7 : 0;
            int kingToFile = move.to.file;
            int kingPassFile = (kingFile + kingToFile) / 2;

            if (isSquareAttacked(new ChessPosition(kingFile, rank), currentTurn.opposite())) return false; // King is in check
            if (isSquareAttacked(new ChessPosition(kingPassFile, rank), currentTurn.opposite())) return false; // King passes through check
            // Final square check is handled by the general check below
        }


        ChessBoard tempBoard = this.copy();
        tempBoard.makeUncheckedMove(move); // makeUncheckedMove is a simplified move for testing
        return !tempBoard.isInCheck(currentTurn);
    }

    private boolean isPseudoLegalMove(ChessMove move, ChessPiece piece) {
        if (piece == null) return false;

        ChessPiece targetPiece = getPiece(move.to);
        if (targetPiece != null && piece.isWhite() == targetPiece.isWhite()) {
            return false;
        }

        // FIXED: Prevent king capture in pseudo-legal move generation
        if (targetPiece != null && targetPiece.getType() == PieceType.KING) {
            return false; // Cannot capture king
        }


        return switch (piece.getType()) {
            case PAWN -> isValidPawnMove(move, piece);
            case ROOK -> isValidRookMove(move);
            case KNIGHT -> isValidKnightMove(move);
            case BISHOP -> isValidBishopMove(move);
            case QUEEN -> isValidQueenMove(move);
            case KING -> isValidKingMove(move, piece);
        };
    }


    private boolean isValidPawnMove(ChessMove move, ChessPiece piece) {
        int direction = piece.isWhite() ? 1 : -1;
        int startRank = piece.isWhite() ? 1 : 6;
        int fileDiff = move.to.file - move.from.file;
        int rankDiff = move.to.rank - move.from.rank;

        if (fileDiff == 0) { // Forward move
            if (getPiece(move.to) != null) return false; // Path blocked
            if (rankDiff == direction) return true; // One square
            if (rankDiff == 2 * direction && move.from.rank == startRank && getPiece(new ChessPosition(move.from.file, move.from.rank + direction)) == null) return true; // Two squares
        } else if (Math.abs(fileDiff) == 1 && rankDiff == direction) { // Capture
            if (getPiece(move.to) != null && getPiece(move.to).isWhite() != piece.isWhite()) return true; // Regular capture
            if (move.to.equals(enPassantTarget)) return true; // En passant
        }
        return false;
    }

    private boolean isValidRookMove(ChessMove move) {
        return (move.from.file == move.to.file || move.from.rank == move.to.rank) && isPathClear(move.from, move.to);
    }

    private boolean isValidKnightMove(ChessMove move) {
        int fileDiff = Math.abs(move.to.file - move.from.file);
        int rankDiff = Math.abs(move.to.rank - move.from.rank);
        return (fileDiff == 1 && rankDiff == 2) || (fileDiff == 2 && rankDiff == 1);
    }

    private boolean isValidBishopMove(ChessMove move) {
        return Math.abs(move.to.file - move.from.file) == Math.abs(move.to.rank - move.from.rank) && isPathClear(move.from, move.to);
    }

    private boolean isValidQueenMove(ChessMove move) {
        return isValidRookMove(move) || isValidBishopMove(move);
    }

    private boolean isValidKingMove(ChessMove move, ChessPiece piece) {
        int fileDiff = Math.abs(move.to.file - move.from.file);
        int rankDiff = Math.abs(move.to.rank - move.from.rank);

        if (fileDiff <= 1 && rankDiff <= 1) return true; // Normal move

        // Castling (pre-check path and rights, isLegalMove will do full check validation)
        if (rankDiff == 0 && fileDiff == 2 && !isInCheck(piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK)) {
            boolean kingSide = move.to.file == 6;
            boolean queenSide = move.to.file == 2;

            if (piece.isWhite() && move.from.rank == 0) {
                if (kingSide && whiteKingSideCastle && isPathClear(move.from, new ChessPosition(7, 0))) return true;
                if (queenSide && whiteQueenSideCastle && isPathClear(move.from, new ChessPosition(0, 0))) return true;
            } else if (piece.isBlack() && move.from.rank == 7) {
                if (kingSide && blackKingSideCastle && isPathClear(move.from, new ChessPosition(7, 7))) return true;
                if (queenSide && blackQueenSideCastle && isPathClear(move.from, new ChessPosition(0, 7))) return true;
            }
        }
        return false;
    }


    private boolean isPathClear(ChessPosition from, ChessPosition to) {
        int dFile = Integer.compare(to.file, from.file);
        int dRank = Integer.compare(to.rank, from.rank);
        int currentFile = from.file + dFile;
        int currentRank = from.rank + dRank;

        while (currentFile != to.file || currentRank != to.rank) {
            ChessPosition checkPos = new ChessPosition(currentFile, currentRank);
            ChessPiece blockingPiece = getPiece(checkPos);
            if (blockingPiece != null) {
                // Debug path blocking
                if (from.file == to.file || from.rank == to.rank || Math.abs(to.file - from.file) == Math.abs(to.rank - from.rank)) {
                    System.out.println("[Chess Debug] Path blocked from " + from.toNotation() + " to " + to.toNotation() + " by " + blockingPiece + " at " + checkPos.toNotation());
                }
                return false;
            }
            currentFile += dFile;
            currentRank += dRank;
        }
        return true;
    }

    public boolean isInCheck(PieceColor color) {
        ChessPosition kingPos = findKing(color);
        if (kingPos == null) {
            System.err.println("[Chess Debug] No king found for color " + color);
            return true; // Should not happen in a valid game
        }
        
        // Check all opponent pieces to see if any can attack the king
        PieceColor attackingColor = color.opposite();
        
        // Check for pawn attacks
        if (isAttackedByPawn(kingPos, attackingColor)) {
            System.out.println("[Chess Debug] " + color + " king at " + kingPos.toNotation() + " is in check by pawn!");
            return true;
        }
        
        // Check for knight attacks
        if (isAttackedByKnight(kingPos, attackingColor)) {
            System.out.println("[Chess Debug] " + color + " king at " + kingPos.toNotation() + " is in check by knight!");
            return true;
        }
        
        // Check for sliding piece attacks (bishop, rook, queen)
        if (isAttackedBySlidingPiece(kingPos, attackingColor)) {
            System.out.println("[Chess Debug] " + color + " king at " + kingPos.toNotation() + " is in check by sliding piece!");
            return true;
        }
        
        // Check for king attacks (adjacent squares)
        if (isAttackedByKing(kingPos, attackingColor)) {
            System.out.println("[Chess Debug] " + color + " king at " + kingPos.toNotation() + " is in check by king!");
            return true;
        }
        
        return false;
    }

    public boolean isSquareAttacked(ChessPosition pos, PieceColor attackingColor) {
        // Check if any piece of the attacking color can attack this square
        return isAttackedByPawn(pos, attackingColor) ||
               isAttackedByKnight(pos, attackingColor) ||
               isAttackedBySlidingPiece(pos, attackingColor) ||
               isAttackedByKing(pos, attackingColor);
    }
    
    private boolean isAttackedByPawn(ChessPosition pos, PieceColor attackingColor) {
        int direction = attackingColor == PieceColor.WHITE ? -1 : 1; // Reverse direction for attack check
        
        // Check the two squares from which a pawn could attack
        ChessPosition[] attackSquares = {
            new ChessPosition(pos.file - 1, pos.rank + direction),
            new ChessPosition(pos.file + 1, pos.rank + direction)
        };
        
        for (ChessPosition attackFrom : attackSquares) {
            if (!attackFrom.isValid()) continue;
            
            ChessPiece piece = getPiece(attackFrom);
            if (piece != null && piece.getType() == PieceType.PAWN && 
                ((attackingColor == PieceColor.WHITE && piece.isWhite()) || 
                 (attackingColor == PieceColor.BLACK && piece.isBlack()))) {
                System.out.println("[Chess Debug] Pawn at " + attackFrom.toNotation() + " attacks " + pos.toNotation());
                return true;
            }
        }
        return false;
    }
    
    private boolean isAttackedByKnight(ChessPosition pos, PieceColor attackingColor) {
        // All possible knight move offsets
        int[][] knightMoves = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
            {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };
        
        for (int[] move : knightMoves) {
            ChessPosition attackFrom = new ChessPosition(pos.file + move[0], pos.rank + move[1]);
            if (!attackFrom.isValid()) continue;
            
            ChessPiece piece = getPiece(attackFrom);
            if (piece != null && piece.getType() == PieceType.KNIGHT && 
                ((attackingColor == PieceColor.WHITE && piece.isWhite()) || 
                 (attackingColor == PieceColor.BLACK && piece.isBlack()))) {
                System.out.println("[Chess Debug] Knight at " + attackFrom.toNotation() + " attacks " + pos.toNotation());
                return true;
            }
        }
        return false;
    }
    
    private boolean isAttackedBySlidingPiece(ChessPosition pos, PieceColor attackingColor) {
        // Check all 8 directions for sliding pieces
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
            {0, 1}, {1, -1}, {1, 0}, {1, 1}
        };
        
        for (int[] dir : directions) {
            ChessPosition current = new ChessPosition(pos.file + dir[0], pos.rank + dir[1]);
            
            while (current.isValid()) {
                ChessPiece piece = getPiece(current);
                if (piece != null) {
                    if ((attackingColor == PieceColor.WHITE && piece.isWhite()) || 
                        (attackingColor == PieceColor.BLACK && piece.isBlack())) {
                        // Check if this piece can attack along this direction
                        boolean isDiagonal = (dir[0] != 0 && dir[1] != 0);
                        boolean isStraight = (dir[0] == 0 || dir[1] == 0);
                        
                        if ((piece.getType() == PieceType.BISHOP && isDiagonal) ||
                            (piece.getType() == PieceType.ROOK && isStraight) ||
                            (piece.getType() == PieceType.QUEEN)) {
                            System.out.println("[Chess Debug] " + piece.getType() + " at " + current.toNotation() + " attacks " + pos.toNotation());
                            return true;
                        }
                    }
                    break; // Piece blocks further checking in this direction
                }
                current = new ChessPosition(current.file + dir[0], current.rank + dir[1]);
            }
        }
        return false;
    }
    
    private boolean isAttackedByKing(ChessPosition pos, PieceColor attackingColor) {
        // Check all 8 adjacent squares for enemy king
        for (int dFile = -1; dFile <= 1; dFile++) {
            for (int dRank = -1; dRank <= 1; dRank++) {
                if (dFile == 0 && dRank == 0) continue;
                
                ChessPosition attackFrom = new ChessPosition(pos.file + dFile, pos.rank + dRank);
                if (!attackFrom.isValid()) continue;
                
                ChessPiece piece = getPiece(attackFrom);
                if (piece != null && piece.getType() == PieceType.KING && 
                    ((attackingColor == PieceColor.WHITE && piece.isWhite()) || 
                     (attackingColor == PieceColor.BLACK && piece.isBlack()))) {
                    System.out.println("[Chess Debug] King at " + attackFrom.toNotation() + " attacks " + pos.toNotation());
                    return true;
                }
            }
        }
        return false;
    }


    public ChessPosition findKing(PieceColor color) {
        ChessPiece kingToFind = (color == PieceColor.WHITE) ? ChessPiece.WHITE_KING : ChessPiece.BLACK_KING;
        for (int i = 0; i < 64; i++) {
            if (board[i] == kingToFind) return ChessPosition.fromIndex(i);
        }
        return null;
    }

    public List<ChessMove> getLegalMoves() {
        List<ChessMove> allPseudoLegalMoves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            ChessPiece piece = board[i];
            if (piece != null && ((currentTurn == PieceColor.WHITE && piece.isWhite()) || (currentTurn == PieceColor.BLACK && piece.isBlack()))) {
                ChessPosition from = ChessPosition.fromIndex(i);
                generatePseudoLegalMovesForPiece(from, piece, allPseudoLegalMoves);
            }
        }

        List<ChessMove> legalMoves = new ArrayList<>();
        for (ChessMove pseudoMove : allPseudoLegalMoves) {
            // Add flags like isCheck and isCheckmate after making the move on a temp board
            ChessBoard tempBoard = this.copy();
            tempBoard.makeUncheckedMove(pseudoMove); // Apply the basic move

            if (!tempBoard.isInCheck(currentTurn)) { // If own king is NOT in check after this move
                boolean isCheck = tempBoard.isInCheck(currentTurn.opposite());
                boolean isCheckmate = false;
                if (isCheck) {
                    // Check for checkmate
                    tempBoard.currentTurn = currentTurn.opposite(); // Switch turn to opponent for their legal moves
                    if (tempBoard.getLegalMoves().isEmpty()) {
                        isCheckmate = true;
                    }
                }

                ChessMove finalMove = new ChessMove(
                        pseudoMove.from, pseudoMove.to, pseudoMove.promotionPiece,
                        pseudoMove.isCapture, pseudoMove.isEnPassant, pseudoMove.isCastling,
                        isCheck, isCheckmate
                );
                legalMoves.add(finalMove);
            }
        }
        return legalMoves;
    }


    private void generatePseudoLegalMovesForPiece(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        switch (piece.getType()) {
            case PAWN: generatePawnMoves(from, piece, moves); break;
            case ROOK: generateSlidingPieceMoves(from, piece, new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}, 7, moves); break;
            case KNIGHT: generateKnightMoves(from, piece, moves); break;
            case BISHOP: generateSlidingPieceMoves(from, piece, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}, 7, moves); break;
            case QUEEN: generateSlidingPieceMoves(from, piece, new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}, 7, moves); break;
            case KING: generateKingMoves(from, piece, moves); break;
        }
    }

    private void generatePawnMoves(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        int direction = piece.isWhite() ? 1 : -1;
        int startRank = piece.isWhite() ? 1 : 6;
        int promotionRank = piece.isWhite() ? 7 : 0;

        // Forward one square
        ChessPosition to1 = new ChessPosition(from.file, from.rank + direction);
        if (to1.isValid() && getPiece(to1) == null) {
            addPawnMove(from, to1, piece, null, false, false, moves, promotionRank);
            // Forward two squares
            if (from.rank == startRank) {
                ChessPosition to2 = new ChessPosition(from.file, from.rank + 2 * direction);
                if (to2.isValid() && getPiece(to2) == null) {
                    moves.add(new ChessMove(from, to2));
                }
            }
        }
        // Captures
        for (int fileOffset : new int[]{-1, 1}) {
            ChessPosition toCapture = new ChessPosition(from.file + fileOffset, from.rank + direction);
            if (toCapture.isValid()) {
                ChessPiece target = getPiece(toCapture);
                if (target != null && target.isWhite() != piece.isWhite()) {
                    // FIXED: Prevent king capture in pawn moves
                    if (target.getType() != PieceType.KING) {
                        addPawnMove(from, toCapture, piece, target, true, false, moves, promotionRank);
                    }
                } else if (toCapture.equals(enPassantTarget)) { // En passant
                    addPawnMove(from, toCapture, piece, null, true, true, moves, promotionRank);
                }
            }
        }
    }

    private void addPawnMove(ChessPosition from, ChessPosition to, ChessPiece piece, ChessPiece captured, boolean isCapture, boolean isEnPassant, List<ChessMove> moves, int promotionRank) {
        if (to.rank == promotionRank) {
            moves.add(new ChessMove(from, to, PieceType.QUEEN, isCapture, isEnPassant, false, false, false));
            moves.add(new ChessMove(from, to, PieceType.ROOK, isCapture, isEnPassant, false, false, false));
            moves.add(new ChessMove(from, to, PieceType.BISHOP, isCapture, isEnPassant, false, false, false));
            moves.add(new ChessMove(from, to, PieceType.KNIGHT, isCapture, isEnPassant, false, false, false));
        } else {
            moves.add(new ChessMove(from, to, null, isCapture, isEnPassant, false, false, false));
        }
    }


    private void generateSlidingPieceMoves(ChessPosition from, ChessPiece piece, int[][] directions, int maxDistance, List<ChessMove> moves) {
        for (int[] d : directions) {
            for (int i = 1; i <= maxDistance; i++) {
                ChessPosition to = new ChessPosition(from.file + d[0] * i, from.rank + d[1] * i);
                if (!to.isValid()) break;
                ChessPiece target = getPiece(to);
                if (target == null) {
                    moves.add(new ChessMove(from, to));
                } else {
                    if (target.isWhite() != piece.isWhite() && target.getType() != PieceType.KING) {
                        // FIXED: Prevent king capture in sliding piece moves
                        moves.add(new ChessMove(from, to, null, true, false, false, false, false));
                    }
                    break;
                }
            }
        }
    }

    private void generateKnightMoves(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        int[][] offsets = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};
        for (int[] offset : offsets) {
            ChessPosition to = new ChessPosition(from.file + offset[0], from.rank + offset[1]);
            if (to.isValid()) {
                ChessPiece target = getPiece(to);
                if (target == null || (target.isWhite() != piece.isWhite() && target.getType() != PieceType.KING)) {
                    // FIXED: Prevent king capture in knight moves
                    moves.add(new ChessMove(from, to, null, target != null, false, false, false, false));
                }
            }
        }
    }

    private void generateKingMoves(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        // Normal moves
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) continue;
                ChessPosition to = new ChessPosition(from.file + df, from.rank + dr);
                if (to.isValid()) {
                    ChessPiece target = getPiece(to);
                    if (target == null || (target.isWhite() != piece.isWhite() && target.getType() != PieceType.KING)) {
                        // FIXED: Prevent king capture in king moves
                        moves.add(new ChessMove(from, to, null, target != null, false, false, false, false));
                    }
                }
            }
        }
        // Castling
        if (isInCheck(currentTurn)) return; // Cannot castle out of check

        int rank = piece.isWhite() ? 0 : 7;
        if (from.file == 4 && from.rank == rank) { // King is on its original square
            // King-side
            boolean canKingSide = piece.isWhite() ? whiteKingSideCastle : blackKingSideCastle;
            if (canKingSide && getPiece(new ChessPosition(5, rank)) == null && getPiece(new ChessPosition(6, rank)) == null) {
                if (!isSquareAttacked(new ChessPosition(5, rank), piece.isWhite() ? PieceColor.BLACK : PieceColor.WHITE)) {
                    moves.add(new ChessMove(from, new ChessPosition(6, rank), null, false, false, true, false, false));
                }
            }
            // Queen-side
            boolean canQueenSide = piece.isWhite() ? whiteQueenSideCastle : blackQueenSideCastle;
            if (canQueenSide && getPiece(new ChessPosition(3, rank)) == null && getPiece(new ChessPosition(2, rank)) == null && getPiece(new ChessPosition(1, rank)) == null) {
                if (!isSquareAttacked(new ChessPosition(3, rank), piece.isWhite() ? PieceColor.BLACK : PieceColor.WHITE)) {
                    moves.add(new ChessMove(from, new ChessPosition(2, rank), null, false, false, true, false, false));
                }
            }
        }
    }

    private void updateGameState() {
        boolean inCheck = isInCheck(currentTurn);
        List<ChessMove> legalMovesCurrentPlayer = getLegalMovesForColor(currentTurn); // Need a specific method for this to avoid recursion

        System.out.println("[Chess Debug] UpdateGameState: turn=" + currentTurn + ", inCheck=" + inCheck + ", legalMoves=" + legalMovesCurrentPlayer.size());

        if (legalMovesCurrentPlayer.isEmpty()) {
            if (inCheck) {
                gameState = (currentTurn == PieceColor.WHITE) ? GameState.CHECKMATE_BLACK_WINS : GameState.CHECKMATE_WHITE_WINS;
                System.out.println("[Chess Debug] CHECKMATE detected! Winner: " + (currentTurn == PieceColor.WHITE ? "Black" : "White"));
            } else {
                gameState = GameState.STALEMATE;
                System.out.println("[Chess Debug] STALEMATE detected!");
            }
        } else if (inCheck) {
            gameState = (currentTurn == PieceColor.WHITE) ? GameState.CHECK_WHITE : GameState.CHECK_BLACK;
            System.out.println("[Chess Debug] CHECK detected for " + currentTurn);
        } else {
            gameState = (currentTurn == PieceColor.WHITE) ? GameState.WHITE_TURN : GameState.BLACK_TURN;
        }

        if (gameState == GameState.WHITE_TURN || gameState == GameState.BLACK_TURN || gameState == GameState.CHECK_WHITE || gameState == GameState.CHECK_BLACK) {
            if (halfMoveClock >= 100) gameState = GameState.DRAW_FIFTY_MOVE;
            else if (isThreefoldRepetition()) gameState = GameState.DRAW_THREEFOLD;
            else if (isInsufficientMaterial()) gameState = GameState.DRAW_INSUFFICIENT;
        }
    }

    private List<ChessMove> getLegalMovesForColor(PieceColor color) {
        // Temporarily set currentTurn to the color to generate its moves, then revert.
        PieceColor originalTurn = this.currentTurn;
        this.currentTurn = color;
        List<ChessMove> legalMoves = getLegalMoves(); // This will generate for the new currentTurn
        this.currentTurn = originalTurn; // Revert
        return legalMoves;
    }


    private boolean isThreefoldRepetition() {
        String currentBoardFen = boardToFENBoardPart();
        long count = positionHistoryFenOnly.stream().filter(fen -> fen.equals(currentBoardFen)).count();
        return count >= 3;
    }

    private boolean isInsufficientMaterial() {
        List<ChessPiece> piecesOnBoard = new ArrayList<>();
        for (ChessPiece p : board) {
            if (p != null) piecesOnBoard.add(p);
        }

        if (piecesOnBoard.size() <= 2) return true; // K vs K

        long whiteKnights = piecesOnBoard.stream().filter(p -> p == ChessPiece.WHITE_KNIGHT).count();
        long blackKnights = piecesOnBoard.stream().filter(p -> p == ChessPiece.BLACK_KNIGHT).count();
        long whiteBishops = piecesOnBoard.stream().filter(p -> p == ChessPiece.WHITE_BISHOP).count();
        long blackBishops = piecesOnBoard.stream().filter(p -> p == ChessPiece.BLACK_BISHOP).count();
        long whiteHeavy = piecesOnBoard.stream().filter(p -> p == ChessPiece.WHITE_QUEEN || p == ChessPiece.WHITE_ROOK || p == ChessPiece.WHITE_PAWN).count();
        long blackHeavy = piecesOnBoard.stream().filter(p -> p == ChessPiece.BLACK_QUEEN || p == ChessPiece.BLACK_ROOK || p == ChessPiece.BLACK_PAWN).count();

        if (whiteHeavy > 0 || blackHeavy > 0) return false; // Pawns or heavy pieces exist

        // K vs K+N or K vs K+B
        if (whiteKnights + whiteBishops <= 1 && blackKnights + blackBishops == 0) return true;
        if (blackKnights + blackBishops <= 1 && whiteKnights + whiteBishops == 0) return true;

        // K+B vs K+B (bishops on same color) - This is complex, simplified check:
        if (whiteBishops == 1 && blackBishops == 1 && whiteKnights == 0 && blackKnights == 0) {
            ChessPosition wBishopPos = null, bBishopPos = null;
            for(int i=0; i<64; ++i) {
                if (board[i] == ChessPiece.WHITE_BISHOP) wBishopPos = ChessPosition.fromIndex(i);
                if (board[i] == ChessPiece.BLACK_BISHOP) bBishopPos = ChessPosition.fromIndex(i);
            }
            if (wBishopPos != null && bBishopPos != null) {
                boolean wBishopLight = (wBishopPos.file + wBishopPos.rank) % 2 == 0;
                boolean bBishopLight = (bBishopPos.file + bBishopPos.rank) % 2 == 0;
                if (wBishopLight == bBishopLight) return true;
            }
        }


        return false;
    }

    private void makeUncheckedMove(ChessMove move) {
        ChessPiece piece = getPiece(move.from);
        setPiece(move.to, piece);
        setPiece(move.from, null);
        if (move.isEnPassant) setPiece(new ChessPosition(move.to.file, move.from.rank), null);
        if (move.isCastling) {
            if (move.to.file == 6) {
                setPiece(new ChessPosition(5, move.from.rank), getPiece(new ChessPosition(7, move.from.rank)));
                setPiece(new ChessPosition(7, move.from.rank), null);
            } else {
                setPiece(new ChessPosition(3, move.from.rank), getPiece(new ChessPosition(0, move.from.rank)));
                setPiece(new ChessPosition(0, move.from.rank), null);
            }
        }
        if (move.promotionPiece != null && piece != null) {
            setPiece(move.to, ChessPiece.fromColorAndType(piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK, move.promotionPiece));
        }
    }

    public ChessBoard copy() {
        ChessBoard copy = new ChessBoard(); // This will call setupInitialPosition
        // Manual copy of state:
        System.arraycopy(this.board, 0, copy.board, 0, 64);
        copy.currentTurn = this.currentTurn;
        copy.gameState = this.gameState;
        copy.whiteKingSideCastle = this.whiteKingSideCastle;
        copy.whiteQueenSideCastle = this.whiteQueenSideCastle;
        copy.blackKingSideCastle = this.blackKingSideCastle;
        copy.blackQueenSideCastle = this.blackQueenSideCastle;
        copy.enPassantTarget = this.enPassantTarget; // This is a ChessPosition, immutable
        copy.halfMoveClock = this.halfMoveClock;
        copy.fullMoveNumber = this.fullMoveNumber;
        copy.moveHistory = new ArrayList<>(this.moveHistory); // ChessMove is immutable
        copy.positionHistoryFenOnly = new ArrayList<>(this.positionHistoryFenOnly); // String is immutable
        return copy;
    }

    private String boardToFENBoardPart() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                ChessPiece piece = getPiece(new ChessPosition(file, rank));
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) fen.append(emptyCount);
                    emptyCount = 0;
                    fen.append(pieceToFENChar(piece));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (rank > 0) fen.append('/');
        }
        return fen.toString();
    }

    public String toFEN() {
        String boardPart = boardToFENBoardPart();
        String turnPart = (currentTurn == PieceColor.WHITE ? "w" : "b");
        String castlingPart = "";
        if (whiteKingSideCastle) castlingPart += "K";
        if (whiteQueenSideCastle) castlingPart += "Q";
        if (blackKingSideCastle) castlingPart += "k";
        if (blackQueenSideCastle) castlingPart += "q";
        if (castlingPart.isEmpty()) castlingPart = "-";
        String enPassantPart = (enPassantTarget != null ? enPassantTarget.toNotation() : "-");

        return String.format("%s %s %s %s %d %d", boardPart, turnPart, castlingPart, enPassantPart, halfMoveClock, fullMoveNumber);
    }


    private char pieceToFENChar(ChessPiece piece) {
        if (piece == null) return ' '; // Should not happen if called correctly
        char c = switch (piece.getType()) {
            case KING -> 'k';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            case PAWN -> 'p';
        };
        return piece.isWhite() ? Character.toUpperCase(c) : c;
    }

    public PieceColor getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(PieceColor turn) { this.currentTurn = turn; }
    public GameState getGameState() { return gameState; }
    public List<ChessMove> getMoveHistory() { return Collections.unmodifiableList(moveHistory); }
    public int getHalfMoveClock() { return halfMoveClock; }
    public int getFullMoveNumber() { return fullMoveNumber; }
    public ChessPosition getEnPassantTarget() { return enPassantTarget; }

}