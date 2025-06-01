package com.leclowndu93150.guichess.game;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.data.PlayerData;
import com.leclowndu93150.guichess.gui.ChessGUI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.*;

public class ChessGame {
    private final UUID gameId;
    private final ServerPlayer whitePlayer;
    private final ServerPlayer blackPlayer;
    private final TimeControl timeControl;
    private final long startTime;

    private ChessBoard board;
    private int whiteTimeLeft; // in seconds
    private int blackTimeLeft; // in seconds
    private long lastMoveTime;
    private boolean gameActive = true;

    // Game state
    private ChessPosition selectedSquare = null;
    private Set<ChessPosition> validMoves = new HashSet<>();
    private boolean drawOffered = false;
    private ServerPlayer drawOfferer = null;

    // Analysis integration
    private boolean analysisMode = false;
    private Map<String, Object> analysisData = new HashMap<>();

    public ChessGame(ServerPlayer whitePlayer, ServerPlayer blackPlayer, TimeControl timeControl) {
        this.gameId = UUID.randomUUID();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.timeControl = timeControl;
        this.startTime = System.currentTimeMillis();
        this.lastMoveTime = startTime;

        this.board = new ChessBoard();
        this.whiteTimeLeft = timeControl.initialSeconds;
        this.blackTimeLeft = timeControl.initialSeconds;

        // Start the game timer
        GameManager.getInstance().scheduleGameTimer(this);
    }

    public boolean makeMove(ServerPlayer player, ChessPosition from, ChessPosition to, PieceType promotion) {
        if (!gameActive || !isPlayerTurn(player)) return false;

        ChessMove move = new ChessMove(from, to, promotion,
                board.getPiece(to) != null,
                false, false, false, false);

        // Validate and make the move
        if (board.makeMove(move)) {
            // Update timers
            updateTimers();

            // Clear selection
            selectedSquare = null;
            validMoves.clear();

            // Clear any draw offers
            drawOffered = false;
            drawOfferer = null;

            // Check for game end
            checkGameEnd();

            // Update GUIs
            updatePlayerGUIs();

            return true;
        }

        return false;
    }

    private void updateTimers() {
        long currentTime = System.currentTimeMillis();
        long elapsed = (currentTime - lastMoveTime) / 1000;

        if (board.getCurrentTurn() == PieceColor.BLACK) {
            // White just moved
            whiteTimeLeft = (int) Math.max(0, whiteTimeLeft - elapsed + timeControl.incrementSeconds);
        } else {
            // Black just moved
            blackTimeLeft = (int) Math.max(0, blackTimeLeft - elapsed + timeControl.incrementSeconds);
        }

        lastMoveTime = currentTime;
    }

    public void tickTimer() {
        if (!gameActive || board.getGameState().name().contains("CHECKMATE") ||
                board.getGameState().name().contains("DRAW") ||
                board.getGameState().name().contains("STALEMATE")) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = (currentTime - lastMoveTime) / 1000;

        if (board.getCurrentTurn() == PieceColor.WHITE) {
            whiteTimeLeft = (int) Math.max(0, whiteTimeLeft - elapsed);
            if (whiteTimeLeft <= 0) {
                endGame(GameState.WHITE_TIME_OUT);
                return;
            }
        } else {
            blackTimeLeft = (int) Math.max(0, blackTimeLeft - elapsed);
            if (blackTimeLeft <= 0) {
                endGame(GameState.BLACK_TIME_OUT);
                return;
            }
        }

        lastMoveTime = currentTime;
        updatePlayerGUIs();
    }

    private void checkGameEnd() {
        GameState state = board.getGameState();
        if (state != GameState.WHITE_TURN && state != GameState.BLACK_TURN &&
                state != GameState.CHECK_WHITE && state != GameState.CHECK_BLACK &&
                state != GameState.WHITE_PROMOTION && state != GameState.BLACK_PROMOTION) {
            endGame(state);
        }
    }

    private void endGame(GameState finalState) {
        gameActive = false;

        // Update ELO ratings
        updateELORatings(finalState);

        // Save game to history
        saveGameHistory(finalState);

        // Notify players
        notifyGameEnd(finalState);

        // Schedule GUI close
        GameManager.getInstance().scheduleGameCleanup(this, 10); // 10 seconds
    }

