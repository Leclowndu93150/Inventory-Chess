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
    private int whiteTimeLeft;
    private int blackTimeLeft;
    private boolean gameActive = true;

    // Per-player selection state instead of shared
    private ChessPosition whiteSelectedSquare = null;
    private Set<ChessPosition> whiteValidMoves = new HashSet<>();
    private ChessPosition blackSelectedSquare = null;
    private Set<ChessPosition> blackValidMoves = new HashSet<>();

    private boolean drawOffered = false;
    private ServerPlayer drawOfferer = null;

    private boolean analysisMode = false;
    private Map<String, Object> analysisData = new HashMap<>();

    public ChessGame(ServerPlayer whitePlayer, ServerPlayer blackPlayer, TimeControl timeControl) {
        this.gameId = UUID.randomUUID();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.timeControl = timeControl;
        this.startTime = System.currentTimeMillis();

        this.board = new ChessBoard();
        if (timeControl.initialSeconds == -1) { // Unlimited time
            this.whiteTimeLeft = Integer.MAX_VALUE; // Effectively unlimited
            this.blackTimeLeft = Integer.MAX_VALUE;
        } else {
            this.whiteTimeLeft = timeControl.initialSeconds;
            this.blackTimeLeft = timeControl.initialSeconds;
        }
    }

    public boolean makeMove(ServerPlayer player, ChessPosition from, ChessPosition to, PieceType promotion) {
        if (!gameActive || !isPlayerTurn(player)) return false;

        ChessMove legalMoveToMake = null;
        for (ChessMove legalMove : board.getLegalMoves()) {
            if (legalMove.from.equals(from) && legalMove.to.equals(to)) {
                if (promotion != null) {
                    if (legalMove.promotionPiece == promotion) {
                        legalMoveToMake = legalMove;
                        break;
                    }
                } else {
                    if (legalMove.promotionPiece == null) {
                        legalMoveToMake = legalMove;
                        break;
                    }
                }
            }
        }

        if (legalMoveToMake == null) {
            player.sendSystemMessage(Component.literal("§cInvalid move attempted. (Not found in legal moves)"));
            clearPlayerSelection(player);
            updatePlayerGUIs();
            return false;
        }

        if (board.makeMove(legalMoveToMake)) {
            updateTimersAfterMove();

            // Clear all selections after move
            clearAllSelections();

            if (drawOffered && getOpponent(player).equals(drawOfferer)) {
                // If a move is made, any pending draw offer to the current player is implicitly declined.
                ServerPlayer opponent = drawOfferer;
                drawOffered = false;
                drawOfferer = null;
                if (opponent != null) {
                    opponent.sendSystemMessage(Component.literal("§eYour draw offer was implicitly declined by " + player.getName().getString() + " making a move."));
                }
            }

            checkGameEnd();
            updatePlayerGUIs();

            return true;
        }
        return false;
    }

    private void updateTimersAfterMove() {
        if (timeControl.initialSeconds == -1) return; // No increment for unlimited time

        if (board.getCurrentTurn() == PieceColor.BLACK) {
            whiteTimeLeft += timeControl.incrementSeconds;
        } else {
            blackTimeLeft += timeControl.incrementSeconds;
        }
    }

    public void tickTimer() {
        if (!gameActive || board.getGameState().name().contains("CHECKMATE") ||
                board.getGameState().name().contains("DRAW") ||
                board.getGameState().name().contains("STALEMATE") ||
                board.getGameState().name().contains("RESIGNED") ||
                board.getGameState().name().contains("TIME_OUT")) {
            return;
        }

        if (timeControl.initialSeconds == -1) return; // No ticking for unlimited time

        if (board.getCurrentTurn() == PieceColor.WHITE) {
            if (whiteTimeLeft > 0) {
                whiteTimeLeft--;
            }
            if (whiteTimeLeft <= 0) {
                endGame(GameState.WHITE_TIME_OUT);
                return;
            }
        } else {
            if (blackTimeLeft > 0) {
                blackTimeLeft--;
            }
            if (blackTimeLeft <= 0) {
                endGame(GameState.BLACK_TIME_OUT);
                return;
            }
        }
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

    void endGame(GameState finalState) {
        if (!gameActive) return; // Prevent multiple endGame calls
        gameActive = false;

        updateELORatings(finalState);
        saveGameHistory(finalState);
        notifyGameEnd(finalState);
        updatePlayerGUIs(); // Final GUI update to show game over state

        GameManager.getInstance().scheduleGameCleanup(this, 15);
    }

    private void updateELORatings(GameState finalState) {
        PlayerData whiteData = GameManager.getInstance().getPlayerData(whitePlayer);
        PlayerData blackData = GameManager.getInstance().getPlayerData(blackPlayer);

        double result;
        switch (finalState) {
            case CHECKMATE_WHITE_WINS, BLACK_RESIGNED, BLACK_TIME_OUT:
                result = 1.0;
                break;
            case CHECKMATE_BLACK_WINS, WHITE_RESIGNED, WHITE_TIME_OUT:
                result = 0.0;
                break;
            default: // All draws
                result = 0.5;
                break;
        }

        double[] newRatings = calculateELO(whiteData.elo, blackData.elo, result);

        whiteData.elo = (int) Math.round(newRatings[0]);
        blackData.elo = (int) Math.round(newRatings[1]);
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
        double kFactor = 32.0;

        double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (double)(blackELO - whiteELO) / 400.0));
        double expectedBlack = 1.0 - expectedWhite;

        double newWhiteELO = whiteELO + kFactor * (result - expectedWhite);
        double newBlackELO = blackELO + kFactor * ((1.0 - result) - expectedBlack);

        return new double[]{newWhiteELO, newBlackELO};
    }

    public boolean selectSquare(ServerPlayer player, ChessPosition position) {
        if (!gameActive || (!isPlayerTurn(player) && !analysisMode)) return false;

        ChessPiece piece = board.getPiece(position);
        PieceColor playerColor = getPlayerColor(player);
        PieceColor playerColorForSelection = analysisMode ? board.getCurrentTurn() : playerColor;

        // Get player-specific selection state
        ChessPosition playerSelectedSquare = getPlayerSelectedSquare(player);
        Set<ChessPosition> playerValidMoves = getPlayerValidMoves(player);

        // Priority 1: If we have a selection and this is a valid move (including captures), make the move
        if (playerSelectedSquare != null && playerValidMoves.contains(position)) {
            if (analysisMode) { // In analysis mode, just make the move for display
                ChessMove tempMove = new ChessMove(playerSelectedSquare, position); // Simplified move for analysis display
                board.makeMove(tempMove); // This won't affect real game state if board is a copy or analysis is separate
                clearPlayerSelection(player);
                updatePlayerGUIs();
                return true;
            }

            ChessPiece selectedPieceOnBoard = board.getPiece(playerSelectedSquare);

            if (selectedPieceOnBoard != null && selectedPieceOnBoard.getType() == PieceType.PAWN &&
                    ((selectedPieceOnBoard.isWhite() && position.rank == 7) ||
                            (selectedPieceOnBoard.isBlack() && position.rank == 0))) {
                showPromotionDialog(player, playerSelectedSquare, position);
                return true;
            }

            return makeMove(player, playerSelectedSquare, position, null);
        }

        // Priority 2: If there's a piece at this position that belongs to the player, select it
        if (piece != null &&
                ((playerColorForSelection == PieceColor.WHITE && piece.isWhite()) ||
                        (playerColorForSelection == PieceColor.BLACK && piece.isBlack()))) {

            setPlayerSelection(player, position, getValidMovesFrom(position));
            updatePlayerGUIs();
            return true;
        }

        // Priority 3: Deselect if clicking on invalid square
        clearPlayerSelection(player);
        updatePlayerGUIs();
        return true;
    }

    private Set<ChessPosition> getValidMovesFrom(ChessPosition from) {
        Set<ChessPosition> moves = new HashSet<>();
        List<ChessMove> legalMoves = board.getLegalMoves(); // Considers current player's turn

        PieceColor colorOfPieceAtFrom = null;
        ChessPiece pieceAtFrom = board.getPiece(from);
        if (pieceAtFrom != null) {
            colorOfPieceAtFrom = pieceAtFrom.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
        }

        // In analysis mode, show moves for the piece at 'from', regardless of whose turn it is in the main game.
        if (analysisMode && colorOfPieceAtFrom != null && colorOfPieceAtFrom != board.getCurrentTurn()) {
            // If in analysis mode and selected piece is not of current turn, get its moves
            ChessBoard tempBoard = board.copy(); // Create a copy to not mess with main board's turn
            tempBoard.setCurrentTurn(colorOfPieceAtFrom); // Temporarily set turn
            for (ChessMove move : tempBoard.getLegalMoves()) {
                if (move.from.equals(from)) {
                    moves.add(move.to);
                }
            }
        } else {
            // Normal mode or analysis mode with piece of current turn
            for (ChessMove move : legalMoves) {
                if (move.from.equals(from)) {
                    moves.add(move.to);
                }
            }
        }
        return moves;
    }

    // Helper methods for per-player selection state
    private ChessPosition getPlayerSelectedSquare(ServerPlayer player) {
        PieceColor playerColor = getPlayerColor(player);
        if (playerColor == PieceColor.WHITE) {
            return whiteSelectedSquare;
        } else if (playerColor == PieceColor.BLACK) {
            return blackSelectedSquare;
        }
        return null;
    }

    private Set<ChessPosition> getPlayerValidMoves(ServerPlayer player) {
        PieceColor playerColor = getPlayerColor(player);
        if (playerColor == PieceColor.WHITE) {
            return whiteValidMoves;
        } else if (playerColor == PieceColor.BLACK) {
            return blackValidMoves;
        }
        return new HashSet<>();
    }

    private void setPlayerSelection(ServerPlayer player, ChessPosition selectedSquare, Set<ChessPosition> validMoves) {
        PieceColor playerColor = getPlayerColor(player);
        if (playerColor == PieceColor.WHITE) {
            this.whiteSelectedSquare = selectedSquare;
            this.whiteValidMoves = new HashSet<>(validMoves);
        } else if (playerColor == PieceColor.BLACK) {
            this.blackSelectedSquare = selectedSquare;
            this.blackValidMoves = new HashSet<>(validMoves);
        }
    }

    private void clearPlayerSelection(ServerPlayer player) {
        PieceColor playerColor = getPlayerColor(player);
        if (playerColor == PieceColor.WHITE) {
            this.whiteSelectedSquare = null;
            this.whiteValidMoves.clear();
        } else if (playerColor == PieceColor.BLACK) {
            this.blackSelectedSquare = null;
            this.blackValidMoves.clear();
        }
    }

    private void clearAllSelections() {
        whiteSelectedSquare = null;
        whiteValidMoves.clear();
        blackSelectedSquare = null;
        blackValidMoves.clear();
    }

    private void showPromotionDialog(ServerPlayer player, ChessPosition from, ChessPosition to) {
        ChessGUI gui = GameManager.getInstance().getPlayerGUI(player);
        if (gui != null) {
            gui.showPromotionDialog(from, to);
        }
    }

    public boolean offerDraw(ServerPlayer player) {
        if (!gameActive || drawOffered || analysisMode) return false;

        drawOffered = true;
        drawOfferer = player;

        ServerPlayer opponent = getOpponent(player);
        if (opponent != null) {
            opponent.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " offers a draw. Use /chess accept or /chess decline, or GUI buttons."));
            player.sendSystemMessage(Component.literal("§eYou offered a draw."));
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean respondToDraw(ServerPlayer player, boolean accept) {
        if (!gameActive || !drawOffered || analysisMode || player.equals(drawOfferer)) {
            return false;
        }

        ServerPlayer opponentWhoOffered = drawOfferer;

        if (accept) {
            player.sendSystemMessage(Component.literal("§aYou accepted the draw offer."));
            if (opponentWhoOffered != null) {
                opponentWhoOffered.sendSystemMessage(Component.literal("§a" + player.getName().getString() + " accepted your draw offer."));
            }
            endGame(GameState.DRAW_AGREED);
        } else {
            drawOffered = false;
            drawOfferer = null;
            player.sendSystemMessage(Component.literal("§cYou declined the draw offer."));
            if (opponentWhoOffered != null) {
                opponentWhoOffered.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " declined your draw offer."));
            }
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean cancelDrawOffer(ServerPlayer player) {
        if (!gameActive || !drawOffered || analysisMode || !player.equals(drawOfferer)) {
            return false;
        }
        drawOffered = false;
        drawOfferer = null;

        ServerPlayer opponent = getOpponent(player);
        if (opponent != null) {
            opponent.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " cancelled their draw offer."));
        }
        player.sendSystemMessage(Component.literal("§eYou cancelled your draw offer."));
        updatePlayerGUIs();
        return true;
    }

    public boolean resign(ServerPlayer player) {
        if (!gameActive || analysisMode) return false;

        GameState resignState = getPlayerColor(player) == PieceColor.WHITE ?
                GameState.WHITE_RESIGNED : GameState.BLACK_RESIGNED;
        endGame(resignState);
        return true;
    }

    public void enableAnalysisMode() {
        if(gameActive) { // Can only enter analysis mode if game is over or was never for rating
            if (whitePlayer != null) whitePlayer.sendSystemMessage(Component.literal("§dGame is now in analysis mode. Moves are not rated."));
            if (blackPlayer != null) blackPlayer.sendSystemMessage(Component.literal("§dGame is now in analysis mode. Moves are not rated."));
        }
        analysisMode = true;
        requestStockfishAnalysis();
        updatePlayerGUIs(); // Update GUI to reflect analysis mode (e.g. different button states)
    }

    private void requestStockfishAnalysis() {
        analysisData.put("fen", board.toFEN());
        analysisData.put("depth", 15);
        analysisData.put("multipv", 3);
    }

    public void receiveAnalysis(Map<String, Object> analysis) {
        analysisData.putAll(analysis);
        updatePlayerGUIs();
    }

    private void saveGameHistory(GameState finalState) {
        CompoundTag gameData = new CompoundTag();
        gameData.putString("gameId", gameId.toString());
        if (whitePlayer != null) gameData.putString("whitePlayerName", whitePlayer.getName().getString());
        if (whitePlayer != null) gameData.putString("whitePlayerUUID", whitePlayer.getUUID().toString());
        if (blackPlayer != null) gameData.putString("blackPlayerName", blackPlayer.getName().getString());
        if (blackPlayer != null) gameData.putString("blackPlayerUUID", blackPlayer.getUUID().toString());
        gameData.putString("timeControl", timeControl.name());
        gameData.putLong("startTime", startTime);
        gameData.putLong("endTime", System.currentTimeMillis());
        gameData.putString("result", finalState.name());
        gameData.putString("finalFEN", board.toFEN());

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
            default -> "§7Game ended (" + finalState.name() + ")";
        };

        if (whitePlayer != null) whitePlayer.sendSystemMessage(Component.literal(message));
        if (blackPlayer != null) blackPlayer.sendSystemMessage(Component.literal(message));
    }

    private void updatePlayerGUIs() {
        ChessGUI whiteGUI = GameManager.getInstance().getPlayerGUI(whitePlayer);
        ChessGUI blackGUI = GameManager.getInstance().getPlayerGUI(blackPlayer);

        if (whiteGUI != null && whiteGUI.isOpen()) whiteGUI.updateBoard();
        if (blackGUI != null && blackGUI.isOpen()) blackGUI.updateBoard();

        // Update spectator GUIs too
        GameManager.getInstance().getActiveGames().get(gameId); // ensure game is fetched if needed
        GameManager.getInstance().updateSpectatorGUIs(this);
    }

    public boolean isPlayerTurn(ServerPlayer player) {
        PieceColor pc = getPlayerColor(player);
        if (pc == null) return false; // Spectator
        return pc == board.getCurrentTurn();
    }

    public PieceColor getPlayerColor(ServerPlayer player) {
        if (player == null) return null;
        if (player.equals(whitePlayer)) return PieceColor.WHITE;
        if (player.equals(blackPlayer)) return PieceColor.BLACK;
        return null;
    }

    public ServerPlayer getOpponent(ServerPlayer player) {
        if (player == null) return null;
        if (player.equals(whitePlayer)) return blackPlayer;
        if (player.equals(blackPlayer)) return whitePlayer;
        return null;
    }

    public String formatTime(int seconds) {
        if (seconds == Integer.MAX_VALUE) return "∞"; // Unlimited
        if (seconds < 0) seconds = 0;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    // Updated methods to use per-player selection state
    public UUID getGameId() { return gameId; }
    public ServerPlayer getWhitePlayer() { return whitePlayer; }
    public ServerPlayer getBlackPlayer() { return blackPlayer; }
    public ChessBoard getBoard() { return board; }
    public TimeControl getTimeControl() { return timeControl; }
    public boolean isGameActive() { return gameActive; }
    public int getWhiteTimeLeft() { return whiteTimeLeft; }
    public int getBlackTimeLeft() { return blackTimeLeft; }

    // These methods now return player-specific selection state
    public ChessPosition getSelectedSquare(ServerPlayer player) {
        return getPlayerSelectedSquare(player);
    }

    public Set<ChessPosition> getValidMoves(ServerPlayer player) {
        return getPlayerValidMoves(player);
    }

    // Legacy methods for backward compatibility - default to white player for now
    @Deprecated
    public ChessPosition getSelectedSquare() {
        return whiteSelectedSquare;
    }

    @Deprecated
    public Set<ChessPosition> getValidMoves() {
        return whiteValidMoves;
    }

    public boolean isDrawOffered() { return drawOffered; }
    public ServerPlayer getDrawOfferer() { return drawOfferer; }
    public boolean isAnalysisMode() { return analysisMode; }
    public Map<String, Object> getAnalysisData() { return analysisData; }
}