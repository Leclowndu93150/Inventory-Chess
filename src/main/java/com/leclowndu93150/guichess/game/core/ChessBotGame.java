package com.leclowndu93150.guichess.game.core;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.pieces.PieceType;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.game.players.BotPlayer;
import com.leclowndu93150.guichess.game.players.GameParticipant;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import com.leclowndu93150.guichess.util.time.TimeControl;
import com.leclowndu93150.guichess.engine.integration.StockfishEngineManager;
import com.leclowndu93150.guichess.engine.integration.StockfishIntegration;
import com.leclowndu93150.guichess.gui.game.ChessGUI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChessBotGame extends ChessGame {
    private final BotPlayer botPlayer;
    private final PieceColor botColor;
    private boolean isThinking = false;
    private CompletableFuture<Void> currentBotMove = null;
    
    private static final Pattern UCI_MOVE_PATTERN = Pattern.compile("([a-h][1-8])([a-h][1-8])([qrbn])?");
    
    public ChessBotGame(GameParticipant whiteParticipant, GameParticipant blackParticipant, TimeControl timeControl, int hintsAllowed) {
        super(whiteParticipant, blackParticipant, timeControl, hintsAllowed);
        if (whiteParticipant.isBot()) {
            this.botPlayer = (BotPlayer) whiteParticipant;
            this.botColor = PieceColor.WHITE;
        } else {
            this.botPlayer = (BotPlayer) blackParticipant;
            this.botColor = PieceColor.BLACK;
        }
        
        if (botColor == PieceColor.WHITE) {
            GameManager.getInstance().getServer().execute(() -> {
                if (isGameActive() && board.getMoveHistory().isEmpty()) {
                    makeBotMove();
                }
            });
        }
    }
    
    @Override
    public boolean makeMove(ServerPlayer player, ChessPosition from, ChessPosition to, PieceType promotion) {
        boolean result = super.makeMove(player, from, to, promotion);
        
        if (result && board.getCurrentTurn() == botColor && isGameActive()) {
            makeBotMove();
        }
        
        return result;
    }
    
    
    private void makeBotMove() {
        if (isThinking || !isGameActive()) return;
        
        isThinking = true;
        final ServerPlayer humanPlayer = getHumanPlayer();
        
        if (humanPlayer != null) {
            humanPlayer.sendSystemMessage(Component.literal("§7Bot is thinking..."));
        }
        
        if (currentBotMove != null && !currentBotMove.isDone()) {
            currentBotMove.cancel(true);
        }
        
        String fen = board.toFEN();
        
        configureStockfishForElo(botPlayer.getTargetElo());
        
        currentBotMove = CompletableFuture
            .runAsync(() -> {
                try {
                    Thread.sleep(500 + (int)(Math.random() * 1500));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                
                StockfishEngineManager.getInstance().analyzePosition(fen)
                    .thenAccept(result -> {
                        if (!isGameActive() || board.getCurrentTurn() != botColor) {
                            isThinking = false;
                            return;
                        }
                        
                        if (result.bestMove != null) {
                            ChessMove move = parseUCIMove(result.bestMove);
                            
                            if (move != null) {
                                GameManager.getInstance().getServer().execute(() -> {
                                    if (isValidBotMove(move)) {
                                        if (board.makeMove(move)) {
                                            updateTimersAfterMove();
                                            
                                            if (!timerStarted && board.getCurrentTurn() == PieceColor.BLACK) {
                                                timerStarted = true;
                                            }
                                            ServerPlayer human = getHumanPlayer();
                                            if (human != null) {
                                                ChessSoundManager.playMoveSound(
                                                    human, move, board.getGameState());
                                            }
                                            
                                            ServerPlayer whitePlayer = getWhitePlayer();
                                            ServerPlayer blackPlayer = getBlackPlayer();
                                            
                                            ChessGUI whiteGUI = whitePlayer != null ? GameManager.getInstance().getPlayerGUI(whitePlayer) : null;
                                            ChessGUI blackGUI = blackPlayer != null ? GameManager.getInstance().getPlayerGUI(blackPlayer) : null;
                                            
                                            if (whiteGUI != null && whiteGUI.isOpen()) whiteGUI.updateBoard();
                                            if (blackGUI != null && blackGUI.isOpen()) blackGUI.updateBoard();
                                            
                                            GameState currentState = board.getGameState();
                                            if (currentState != GameState.WHITE_TURN && currentState != GameState.BLACK_TURN &&
                                                currentState != GameState.CHECK_WHITE && currentState != GameState.CHECK_BLACK) {
                                                endGame(currentState);
                                            }
                                        } else {
                                            if (humanPlayer != null) humanPlayer.sendSystemMessage(Component.literal("§cBot move failed to execute!"));
                                        }
                                    } else {
                                        if (humanPlayer != null) humanPlayer.sendSystemMessage(Component.literal("§cBot made invalid move!"));
                                    }
                                    isThinking = false;
                                });
                            } else {
                                if (humanPlayer != null) {
                                    humanPlayer.sendSystemMessage(Component.literal("§cBot failed to parse move: " + result.bestMove));
                                }
                                isThinking = false;
                            }
                        } else {
                            if (humanPlayer != null) {
                                humanPlayer.sendSystemMessage(Component.literal("§cBot failed to find a move!"));
                                if (result.error != null) {
                                    humanPlayer.sendSystemMessage(Component.literal("§cError: " + result.error));
                                }
                            }
                            isThinking = false;
                        }
                    })
                    .exceptionally(throwable -> {
                        if (humanPlayer != null) {
                            humanPlayer.sendSystemMessage(Component.literal("§cBot error: " + throwable.getMessage()));
                            throwable.printStackTrace();
                        }
                        isThinking = false;
                        return null;
                    });
            });
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
    
    private boolean isValidBotMove(ChessMove move) {
        return board.getLegalMoves().contains(move) &&
               board.getPiece(move.from) != null &&
               board.getPiece(move.from).isWhite() == (botColor == PieceColor.WHITE);
    }
    
    private ServerPlayer getHumanPlayer() {
        return botColor == PieceColor.WHITE ? getBlackPlayer() : getWhitePlayer();
    }
    
    @Override
    public void endGame(GameState finalState) {
        if (currentBotMove != null && !currentBotMove.isDone()) {
            currentBotMove.cancel(true);
        }
        isThinking = false;
        
        super.endGame(finalState);
    }
    
    public String getBotName() {
        return botPlayer.getName();
    }
    
    public BotPlayer getBotPlayer() {
        return botPlayer;
    }
}