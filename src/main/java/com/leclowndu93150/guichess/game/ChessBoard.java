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

    // Castling rights
    private boolean whiteKingSideCastle = true;
    private boolean whiteQueenSideCastle = true;
    private boolean blackKingSideCastle = true;
    private boolean blackQueenSideCastle = true;

    // En passant
    private ChessPosition enPassantTarget = null;

    // Move history
    private List<ChessMove> moveHistory = new ArrayList<>();
    private List<String> positionHistory = new ArrayList<>();

    // Fifty move rule
    private int halfMoveClock = 0;
    private int fullMoveNumber = 1;

    public ChessBoard() {
        setupInitialPosition();
    }

    private void setupInitialPosition() {
        // Clear board
        Arrays.fill(board, null);

        // White pieces
        board[0] = ChessPiece.WHITE_ROOK;    // a1
        board[1] = ChessPiece.WHITE_KNIGHT;  // b1
        board[2] = ChessPiece.WHITE_BISHOP;  // c1
        board[3] = ChessPiece.WHITE_QUEEN;   // d1
        board[4] = ChessPiece.WHITE_KING;    // e1
        board[5] = ChessPiece.WHITE_BISHOP;  // f1
        board[6] = ChessPiece.WHITE_KNIGHT;  // g1
        board[7] = ChessPiece.WHITE_ROOK;    // h1

        // White pawns
        for (int i = 8; i < 16; i++) {
            board[i] = ChessPiece.WHITE_PAWN;
        }

        // Black pawns
        for (int i = 48; i < 56; i++) {
            board[i] = ChessPiece.BLACK_PAWN;
        }

        // Black pieces
        board[56] = ChessPiece.BLACK_ROOK;    // a8
        board[57] = ChessPiece.BLACK_KNIGHT;  // b8
        board[58] = ChessPiece.BLACK_BISHOP;  // c8
        board[59] = ChessPiece.BLACK_QUEEN;   // d8
        board[60] = ChessPiece.BLACK_KING;    // e8
        board[61] = ChessPiece.BLACK_BISHOP;  // f8
        board[62] = ChessPiece.BLACK_KNIGHT;  // g8
        board[63] = ChessPiece.BLACK_ROOK;    // h8

        positionHistory.add(toFEN());
    }

    public ChessPiece getPiece(ChessPosition pos) {
        if (!pos.isValid()) return null;
        return board[pos.toIndex()];
    }

    public ChessPiece getPiece(int index) {
        if (index < 0 || index >= 64) return null;
        return board[index];
    }

    public void setPiece(ChessPosition pos, ChessPiece piece) {
        if (pos.isValid()) {
            board[pos.toIndex()] = piece;
        }
    }

    public boolean makeMove(ChessMove move) {
        if (!isLegalMove(move)) return false;

        ChessPiece movingPiece = getPiece(move.from);
        ChessPiece capturedPiece = getPiece(move.to);

        // Handle special moves
        if (move.isEnPassant) {
            // Remove the captured pawn
            ChessPosition capturedPawnPos = new ChessPosition(move.to.file, move.from.rank);
            setPiece(capturedPawnPos, null);
        }

        if (move.isCastling) {
            // Move the rook
            if (move.to.file == 6) { // King-side castling
                ChessPosition rookFrom = new ChessPosition(7, move.from.rank);
                ChessPosition rookTo = new ChessPosition(5, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            } else { // Queen-side castling
                ChessPosition rookFrom = new ChessPosition(0, move.from.rank);
                ChessPosition rookTo = new ChessPosition(3, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            }
        }

        // Make the move
        setPiece(move.to, movingPiece);
        setPiece(move.from, null);

        // Handle promotion
        if (move.promotionPiece != null) {
            ChessPiece promotedPiece = ChessPiece.fromColorAndType(currentTurn, move.promotionPiece);
            setPiece(move.to, promotedPiece);
        }

        // Update castling rights
        updateCastlingRights(move, movingPiece);

        // Update en passant target
        updateEnPassantTarget(move, movingPiece);

        // Update clocks
        if (capturedPiece != null || movingPiece.getType() == PieceType.PAWN) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        if (currentTurn == PieceColor.BLACK) {
            fullMoveNumber++;
        }

        // Switch turns
        currentTurn = currentTurn.opposite();

        // Add to history
        moveHistory.add(move);
        positionHistory.add(toFEN());

        // Update game state
        updateGameState();

        return true;
    }

    private void updateCastlingRights(ChessMove move, ChessPiece movingPiece) {
        // King moves
        if (movingPiece == ChessPiece.WHITE_KING) {
            whiteKingSideCastle = false;
            whiteQueenSideCastle = false;
        } else if (movingPiece == ChessPiece.BLACK_KING) {
            blackKingSideCastle = false;
            blackQueenSideCastle = false;
        }

        // Rook moves or captures
        if (move.from.equals(new ChessPosition(0, 0)) || move.to.equals(new ChessPosition(0, 0))) {
            whiteQueenSideCastle = false;
        }
        if (move.from.equals(new ChessPosition(7, 0)) || move.to.equals(new ChessPosition(7, 0))) {
            whiteKingSideCastle = false;
        }
        if (move.from.equals(new ChessPosition(0, 7)) || move.to.equals(new ChessPosition(0, 7))) {
            blackQueenSideCastle = false;
        }
        if (move.from.equals(new ChessPosition(7, 7)) || move.to.equals(new ChessPosition(7, 7))) {
            blackKingSideCastle = false;
        }
    }

    private void updateEnPassantTarget(ChessMove move, ChessPiece movingPiece) {
        enPassantTarget = null;

        // Check for pawn double move
        if (movingPiece.getType() == PieceType.PAWN && Math.abs(move.to.rank - move.from.rank) == 2) {
            int targetRank = (move.from.rank + move.to.rank) / 2;
            enPassantTarget = new ChessPosition(move.from.file, targetRank);
        }
    }

    public boolean isLegalMove(ChessMove move) {
        ChessPiece piece = getPiece(move.from);
        if (piece == null) return false;
        if ((piece.isWhite() && currentTurn != PieceColor.WHITE) ||
                (piece.isBlack() && currentTurn != PieceColor.BLACK)) return false;

        // Check if move is pseudo-legal
        if (!isPseudoLegalMove(move)) return false;

        // Check if move leaves king in check
        ChessBoard tempBoard = this.copy();
        tempBoard.makeUncheckedMove(move);
        return !tempBoard.isInCheck(currentTurn);
    }

    private boolean isPseudoLegalMove(ChessMove move) {
        ChessPiece piece = getPiece(move.from);
        if (piece == null) return false;

        ChessPiece targetPiece = getPiece(move.to);
        if (targetPiece != null && piece.isWhite() == targetPiece.isWhite()) {
            return false; // Can't capture own piece
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
        int fileDiff = move.to.file - move.from.file;
        int rankDiff = move.to.rank - move.from.rank;

        // Forward move
        if (fileDiff == 0) {
            if (rankDiff == direction && getPiece(move.to) == null) {
                return true; // One square forward
            }
            if (rankDiff == 2 * direction &&
                    move.from.rank == (piece.isWhite() ? 1 : 6) &&
                    getPiece(move.to) == null &&
                    getPiece(new ChessPosition(move.from.file, move.from.rank + direction)) == null) {
                return true; // Two squares forward from starting position
            }
        }

        // Diagonal capture
        if (Math.abs(fileDiff) == 1 && rankDiff == direction) {
            ChessPiece targetPiece = getPiece(move.to);
            if (targetPiece != null && piece.isWhite() != targetPiece.isWhite()) {
                return true; // Regular capture
            }
            if (enPassantTarget != null && move.to.equals(enPassantTarget)) {
                return true; // En passant
            }
        }

        return false;
    }

    private boolean isValidRookMove(ChessMove move) {
        return (move.from.file == move.to.file || move.from.rank == move.to.rank) &&
                isPathClear(move.from, move.to);
    }

    private boolean isValidKnightMove(ChessMove move) {
        int fileDiff = Math.abs(move.to.file - move.from.file);
        int rankDiff = Math.abs(move.to.rank - move.from.rank);
        return (fileDiff == 2 && rankDiff == 1) || (fileDiff == 1 && rankDiff == 2);
    }

    private boolean isValidBishopMove(ChessMove move) {
        return Math.abs(move.to.file - move.from.file) == Math.abs(move.to.rank - move.from.rank) &&
                isPathClear(move.from, move.to);
    }

    private boolean isValidQueenMove(ChessMove move) {
        return isValidRookMove(move) || isValidBishopMove(move);
    }

    private boolean isValidKingMove(ChessMove move, ChessPiece piece) {
        int fileDiff = Math.abs(move.to.file - move.from.file);
        int rankDiff = Math.abs(move.to.rank - move.from.rank);

        // Normal king move
        if (fileDiff <= 1 && rankDiff <= 1) {
            return true;
        }

        // Castling
        if (rankDiff == 0 && fileDiff == 2 && !isInCheck(piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK)) {
            if (piece.isWhite() && move.from.rank == 0) {
                if (move.to.file == 6 && whiteKingSideCastle &&
                        isPathClear(move.from, new ChessPosition(7, 0))) {
                    return true; // White king-side castling
                }
                if (move.to.file == 2 && whiteQueenSideCastle &&
                        isPathClear(move.from, new ChessPosition(0, 0))) {
                    return true; // White queen-side castling
                }
            } else if (piece.isBlack() && move.from.rank == 7) {
                if (move.to.file == 6 && blackKingSideCastle &&
                        isPathClear(move.from, new ChessPosition(7, 7))) {
                    return true; // Black king-side castling
                }
                if (move.to.file == 2 && blackQueenSideCastle &&
                        isPathClear(move.from, new ChessPosition(0, 7))) {
                    return true; // Black queen-side castling
                }
            }
        }

        return false;
    }

    private boolean isPathClear(ChessPosition from, ChessPosition to) {
        int fileStep = Integer.compare(to.file, from.file);
        int rankStep = Integer.compare(to.rank, from.rank);

        int currentFile = from.file + fileStep;
        int currentRank = from.rank + rankStep;

        while (currentFile != to.file || currentRank != to.rank) {
            if (getPiece(new ChessPosition(currentFile, currentRank)) != null) {
                return false;
            }
            currentFile += fileStep;
            currentRank += rankStep;
        }

        return true;
    }

    public boolean isInCheck(PieceColor color) {
        ChessPosition kingPos = findKing(color);
        if (kingPos == null) return false;

        return isSquareAttacked(kingPos, color.opposite());
    }

    public boolean isSquareAttacked(ChessPosition pos, PieceColor attackingColor) {
        for (int i = 0; i < 64; i++) {
            ChessPiece piece = board[i];
            if (piece != null &&
                    ((attackingColor == PieceColor.WHITE && piece.isWhite()) ||
                            (attackingColor == PieceColor.BLACK && piece.isBlack()))) {

                ChessPosition piecePos = ChessPosition.fromIndex(i);
                ChessMove attackMove = new ChessMove(piecePos, pos);
                if (isPseudoLegalMove(attackMove)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ChessPosition findKing(PieceColor color) {
        ChessPiece king = color == PieceColor.WHITE ? ChessPiece.WHITE_KING : ChessPiece.BLACK_KING;
        for (int i = 0; i < 64; i++) {
            if (board[i] == king) {
                return ChessPosition.fromIndex(i);
            }
        }
        return null;
    }

    public List<ChessMove> getLegalMoves() {
        List<ChessMove> legalMoves = new ArrayList<>();

        for (int i = 0; i < 64; i++) {
            ChessPiece piece = board[i];
            if (piece != null &&
                    ((currentTurn == PieceColor.WHITE && piece.isWhite()) ||
                            (currentTurn == PieceColor.BLACK && piece.isBlack()))) {

                ChessPosition from = ChessPosition.fromIndex(i);
                List<ChessMove> pseudoLegalMoves = generatePseudoLegalMoves(from, piece);

                for (ChessMove move : pseudoLegalMoves) {
                    if (isLegalMove(move)) {
                        legalMoves.add(move);
                    }
                }
            }
        }

        return legalMoves;
    }

    private List<ChessMove> generatePseudoLegalMoves(ChessPosition from, ChessPiece piece) {
        List<ChessMove> moves = new ArrayList<>();

        switch (piece.getType()) {
            case PAWN -> generatePawnMoves(from, piece, moves);
            case ROOK -> generateRookMoves(from, moves);
            case KNIGHT -> generateKnightMoves(from, moves);
            case BISHOP -> generateBishopMoves(from, moves);
            case QUEEN -> generateQueenMoves(from, moves);
            case KING -> generateKingMoves(from, piece, moves);
        }

        return moves;
    }

    private void generatePawnMoves(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        int direction = piece.isWhite() ? 1 : -1;

        // Forward moves
        ChessPosition oneForward = new ChessPosition(from.file, from.rank + direction);
        if (oneForward.isValid() && getPiece(oneForward) == null) {
            if (oneForward.rank == (piece.isWhite() ? 7 : 0)) {
                // Promotion
                for (PieceType promotionType : new PieceType[]{PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT}) {
                    moves.add(new ChessMove(from, oneForward, promotionType, false, false, false, false, false));
                }
            } else {
                moves.add(new ChessMove(from, oneForward));
            }

            // Two squares forward
            ChessPosition twoForward = new ChessPosition(from.file, from.rank + 2 * direction);
            if (twoForward.isValid() && getPiece(twoForward) == null &&
                    from.rank == (piece.isWhite() ? 1 : 6)) {
                moves.add(new ChessMove(from, twoForward));
            }
        }

        // Captures
        for (int fileOffset : new int[]{-1, 1}) {
            ChessPosition capturePos = new ChessPosition(from.file + fileOffset, from.rank + direction);
            if (capturePos.isValid()) {
                ChessPiece targetPiece = getPiece(capturePos);
                if (targetPiece != null && piece.isWhite() != targetPiece.isWhite()) {
                    if (capturePos.rank == (piece.isWhite() ? 7 : 0)) {
                        // Promotion with capture
                        for (PieceType promotionType : new PieceType[]{PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT}) {
                            moves.add(new ChessMove(from, capturePos, promotionType, true, false, false, false, false));
                        }
                    } else {
                        moves.add(new ChessMove(from, capturePos, null, true, false, false, false, false));
                    }
                }

                // En passant
                if (enPassantTarget != null && capturePos.equals(enPassantTarget)) {
                    moves.add(new ChessMove(from, capturePos, null, true, true, false, false, false));
                }
            }
        }
    }

    private void generateRookMoves(ChessPosition from, List<ChessMove> moves) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        generateSlidingMoves(from, directions, moves);
    }

    private void generateBishopMoves(ChessPosition from, List<ChessMove> moves) {
        int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        generateSlidingMoves(from, directions, moves);
    }

    private void generateQueenMoves(ChessPosition from, List<ChessMove> moves) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        generateSlidingMoves(from, directions, moves);
    }

    private void generateSlidingMoves(ChessPosition from, int[][] directions, List<ChessMove> moves) {
        ChessPiece piece = getPiece(from);

        for (int[] direction : directions) {
            for (int distance = 1; distance < 8; distance++) {
                int newFile = from.file + direction[0] * distance;
                int newRank = from.rank + direction[1] * distance;

                ChessPosition to = new ChessPosition(newFile, newRank);
                if (!to.isValid()) break;

                ChessPiece targetPiece = getPiece(to);
                if (targetPiece == null) {
                    moves.add(new ChessMove(from, to));
                } else {
                    if (piece.isWhite() != targetPiece.isWhite()) {
                        moves.add(new ChessMove(from, to, null, true, false, false, false, false));
                    }
                    break;
                }
            }
        }
    }

    private void generateKnightMoves(ChessPosition from, List<ChessMove> moves) {
        int[][] knightMoves = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
        ChessPiece piece = getPiece(from);

        for (int[] move : knightMoves) {
            ChessPosition to = new ChessPosition(from.file + move[0], from.rank + move[1]);
            if (to.isValid()) {
                ChessPiece targetPiece = getPiece(to);
                if (targetPiece == null) {
                    moves.add(new ChessMove(from, to));
                } else if (piece.isWhite() != targetPiece.isWhite()) {
                    moves.add(new ChessMove(from, to, null, true, false, false, false, false));
                }
            }
        }
    }

    private void generateKingMoves(ChessPosition from, ChessPiece piece, List<ChessMove> moves) {
        // Normal king moves
        for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
            for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
                if (fileOffset == 0 && rankOffset == 0) continue;

                ChessPosition to = new ChessPosition(from.file + fileOffset, from.rank + rankOffset);
                if (to.isValid()) {
                    ChessPiece targetPiece = getPiece(to);
                    if (targetPiece == null) {
                        moves.add(new ChessMove(from, to));
                    } else if (piece.isWhite() != targetPiece.isWhite()) {
                        moves.add(new ChessMove(from, to, null, true, false, false, false, false));
                    }
                }
            }
        }

        // Castling moves
        if (!isInCheck(piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK)) {
            if (piece.isWhite() && from.equals(new ChessPosition(4, 0))) {
                // White castling
                if (whiteKingSideCastle && isPathClear(from, new ChessPosition(7, 0))) {
                    moves.add(new ChessMove(from, new ChessPosition(6, 0), null, false, false, true, false, false));
                }
                if (whiteQueenSideCastle && isPathClear(from, new ChessPosition(0, 0))) {
                    moves.add(new ChessMove(from, new ChessPosition(2, 0), null, false, false, true, false, false));
                }
            } else if (piece.isBlack() && from.equals(new ChessPosition(4, 7))) {
                // Black castling
                if (blackKingSideCastle && isPathClear(from, new ChessPosition(7, 7))) {
                    moves.add(new ChessMove(from, new ChessPosition(6, 7), null, false, false, true, false, false));
                }
                if (blackQueenSideCastle && isPathClear(from, new ChessPosition(0, 7))) {
                    moves.add(new ChessMove(from, new ChessPosition(2, 7), null, false, false, true, false, false));
                }
            }
        }
    }

    private void updateGameState() {
        List<ChessMove> legalMoves = getLegalMoves();
        boolean inCheck = isInCheck(currentTurn);

        if (legalMoves.isEmpty()) {
            if (inCheck) {
                gameState = currentTurn == PieceColor.WHITE ? GameState.CHECKMATE_BLACK_WINS : GameState.CHECKMATE_WHITE_WINS;
            } else {
                gameState = GameState.STALEMATE;
            }
        } else if (inCheck) {
            gameState = currentTurn == PieceColor.WHITE ? GameState.CHECK_WHITE : GameState.CHECK_BLACK;
        } else {
            gameState = currentTurn == PieceColor.WHITE ? GameState.WHITE_TURN : GameState.BLACK_TURN;
        }

        // Check for draws
        if (halfMoveClock >= 100) {
            gameState = GameState.DRAW_FIFTY_MOVE;
        } else if (isThreefoldRepetition()) {
            gameState = GameState.DRAW_THREEFOLD;
        } else if (isInsufficientMaterial()) {
            gameState = GameState.DRAW_INSUFFICIENT;
        }
    }

    private boolean isThreefoldRepetition() {
        String currentPosition = toFEN().split(" ")[0]; // Just the board position part
        int count = 0;
        for (String position : positionHistory) {
            if (position.split(" ")[0].equals(currentPosition)) {
                count++;
                if (count >= 3) return true;
            }
        }
        return false;
    }

    private boolean isInsufficientMaterial() {
        int whitePieces = 0, blackPieces = 0;
        boolean whiteBishop = false, blackBishop = false;
        boolean whiteKnight = false, blackKnight = false;

        for (ChessPiece piece : board) {
            if (piece != null) {
                switch (piece.getType()) {
                    case KING -> {}
                    case QUEEN, ROOK, PAWN -> {
                        return false; // Sufficient material
                    }
                    case BISHOP -> {
                        if (piece.isWhite()) {
                            whiteBishop = true;
                            whitePieces++;
                        } else {
                            blackBishop = true;
                            blackPieces++;
                        }
                    }
                    case KNIGHT -> {
                        if (piece.isWhite()) {
                            whiteKnight = true;
                            whitePieces++;
                        } else {
                            blackKnight = true;
                            blackPieces++;
                        }
                    }
                }
            }
        }

        // King vs King
        if (whitePieces == 0 && blackPieces == 0) return true;

        // King + Bishop vs King or King + Knight vs King
        if ((whitePieces == 1 && blackPieces == 0 && (whiteBishop || whiteKnight)) ||
                (blackPieces == 1 && whitePieces == 0 && (blackBishop || blackKnight))) {
            return true;
        }

        return false;
    }

    private void makeUncheckedMove(ChessMove move) {
        // Simplified move execution for check testing
        ChessPiece piece = getPiece(move.from);
        setPiece(move.to, piece);
        setPiece(move.from, null);

        if (move.isEnPassant) {
            ChessPosition capturedPawnPos = new ChessPosition(move.to.file, move.from.rank);
            setPiece(capturedPawnPos, null);
        }

        if (move.isCastling) {
            if (move.to.file == 6) {
                ChessPosition rookFrom = new ChessPosition(7, move.from.rank);
                ChessPosition rookTo = new ChessPosition(5, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            } else {
                ChessPosition rookFrom = new ChessPosition(0, move.from.rank);
                ChessPosition rookTo = new ChessPosition(3, move.from.rank);
                setPiece(rookTo, getPiece(rookFrom));
                setPiece(rookFrom, null);
            }
        }

        if (move.promotionPiece != null) {
            ChessPiece promotedPiece = ChessPiece.fromColorAndType(
                    piece.isWhite() ? PieceColor.WHITE : PieceColor.BLACK,
                    move.promotionPiece
            );
            setPiece(move.to, promotedPiece);
        }
    }

    public ChessBoard copy() {
        ChessBoard copy = new ChessBoard();
        System.arraycopy(this.board, 0, copy.board, 0, 64);
        copy.currentTurn = this.currentTurn;
        copy.gameState = this.gameState;
        copy.whiteKingSideCastle = this.whiteKingSideCastle;
        copy.whiteQueenSideCastle = this.whiteQueenSideCastle;
        copy.blackKingSideCastle = this.blackKingSideCastle;
        copy.blackQueenSideCastle = this.blackQueenSideCastle;
        copy.enPassantTarget = this.enPassantTarget;
        copy.halfMoveClock = this.halfMoveClock;
        copy.fullMoveNumber = this.fullMoveNumber;
        copy.moveHistory = new ArrayList<>(this.moveHistory);
        copy.positionHistory = new ArrayList<>(this.positionHistory);
        return copy;
    }

    public String toFEN() {
        StringBuilder fen = new StringBuilder();

        // Board position
        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                ChessPiece piece = getPiece(new ChessPosition(file, rank));
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFENChar(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }

        // Active color
        fen.append(' ').append(currentTurn == PieceColor.WHITE ? 'w' : 'b');

        // Castling rights
        fen.append(' ');
        String castling = "";
        if (whiteKingSideCastle) castling += "K";
        if (whiteQueenSideCastle) castling += "Q";
        if (blackKingSideCastle) castling += "k";
        if (blackQueenSideCastle) castling += "q";
        fen.append(castling.isEmpty() ? "-" : castling);

        // En passant target
        fen.append(' ').append(enPassantTarget != null ? enPassantTarget.toNotation() : "-");

        // Half-move clock and full-move number
        fen.append(' ').append(halfMoveClock).append(' ').append(fullMoveNumber);

        return fen.toString();
    }

    private char pieceToFENChar(ChessPiece piece) {
        char base = switch (piece.getType()) {
            case KING -> 'k';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            case PAWN -> 'p';
        };
        return piece.isWhite() ? Character.toUpperCase(base) : base;
    }

    // Getters
    public PieceColor getCurrentTurn() { return currentTurn; }
    public GameState getGameState() { return gameState; }
    public List<ChessMove> getMoveHistory() { return new ArrayList<>(moveHistory); }
    public int getHalfMoveClock() { return halfMoveClock; }
    public int getFullMoveNumber() { return fullMoveNumber; }
    public ChessPosition getEnPassantTarget() { return enPassantTarget; }
}