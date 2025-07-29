package com.leclowndu93150.guichess.game.core;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.game.players.BotPlayer;
import com.leclowndu93150.guichess.game.players.GameParticipant;
import com.leclowndu93150.guichess.data.models.BotProfile;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import com.leclowndu93150.guichess.util.time.TimeControl;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.engine.integration.StockfishIntegration;
import com.leclowndu93150.guichess.gui.game.ChessGUI;
import com.leclowndu93150.guichess.gui.game.SpectatorGUI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bot vs bot chess games with spectators.
 * Uses different ELO levels and simulated thinking time.
 */
public class BotVsBotGame extends ChessGame {
    private final BotPlayer whiteBot;
    private final BotPlayer blackBot;
    private final ServerPlayer initiator;
    
    private boolean isThinking = false;
    private CompletableFuture<Void> currentBotMove = null;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private static final Pattern UCI_MOVE_PATTERN = Pattern.compile("([a-h][1-8])([a-h][1-8])([qrbn])?");
    
    public BotVsBotGame(ServerPlayer initiator, int whiteElo, int blackElo, TimeControl timeControl) {
        super(
            new BotPlayer(new BotProfile(whiteElo)),
            new BotPlayer(new BotProfile(blackElo)),
            timeControl,
            0
        );
        
        this.whiteBot = (BotPlayer) getWhiteParticipant();
        this.blackBot = (BotPlayer) getBlackParticipant();
        this.initiator = initiator;
        
        // Initialize timers for bot vs bot game
        initializeTimers();
        
        // Start the first move after a short delay
        scheduler.schedule(this::makeNextBotMove, 2, TimeUnit.SECONDS);
    }
    
    // Bot vs bot games start timer immediately since both players are AI
    private void initializeTimers() {
        timerStarted = true;
    }
    