    private void updateELORatings(GameState finalState) {
        PlayerData whiteData = GameManager.getInstance().getPlayerData(whitePlayer);
        PlayerData blackData = GameManager.getInstance().getPlayerData(blackPlayer);

        double result; // 1.0 = white wins, 0.5 = draw, 0.0 = black wins
        switch (finalState) {
            case CHECKMATE_WHITE_WINS, BLACK_RESIGNED, BLACK_TIME_OUT -> result = 1.0;
            case CHECKMATE_BLACK_WINS, WHITE_RESIGNED, WHITE_TIME_OUT -> result = 0.0;
            default -> result = 0.5; // Draw
        }

        // Calculate new ELO ratings
        double[] newRatings = calculateELO(whiteData.elo, blackData.elo, result);

        whiteData.elo = (int) newRatings[0];
        blackData.elo = (int) newRatings[1];
        whiteData.gamesPlayed++;
        blackData.gamesPlayed++;

        if (result == 1.0) {
            whiteData.wins++;
            blackData.losses++;
        } else if (result == 0.0) {
            whiteData.losses++;
            blackData.wins++;
        } else {
            whiteData.draws++;
            blackData.draws++;
        }

        GameManager.getInstance().savePlayerData();
    }

    private double[] calculateELO(int whiteELO, int blackELO, double result) {
        double kFactor = 32.0; // Standard K-factor

        double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (blackELO - whiteELO) / 400.0));
        double expectedBlack = 1.0 - expectedWhite;

        double newWhiteELO = whiteELO + kFactor * (result - expectedWhite);
        double newBlackELO = blackELO + kFactor * ((1.0 - result) - expectedBlack);

        return new double[]{newWhiteELO, newBlackELO};
    }

    public boolean selectSquare(ServerPlayer player, ChessPosition position) {
        if (!gameActive || !isPlayerTurn(player)) return false;

        ChessPiece piece = board.getPiece(position);
        PieceColor playerColor = getPlayerColor(player);

        // If clicking on own piece, select it
        if (piece != null &&
                ((playerColor == PieceColor.WHITE && piece.isWhite()) ||
                        (playerColor == PieceColor.BLACK && piece.isBlack()))) {

            selectedSquare = position;
            validMoves = getValidMovesFrom(position);
            updatePlayerGUIs();
            return true;
        }

        // If a piece is selected and clicking on valid move, make the move
        if (selectedSquare != null && validMoves.contains(position)) {
            ChessPiece selectedPiece = board.getPiece(selectedSquare);

            // Check for promotion
            if (selectedPiece != null && selectedPiece.getType() == PieceType.PAWN &&
                    ((selectedPiece.isWhite() && position.rank == 7) ||
                            (selectedPiece.isBlack() && position.rank == 0))) {
                // Show promotion dialog
                showPromotionDialog(player, selectedSquare, position);
                return true;
            }

            return makeMove(player, selectedSquare, position, null);
        }

        // Clear selection
        selectedSquare = null;
        validMoves.clear();
        updatePlayerGUIs();
        return true;
    }

    private Set<ChessPosition> getValidMovesFrom(ChessPosition from) {
        Set<ChessPosition> moves = new HashSet<>();
        List<ChessMove> legalMoves = board.getLegalMoves();

        for (ChessMove move : legalMoves) {
            if (move.from.equals(from)) {
                moves.add(move.to);
            }
        }

        return moves;
    }

    private void showPromotionDialog(ServerPlayer player, ChessPosition from, ChessPosition to) {
        // This will be handled in the GUI class
        ChessGUI gui = GameManager.getInstance().getPlayerGUI(player);
        if (gui != null) {
            gui.showPromotionDialog(from, to);
        }
    }

    public boolean offerDraw(ServerPlayer player) {
        if (!gameActive || drawOffered) return false;

        drawOffered = true;
        drawOfferer = player;

        ServerPlayer opponent = getOpponent(player);
        if (opponent != null) {
            opponent.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " offers a draw. Accept or decline?"));
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean respondToDraw(ServerPlayer player, boolean accept) {
        if (!drawOffered || player == drawOfferer) return false;

        if (accept) {
            endGame(GameState.DRAW_AGREED);
        } else {
            drawOffered = false;
            drawOfferer = null;

            ServerPlayer offerer = drawOfferer;
            if (offerer != null) {
                offerer.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " declined the draw offer."));
            }
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean resign(ServerPlayer player) {
        if (!gameActive) return false;

        GameState resignState = getPlayerColor(player) == PieceColor.WHITE ?
                GameState.WHITE_RESIGNED : GameState.BLACK_RESIGNED;
        endGame(resignState);
        return true;
    }

    public void enableAnalysisMode() {
        analysisMode = true;
        // Request analysis from Stockfish integration point
        requestStockfishAnalysis();
    }

    private void requestStockfishAnalysis() {
        // Integration point for Stockfish
        // This is where external analysis engine would be called
        analysisData.put("fen", board.toFEN());
        analysisData.put("depth", 15);
        analysisData.put("multipv", 3);

        // Placeholder for future Stockfish integration
        // StockfishEngine.analyze(board.toFEN(), this::receiveAnalysis);
    }

    public void receiveAnalysis(Map<String, Object> analysis) {
        analysisData.putAll(analysis);
        updatePlayerGUIs();
    }

    private void saveGameHistory(GameState finalState) {
        CompoundTag gameData = new CompoundTag();
        gameData.putString("gameId", gameId.toString());
        gameData.putString("whitePlayer", whitePlayer.getUUID().toString());
        gameData.putString("blackPlayer", blackPlayer.getUUID().toString());
        gameData.putString("timeControl", timeControl.name());
        gameData.putLong("startTime", startTime);
        gameData.putLong("endTime", System.currentTimeMillis());
        gameData.putString("result", finalState.name());
        gameData.putString("finalFEN", board.toFEN());

        // Save move history
        ListTag moves = new ListTag();
        for (ChessMove move : board.getMoveHistory()) {
            moves.add(StringTag.valueOf(move.toNotation()));
        }
        gameData.put("moves", moves);

        GameManager.getInstance().saveGameHistory(gameData);
    }

    private void notifyGameEnd(GameState finalState) {
        String message = switch (finalState) {
            case CHECKMATE_WHITE_WINS -> "§fWhite wins by checkmate!";
            case CHECKMATE_BLACK_WINS -> "§8Black wins by checkmate!";
            case STALEMATE -> "§7Game drawn by stalemate";
            case DRAW_AGREED -> "§7Game drawn by agreement";
            case DRAW_FIFTY_MOVE -> "§7Game drawn by 50-move rule";
            case DRAW_THREEFOLD -> "§7Game drawn by threefold repetition";
            case DRAW_INSUFFICIENT -> "§7Game drawn by insufficient material";
            case WHITE_RESIGNED -> "§8Black wins - White resigned";
            case BLACK_RESIGNED -> "§fWhite wins - Black resigned";
            case WHITE_TIME_OUT -> "§8Black wins on time";
            case BLACK_TIME_OUT -> "§fWhite wins on time";
            default -> "§7Game ended";
        };

        whitePlayer.sendSystemMessage(Component.literal(message));
        blackPlayer.sendSystemMessage(Component.literal(message));
    }

    private void updatePlayerGUIs() {
        ChessGUI whiteGUI = GameManager.getInstance().getPlayerGUI(whitePlayer);
        ChessGUI blackGUI = GameManager.getInstance().getPlayerGUI(blackPlayer);

        if (whiteGUI != null) whiteGUI.updateBoard();
        if (blackGUI != null) blackGUI.updateBoard();
    }

    // Utility methods
    public boolean isPlayerTurn(ServerPlayer player) {
        PieceColor playerColor = getPlayerColor(player);
        return playerColor == board.getCurrentTurn();
    }

    public PieceColor getPlayerColor(ServerPlayer player) {
        if (player.equals(whitePlayer)) return PieceColor.WHITE;
        if (player.equals(blackPlayer)) return PieceColor.BLACK;
        return null; // Spectator
    }

    public ServerPlayer getOpponent(ServerPlayer player) {
        if (player.equals(whitePlayer)) return blackPlayer;
        if (player.equals(blackPlayer)) return whitePlayer;
        return null;
    }

    public String formatTime(int seconds) {
        if (seconds < 0) return "0:00";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    // Getters
    public UUID getGameId() { return gameId; }
    public ServerPlayer getWhitePlayer() { return whitePlayer; }
    public ServerPlayer getBlackPlayer() { return blackPlayer; }
    public ChessBoard getBoard() { return board; }
    public TimeControl getTimeControl() { return timeControl; }
    public boolean isGameActive() { return gameActive; }
    public int getWhiteTimeLeft() { return whiteTimeLeft; }
    public int getBlackTimeLeft() { return blackTimeLeft; }
    public ChessPosition getSelectedSquare() { return selectedSquare; }
    public Set<ChessPosition> getValidMoves() { return validMoves; }
    public boolean isDrawOffered() { return drawOffered; }
    public ServerPlayer getDrawOfferer() { return drawOfferer; }
    public boolean isAnalysisMode() { return analysisMode; }
    public Map<String, Object> getAnalysisData() { return analysisData; }
}