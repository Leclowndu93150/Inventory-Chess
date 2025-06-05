package com.leclowndu93150.guichess.game.core;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.game.players.GameParticipant;
import com.leclowndu93150.guichess.game.players.HumanPlayer;
import com.leclowndu93150.guichess.util.time.TimeControl;
import com.leclowndu93150.guichess.data.models.PlayerData;
import com.leclowndu93150.guichess.gui.game.ChessGUI;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.*;

/**
 * Manages a chess game session between two participants with timing, GUI updates, and game state.
 * Supports both human players and chess bots as participants.
 */
public class ChessGame {
    private final UUID gameId;
    private final GameParticipant whiteParticipant;
    private final GameParticipant blackParticipant;
    private final TimeControl timeControl;
    private final long startTime;

    protected ChessBoard board;
    private int whiteTimeLeft;
    private int blackTimeLeft;
    private boolean gameActive = true;

    private ChessPosition whiteSelectedSquare = null;
    private Set<ChessPosition> whiteValidMoves = new HashSet<>();
    private ChessPosition blackSelectedSquare = null;
    private Set<ChessPosition> blackValidMoves = new HashSet<>();

    private boolean drawOffered = false;
    private GameParticipant drawOfferer = null;
    private boolean resignOffered = false;
    private GameParticipant resignOfferer = null;

    private boolean analysisMode = false;
    private Map<String, Object> analysisData = new HashMap<>();
    protected boolean timerStarted = false;
    
    private int hintsAllowed = 0;
    private int whiteHintsUsed = 0;
    private int blackHintsUsed = 0;
    
    private List<ItemStack> betItems = new ArrayList<>();
    
    private List<Long> moveTimestamps = new ArrayList<>();
    private long lastMoveTime = 0;

    public ChessGame(GameParticipant whiteParticipant, GameParticipant blackParticipant, TimeControl timeControl) {
        this(whiteParticipant, blackParticipant, timeControl, 0);
    }
    
    public ChessGame(GameParticipant whiteParticipant, GameParticipant blackParticipant, TimeControl timeControl, int hintsAllowed) {
        this.gameId = UUID.randomUUID();
        this.whiteParticipant = whiteParticipant;
        this.blackParticipant = blackParticipant;
        this.timeControl = timeControl;
        this.startTime = System.currentTimeMillis();
        this.hintsAllowed = hintsAllowed;

        this.board = new ChessBoard();
        if (timeControl.initialSeconds == -1) {
            this.whiteTimeLeft = Integer.MAX_VALUE;
            this.blackTimeLeft = Integer.MAX_VALUE;
        } else {
            this.whiteTimeLeft = timeControl.initialSeconds;
            this.blackTimeLeft = timeControl.initialSeconds;
        }
        
        this.lastMoveTime = this.startTime;
    }
    
    public ServerPlayer getWhitePlayer() {
        return whiteParticipant instanceof HumanPlayer ? ((HumanPlayer) whiteParticipant).getServerPlayer() : null;
    }
    
    public ServerPlayer getBlackPlayer() {
        return blackParticipant instanceof HumanPlayer ? ((HumanPlayer) blackParticipant).getServerPlayer() : null;
    }
    
    public GameParticipant getWhiteParticipant() { return whiteParticipant; }
    public GameParticipant getBlackParticipant() { return blackParticipant; }

    /**
     * Attempts to make a move for the specified player.
     * Validates the move, updates timers, and checks for game ending conditions.
     * 
     * @param player the player making the move
     * @param from starting position
     * @param to ending position  
     * @param promotion piece type for pawn promotion (null if not promoting)
     * @return true if move was made successfully
     */
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
            long currentTime = System.currentTimeMillis();
            moveTimestamps.add(currentTime);
            lastMoveTime = currentTime;
            
            updateTimersAfterMove();

            clearAllSelections();
            
            if (!timerStarted && board.getCurrentTurn() == PieceColor.BLACK) {
                timerStarted = true;
            }