    private void makeNextBotMove() {
        if (isThinking || !isGameActive()) return;
        
        isThinking = true;
        BotPlayer currentBot = board.getCurrentTurn() == PieceColor.WHITE ? whiteBot : blackBot;
        
        // Notify spectators
        broadcastToSpectators(Component.literal("§7" + currentBot.getName() + " is thinking..."));
        
        if (currentBotMove != null && !currentBotMove.isDone()) {
            currentBotMove.cancel(true);
        }
        
        String fen = board.toFEN();
        configureStockfishForElo(currentBot.getTargetElo());
        
        currentBotMove = CompletableFuture
            .runAsync(() -> {
                try {
                    // Simulate thinking time based on ELO
                    int thinkingTime = calculateThinkingTime(currentBot.getTargetElo());
                    Thread.sleep(thinkingTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                
                StockfishEngineManager.getInstance().analyzePosition(fen)
                    .thenAccept(result -> {
                        if (!isGameActive() || board.getCurrentTurn() != (currentBot == whiteBot ? PieceColor.WHITE : PieceColor.BLACK)) {
                            isThinking = false;
                            return;
                        }
                        
                        if (result.bestMove != null) {
                            ChessMove move = parseUCIMove(result.bestMove);
                            
                            if (move != null) {
                                GameManager.getInstance().getServer().execute(() -> {
                                    if (isValidBotMove(move, currentBot)) {
                                        if (board.makeMove(move)) {
                                            updateTimersAfterMove();
                                            
                                            // Play sound for initiator
                                            if (initiator != null) {
                                                ChessSoundManager.playMoveSound(initiator, move, board.getGameState());
                                            }
                                            
                                            // Update all GUIs
                                            updateAllGUIs();
                                            
                                            GameState currentState = board.getGameState();
                                            if (currentState != GameState.WHITE_TURN && currentState != GameState.BLACK_TURN &&
                                                currentState != GameState.CHECK_WHITE && currentState != GameState.CHECK_BLACK) {
                                                endGame(currentState);
                                            } else {
                                                // Schedule next move
                                                isThinking = false;
                                                scheduler.schedule(this::makeNextBotMove, 1, TimeUnit.SECONDS);
                                            }
                                        }
                                    }
                                    isThinking = false;
                                });
                            } else {
                                broadcastToSpectators(Component.literal("§c" + currentBot.getName() + " failed to parse move!"));
                                isThinking = false;
                            }
                        } else {
                            broadcastToSpectators(Component.literal("§c" + currentBot.getName() + " failed to find a move!"));
                            if (result.error != null) {
                                broadcastToSpectators(Component.literal("§cError: " + result.error));
                            }
                            isThinking = false;
                        }
                    })
                    .exceptionally(throwable -> {
                        broadcastToSpectators(Component.literal("§cBot error: " + throwable.getMessage()));
                        throwable.printStackTrace();
                        isThinking = false;
                        return null;
                    });
            });
    }
    
    private int calculateThinkingTime(int elo) {
        // Higher ELO bots think longer
        if (elo < 1000) return 500 + (int)(Math.random() * 500);
        if (elo < 1500) return 1000 + (int)(Math.random() * 1000);
        if (elo < 2000) return 1500 + (int)(Math.random() * 1500);
        return 2000 + (int)(Math.random() * 2000);
    }
    
    private void configureStockfishForElo(int targetElo) {
        StockfishEngineManager stockfish = StockfishEngineManager.getInstance();
        
        int depth;
        int analysisTime;
        
        if (targetElo < 400) {
            stockfish.setSkillLevel(-9);
            depth = 5;
            analysisTime = 1000;
        } else if (targetElo < 650) {
            stockfish.setSkillLevel(-5);
            depth = 5;
            analysisTime = 1000;
        } else if (targetElo < 950) {
            stockfish.setSkillLevel(-1);
            depth = 5;
            analysisTime = 1000;
        } else if (targetElo < 1300) {
            stockfish.setSkillLevel(3);
            depth = 5;
            analysisTime = 1000;
        } else if (targetElo < 1700) {
            stockfish.setSkillLevel(7);
            depth = 5;
            analysisTime = 1000;
        } else if (targetElo < 2100) {
            stockfish.setSkillLevel(11);
            depth = 8;
            analysisTime = 1500;
        } else if (targetElo < 2550) {
            stockfish.setSkillLevel(15);
            depth = 13;
            analysisTime = 2000;
        } else {
            stockfish.setSkillLevel(20);
            depth = 22;
            analysisTime = 3000;
        }
        
        stockfish.setAnalysisDepth(depth);
        stockfish.setAnalysisTime(analysisTime);
    }
    
    private ChessMove parseUCIMove(String uciMove) {
        Matcher matcher = UCI_MOVE_PATTERN.matcher(uciMove.toLowerCase());
        if (!matcher.matches()) {
            return null;
        }
        
        String fromSquare = matcher.group(1);
        String toSquare = matcher.group(2);
        String promotion = matcher.group(3);
        
        ChessPosition from = parseSquare(fromSquare);
        ChessPosition to = parseSquare(toSquare);
        
        if (from == null || to == null) {
            return null;
        }
        
        for (ChessMove move : board.getLegalMoves()) {
            if (move.from.equals(from) && move.to.equals(to)) {
                if (promotion != null) {
                    PieceType promotionType = parsePromotion(promotion);
                    if (move.promotionPiece == promotionType) {
                        return move;
                    }
                } else if (move.promotionPiece == null) {
                    return move;
                }
            }
        }
        
        return null;
    }
    
    private ChessPosition parseSquare(String square) {
        if (square.length() != 2) return null;
        
        char file = square.charAt(0);
        char rank = square.charAt(1);
        
        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') {
            return null;
        }
        
        return new ChessPosition(file - 'a', rank - '1');
    }
    
    private PieceType parsePromotion(String promotion) {
        return switch (promotion) {
            case "q" -> PieceType.QUEEN;
            case "r" -> PieceType.ROOK;
            case "b" -> PieceType.BISHOP;
            case "n" -> PieceType.KNIGHT;
            default -> null;
        };
    }
    
    private boolean isValidBotMove(ChessMove move, BotPlayer bot) {
        return board.getLegalMoves().contains(move) &&
               board.getPiece(move.from) != null &&
               board.getPiece(move.from).isWhite() == (bot == whiteBot);
    }
    
    private void broadcastToSpectators(Component message) {
        if (initiator != null) {
            initiator.sendSystemMessage(message);
        }
    }
    
    private void updateAllGUIs() {
        // Update spectator GUIs
        GameManager.getInstance().updateSpectatorGUIs(this);
    }
    
    @Override
    public void endGame(GameState finalState) {
        if (currentBotMove != null && !currentBotMove.isDone()) {
            currentBotMove.cancel(true);
        }
        isThinking = false;
        scheduler.shutdown();
        
        super.endGame(finalState);
    }
    
    public void addInitiatorAsSpectator() {
        // Add spectator through GameManager to ensure proper registration and updates
        GameManager.getInstance().addSpectatorToBotVsBotGame(this, initiator);
    }
    
    @Override
    public boolean makeMove(ServerPlayer player, ChessPosition from, ChessPosition to, PieceType promotion) {
        // Bot vs Bot games don't accept player moves
        return false;
    }
    
    public BotPlayer getWhiteBot() {
        return whiteBot;
    }
    
    public BotPlayer getBlackBot() {
        return blackBot;
    }
    
    public ServerPlayer getInitiator() {
        return initiator;
    }
}