            if (drawOffered && drawOfferer instanceof HumanPlayer) {
                ServerPlayer drawOfferingPlayer = ((HumanPlayer) drawOfferer).getServerPlayer();
                if (getOpponent(player).equals(drawOfferingPlayer)) {
                    drawOffered = false;
                    drawOfferer = null;
                    if (drawOfferingPlayer != null) {
                        drawOfferingPlayer.sendSystemMessage(Component.literal("§eYour draw offer was implicitly declined by " + player.getName().getString() + " making a move."));
                    }
                }
            }

            checkGameEnd();
            playMoveSound();
            updatePlayerGUIs();

            return true;
        }
        return false;
    }

    protected void updateTimersAfterMove() {
        if (timeControl.initialSeconds == -1) return;

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

        if (timeControl.initialSeconds == -1) return;
        if (!timerStarted) return;

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

    protected void endGame(GameState finalState) {
        if (!gameActive) return;
        gameActive = false;
        
        board.setGameState(finalState);

        updateELORatings(finalState);
        awardBetIfNeeded(finalState);
        saveGameHistory(finalState);
        notifyGameEnd(finalState);
        updatePlayerGUIs();

        GameManager.getInstance().scheduleGameCleanup(this, 15);
    }
    
    private void awardBetIfNeeded(GameState finalState) {
        if (!hasBet()) return;
        
        ServerPlayer winner = null;
        switch (finalState) {
            case CHECKMATE_WHITE_WINS:
            case BLACK_RESIGNED:
            case BLACK_TIME_OUT:
                winner = getWhitePlayer();
                break;
            case CHECKMATE_BLACK_WINS:
            case WHITE_RESIGNED:
            case WHITE_TIME_OUT:
                winner = getBlackPlayer();
                break;
            default:
                List<ItemStack> allItems = getBetItems();
                int halfPoint = allItems.size() / 2;
                
                for (int i = 0; i < halfPoint; i++) {
                    ItemStack item = allItems.get(i);
                    ServerPlayer whitePlayer = getWhitePlayer();
                    if (whitePlayer != null && !whitePlayer.getInventory().add(item.copy())) {
                        whitePlayer.drop(item.copy(), false);
                    }
                }
                
                for (int i = halfPoint; i < allItems.size(); i++) {
                    ItemStack item = allItems.get(i);
                    ServerPlayer blackPlayer = getBlackPlayer();
                    if (blackPlayer != null && !blackPlayer.getInventory().add(item.copy())) {
                        blackPlayer.drop(item.copy(), false);
                    }
                }
                
                ServerPlayer whitePlayer = getWhitePlayer();
                ServerPlayer blackPlayer = getBlackPlayer();
                if (whitePlayer != null) whitePlayer.sendSystemMessage(Component.literal("§7Draw - bet items split between players."));
                if (blackPlayer != null) blackPlayer.sendSystemMessage(Component.literal("§7Draw - bet items split between players."));
                betItems.clear();
                return;
        }
        
        if (winner != null) {
            awardBetToWinner(winner);
        }
    }

    private void updateELORatings(GameState finalState) {
        if (getWhitePlayer() == null || getBlackPlayer() == null) {
            return;
        }
        
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
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
            default:
                result = 0.5;
                break;
        }

        double[] newRatings = calculateELO(whiteData.elo, blackData.elo, whiteData.gamesPlayed, blackData.gamesPlayed, result);

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

        GameManager.getInstance().markDataDirty();
    }

    private double[] calculateELO(int whiteELO, int blackELO, int whiteGamesPlayed, int blackGamesPlayed, double result) {
        double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (double)(blackELO - whiteELO) / 400.0));
        double expectedBlack = 1.0 / (1.0 + Math.pow(10.0, (double)(whiteELO - blackELO) / 400.0));
        
        double whiteKFactor = getPlayerKFactor(whiteELO, whiteGamesPlayed);
        double blackKFactor = getPlayerKFactor(blackELO, blackGamesPlayed);
        
        double newWhiteELO = whiteELO + whiteKFactor * (result - expectedWhite);
        double newBlackELO = blackELO + blackKFactor * ((1.0 - result) - expectedBlack);

        return new double[]{newWhiteELO, newBlackELO};
    }
    
    private double getPlayerKFactor(int playerRating, int totalGamesPlayed) {
        if (playerRating < 2400 && totalGamesPlayed < 30) {
            return 40.0;
        } else if (playerRating < 2400 && totalGamesPlayed >= 30) {
            return 20.0;
        }
        return 10.0;
    }

    /**
     * Handles square selection for piece movement.
     * Manages per-player selection state and move execution.
     * 
     * @param player the player selecting a square
     * @param position the position being selected
     * @return true if selection was processed
     */
    public boolean selectSquare(ServerPlayer player, ChessPosition position) {
        if (!gameActive || (!isPlayerTurn(player) && !analysisMode)) return false;

        ChessPiece piece = board.getPiece(position);
        PieceColor playerColor = getPlayerColor(player);
        PieceColor playerColorForSelection = analysisMode ? board.getCurrentTurn() : playerColor;

        ChessPosition playerSelectedSquare = getPlayerSelectedSquare(player);
        Set<ChessPosition> playerValidMoves = getPlayerValidMoves(player);

        
        if (playerSelectedSquare != null && playerValidMoves.contains(position)) {
            if (analysisMode) {
                ChessMove tempMove = new ChessMove(playerSelectedSquare, position);
                board.makeMove(tempMove);
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

        if (piece != null &&
                ((playerColorForSelection == PieceColor.WHITE && piece.isWhite()) ||
                        (playerColorForSelection == PieceColor.BLACK && piece.isBlack()))) {

            setPlayerSelection(player, position, getValidMovesFrom(position));
            updatePlayerGUIs();
            return true;
        }

        clearPlayerSelection(player);
        updatePlayerGUIs();
        return true;
    }

    private Set<ChessPosition> getValidMovesFrom(ChessPosition from) {
        Set<ChessPosition> moves = new HashSet<>();
        List<ChessMove> legalMoves = board.getLegalMoves();

        PieceColor colorOfPieceAtFrom = null;
        ChessPiece pieceAtFrom = board.getPiece(from);
        if (pieceAtFrom != null) {
            colorOfPieceAtFrom = pieceAtFrom.isWhite() ? PieceColor.WHITE : PieceColor.BLACK;
        }

        if (analysisMode && colorOfPieceAtFrom != null && colorOfPieceAtFrom != board.getCurrentTurn()) {
            ChessBoard tempBoard = board.copy();
            tempBoard.setCurrentTurn(colorOfPieceAtFrom);
            for (ChessMove move : tempBoard.getLegalMoves()) {
                if (move.from.equals(from)) {
                    moves.add(move.to);
                }
            }
        } else {
            for (ChessMove move : legalMoves) {
                if (move.from.equals(from)) {
                    moves.add(move.to);
                }
            }
        }
        return moves;
    }

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
        drawOfferer = new HumanPlayer(player);

        ServerPlayer opponent = getOpponent(player);
        if (opponent != null) {
            opponent.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " offers a draw. Use /chess accept or /chess decline, or GUI buttons."));
            player.sendSystemMessage(Component.literal("§eYou offered a draw."));
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean respondToDraw(ServerPlayer player, boolean accept) {
        if (!gameActive || !drawOffered || analysisMode || (drawOfferer instanceof HumanPlayer && ((HumanPlayer) drawOfferer).getServerPlayer().equals(player))) {
            return false;
        }

        GameParticipant opponentWhoOffered = drawOfferer;
        ServerPlayer opponentPlayer = opponentWhoOffered instanceof HumanPlayer ? ((HumanPlayer) opponentWhoOffered).getServerPlayer() : null;

        if (accept) {
            player.sendSystemMessage(Component.literal("§aYou accepted the draw offer."));
            if (opponentPlayer != null) {
                opponentPlayer.sendSystemMessage(Component.literal("§a" + player.getName().getString() + " accepted your draw offer."));
            }
            endGame(GameState.DRAW_AGREED);
        } else {
            drawOffered = false;
            drawOfferer = null;
            player.sendSystemMessage(Component.literal("§cYou declined the draw offer."));
            if (opponentPlayer != null) {
                opponentPlayer.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " declined your draw offer."));
            }
        }

        updatePlayerGUIs();
        return true;
    }

    public boolean cancelDrawOffer(ServerPlayer player) {
        if (!gameActive || !drawOffered || analysisMode || !(drawOfferer instanceof HumanPlayer && ((HumanPlayer) drawOfferer).getServerPlayer().equals(player))) {
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

    public boolean offerResign(ServerPlayer player) {
        if (!gameActive || resignOffered || analysisMode) return false;

        resignOffered = true;
        resignOfferer = new HumanPlayer(player);

        player.sendSystemMessage(Component.literal("§cYou are about to resign. Click again to confirm."));
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
    
    public boolean confirmResign(ServerPlayer player) {
        if (!gameActive || !resignOffered || analysisMode || !(resignOfferer instanceof HumanPlayer && ((HumanPlayer) resignOfferer).getServerPlayer().equals(player))) {
            return false;
        }
        
        resignOffered = false;
        resignOfferer = null;
        return resign(player);
    }
    
    public boolean cancelResign(ServerPlayer player) {
        if (!resignOffered || !(resignOfferer instanceof HumanPlayer && ((HumanPlayer) resignOfferer).getServerPlayer().equals(player))) {
            return false;
        }
        
        resignOffered = false;
        resignOfferer = null;
        player.sendSystemMessage(Component.literal("§7Resign cancelled."));
        updatePlayerGUIs();
        return true;
    }
    
    public boolean isResignOffered() {
        return resignOffered;
    }
    
    public ServerPlayer getResignOfferer() {
        return resignOfferer instanceof HumanPlayer ? ((HumanPlayer) resignOfferer).getServerPlayer() : null;
    }
    
    // Hint system methods
    public int getHintsAllowed() {
        return hintsAllowed;
    }
    
    public int getHintsUsed(ServerPlayer player) {
        PieceColor color = getPlayerColor(player);
        return color == PieceColor.WHITE ? whiteHintsUsed : blackHintsUsed;
    }
    
    public int getHintsRemaining(ServerPlayer player) {
        return hintsAllowed - getHintsUsed(player);
    }
    
    public boolean canUseHint(ServerPlayer player) {
        return getHintsRemaining(player) > 0 && gameActive && !analysisMode;
    }
    
    public boolean useHint(ServerPlayer player) {
        if (!canUseHint(player)) {
            return false;
        }
        
        PieceColor color = getPlayerColor(player);
        if (color == PieceColor.WHITE) {
            whiteHintsUsed++;
        } else if (color == PieceColor.BLACK) {
            blackHintsUsed++;
        }
        
        return true;
    }

    public void enableAnalysisMode() {
        if(gameActive) {
            ServerPlayer whitePlayer = getWhitePlayer();
            ServerPlayer blackPlayer = getBlackPlayer();
            if (whitePlayer != null) whitePlayer.sendSystemMessage(Component.literal("§dGame is now in analysis mode. Moves are not rated."));
            if (blackPlayer != null) blackPlayer.sendSystemMessage(Component.literal("§dGame is now in analysis mode. Moves are not rated."));
        }
        analysisMode = true;
        requestStockfishAnalysis();
        updatePlayerGUIs();
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
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
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

        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
        if (whitePlayer != null) whitePlayer.sendSystemMessage(Component.literal(message));
        if (blackPlayer != null) blackPlayer.sendSystemMessage(Component.literal(message));
    }

    private void updatePlayerGUIs() {
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
        ChessGUI whiteGUI = GameManager.getInstance().getPlayerGUI(whitePlayer);
        ChessGUI blackGUI = GameManager.getInstance().getPlayerGUI(blackPlayer);

        if (whiteGUI != null && whiteGUI.isOpen()) whiteGUI.updateBoard();
        if (blackGUI != null && blackGUI.isOpen()) blackGUI.updateBoard();

        GameManager.getInstance().updateSpectatorGUIs(this);
    }
    
    private void playMoveSound() {
        if (board.getMoveHistory().isEmpty()) return;
        
        ChessMove lastMove = board.getMoveHistory().get(board.getMoveHistory().size() - 1);
        GameState currentState = board.getGameState();
        
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
        if (whitePlayer != null) {
            ChessSoundManager.playMoveSound(whitePlayer, lastMove, currentState);
        }
        if (blackPlayer != null) {
            ChessSoundManager.playMoveSound(blackPlayer, lastMove, currentState);
        }
    }

    public boolean isPlayerTurn(ServerPlayer player) {
        PieceColor pc = getPlayerColor(player);
        if (pc == null) return false; // Spectator
        return pc == board.getCurrentTurn();
    }

    public PieceColor getPlayerColor(ServerPlayer player) {
        if (player == null) return null;
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
        if (player.equals(whitePlayer)) return PieceColor.WHITE;
        if (player.equals(blackPlayer)) return PieceColor.BLACK;
        return null;
    }
    
    public PieceColor getParticipantColor(GameParticipant participant) {
        if (participant == null) return null;
        if (participant.equals(whiteParticipant)) return PieceColor.WHITE;
        if (participant.equals(blackParticipant)) return PieceColor.BLACK;
        return null;
    }

    public ServerPlayer getOpponent(ServerPlayer player) {
        if (player == null) return null;
        ServerPlayer whitePlayer = getWhitePlayer();
        ServerPlayer blackPlayer = getBlackPlayer();
        if (player.equals(whitePlayer)) return blackPlayer;
        if (player.equals(blackPlayer)) return whitePlayer;
        return null;
    }
    
    public GameParticipant getOpponentParticipant(GameParticipant participant) {
        if (participant == null) return null;
        if (participant.equals(whiteParticipant)) return blackParticipant;
        if (participant.equals(blackParticipant)) return whiteParticipant;
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
    public ChessBoard getBoard() { return board; }
    public TimeControl getTimeControl() { return timeControl; }
    public boolean isGameActive() { return gameActive; }
    public int getWhiteTimeLeft() { return whiteTimeLeft; }
    public int getBlackTimeLeft() { return blackTimeLeft; }
    public long getStartTime() { return startTime; }
    public List<Long> getMoveTimestamps() { return Collections.unmodifiableList(moveTimestamps); }

    public ChessPosition getSelectedSquare(ServerPlayer player) {
        return getPlayerSelectedSquare(player);
    }

    public Set<ChessPosition> getValidMoves(ServerPlayer player) {
        return getPlayerValidMoves(player);
    }

    @Deprecated
    public ChessPosition getSelectedSquare() {
        return whiteSelectedSquare;
    }

    @Deprecated
    public Set<ChessPosition> getValidMoves() {
        return whiteValidMoves;
    }

    public boolean isDrawOffered() { return drawOffered; }
    public ServerPlayer getDrawOfferer() { return drawOfferer instanceof HumanPlayer ? ((HumanPlayer) drawOfferer).getServerPlayer() : null; }
    public boolean isAnalysisMode() { return analysisMode; }
    public Map<String, Object> getAnalysisData() { return analysisData; }
    
    public int getWhiteHintsUsed() { return whiteHintsUsed; }
    public int getBlackHintsUsed() { return blackHintsUsed; }
    
    public void incrementWhiteHints() { whiteHintsUsed++; }
    public void incrementBlackHints() { blackHintsUsed++; }
    
    public void setBetItems(List<ItemStack> items) {
        this.betItems = new ArrayList<>(items);
    }
    
    public List<ItemStack> getBetItems() {
        return new ArrayList<>(betItems);
    }
    
    public boolean hasBet() {
        return !betItems.isEmpty();
    }
    
    public void awardBetToWinner(ServerPlayer winner) {
        if (!hasBet() || winner == null) return;
        
        for (ItemStack item : betItems) {
            if (!winner.getInventory().add(item.copy())) {
                winner.drop(item.copy(), false);
            }
        }
        
        winner.sendSystemMessage(Component.literal("§aYou won " + betItems.size() + " bet items!"));
        betItems.clear();
    }
}