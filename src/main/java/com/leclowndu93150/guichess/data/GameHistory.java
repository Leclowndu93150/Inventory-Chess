package com.leclowndu93150.guichess.data;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.GameState;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete match history data structure that stores all information needed for analysis and replay.
 * Includes move-by-move data, timing information, player details, and game metadata.
 */
public class GameHistory {
    public final UUID gameId;
    public final UUID whitePlayerId;
    public final UUID blackPlayerId;
    public final String whitePlayerName;
    public final String blackPlayerName;
    public final TimeControl timeControl;
    public final LocalDateTime startTime;
    public final LocalDateTime endTime;
    public final GameState finalResult;
    public final String initialFen;
    public final List<MoveRecord> moves;
    public final int whiteEloAtStart;
    public final int blackEloAtStart;
    public final int whiteEloChange;
    public final int blackEloChange;
    public final boolean wasRated;
    public final int hintsAllowed;
    public final int whiteHintsUsed;
    public final int blackHintsUsed;
    public final boolean hadBet;
    public final String terminationReason;

    public GameHistory(UUID gameId, UUID whitePlayerId, UUID blackPlayerId, 
                      String whitePlayerName, String blackPlayerName,
                      TimeControl timeControl, LocalDateTime startTime, LocalDateTime endTime,
                      GameState finalResult, String initialFen, List<MoveRecord> moves,
                      int whiteEloAtStart, int blackEloAtStart, int whiteEloChange, int blackEloChange,
                      boolean wasRated, int hintsAllowed, int whiteHintsUsed, int blackHintsUsed,
                      boolean hadBet, String terminationReason) {
        this.gameId = gameId;
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.whitePlayerName = whitePlayerName;
        this.blackPlayerName = blackPlayerName;
        this.timeControl = timeControl;
        this.startTime = startTime;
        this.endTime = endTime;
        this.finalResult = finalResult;
        this.initialFen = initialFen;
        this.moves = moves != null ? new ArrayList<>(moves) : new ArrayList<>();
        this.whiteEloAtStart = whiteEloAtStart;
        this.blackEloAtStart = blackEloAtStart;
        this.whiteEloChange = whiteEloChange;
        this.blackEloChange = blackEloChange;
        this.wasRated = wasRated;
        this.hintsAllowed = hintsAllowed;
        this.whiteHintsUsed = whiteHintsUsed;
        this.blackHintsUsed = blackHintsUsed;
        this.hadBet = hadBet;
        this.terminationReason = terminationReason;
    }

    /**
     * Individual move record containing all data for a single move in the game.
     */
    public static class MoveRecord {
        public final ChessMove move;
        public final String moveNotation;
        public final String fenBefore;
        public final String fenAfter;
        public final int whiteTimeLeft;
        public final int blackTimeLeft;
        public final int moveTimeMs;
        public final boolean wasCheck;
        public final boolean wasCheckmate;
        public final boolean wasCapture;
        public final boolean wasCastling;
        public final boolean wasEnPassant;
        public final boolean wasPromotion;
        public final LocalDateTime timestamp;
        public String stockfishEvaluation; // Populated during analysis
        public String bestMove; // Populated during analysis
        public int centipawnLoss; // Populated during analysis
        public boolean isBlunder; // Populated during analysis
        public boolean isMistake; // Populated during analysis
        public boolean isInaccuracy; // Populated during analysis
        public boolean isBrilliant; // Populated during analysis
        public boolean isGood; // Populated during analysis

        public MoveRecord(ChessMove move, String moveNotation, String fenBefore, String fenAfter,
                         int whiteTimeLeft, int blackTimeLeft, int moveTimeMs,
                         boolean wasCheck, boolean wasCheckmate, boolean wasCapture,
                         boolean wasCastling, boolean wasEnPassant, boolean wasPromotion,
                         LocalDateTime timestamp) {
            this.move = move;
            this.moveNotation = moveNotation;
            this.fenBefore = fenBefore;
            this.fenAfter = fenAfter;
            this.whiteTimeLeft = whiteTimeLeft;
            this.blackTimeLeft = blackTimeLeft;
            this.moveTimeMs = moveTimeMs;
            this.wasCheck = wasCheck;
            this.wasCheckmate = wasCheckmate;
            this.wasCapture = wasCapture;
            this.wasCastling = wasCastling;
            this.wasEnPassant = wasEnPassant;
            this.wasPromotion = wasPromotion;
            this.timestamp = timestamp;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put("move", move.toNBT());
            tag.putString("moveNotation", moveNotation);
            tag.putString("fenBefore", fenBefore);
            tag.putString("fenAfter", fenAfter);
            tag.putInt("whiteTimeLeft", whiteTimeLeft);
            tag.putInt("blackTimeLeft", blackTimeLeft);
            tag.putInt("moveTimeMs", moveTimeMs);
            tag.putBoolean("wasCheck", wasCheck);
            tag.putBoolean("wasCheckmate", wasCheckmate);
            tag.putBoolean("wasCapture", wasCapture);
            tag.putBoolean("wasCastling", wasCastling);
            tag.putBoolean("wasEnPassant", wasEnPassant);
            tag.putBoolean("wasPromotion", wasPromotion);
            tag.putLong("timestamp", timestamp.toEpochSecond(ZoneOffset.UTC));
            
            // Analysis data
            if (stockfishEvaluation != null) tag.putString("stockfishEvaluation", stockfishEvaluation);
            if (bestMove != null) tag.putString("bestMove", bestMove);
            tag.putInt("centipawnLoss", centipawnLoss);
            tag.putBoolean("isBlunder", isBlunder);
            tag.putBoolean("isMistake", isMistake);
            tag.putBoolean("isInaccuracy", isInaccuracy);
            tag.putBoolean("isBrilliant", isBrilliant);
            tag.putBoolean("isGood", isGood);
            
            return tag;
        }

        public static MoveRecord fromNBT(CompoundTag tag) {
            MoveRecord record = new MoveRecord(
                ChessMove.fromNBT(tag.getCompound("move")),
                tag.getString("moveNotation"),
                tag.getString("fenBefore"),
                tag.getString("fenAfter"),
                tag.getInt("whiteTimeLeft"),
                tag.getInt("blackTimeLeft"),
                tag.getInt("moveTimeMs"),
                tag.getBoolean("wasCheck"),
                tag.getBoolean("wasCheckmate"),
                tag.getBoolean("wasCapture"),
                tag.getBoolean("wasCastling"),
                tag.getBoolean("wasEnPassant"),
                tag.getBoolean("wasPromotion"),
                LocalDateTime.ofEpochSecond(tag.getLong("timestamp"), 0, ZoneOffset.UTC)
            );
            
            // Load analysis data
            if (tag.contains("stockfishEvaluation")) {
                record.stockfishEvaluation = tag.getString("stockfishEvaluation");
            }
            if (tag.contains("bestMove")) {
                record.bestMove = tag.getString("bestMove");
            }
            record.centipawnLoss = tag.getInt("centipawnLoss");
            record.isBlunder = tag.getBoolean("isBlunder");
            record.isMistake = tag.getBoolean("isMistake");
            record.isInaccuracy = tag.getBoolean("isInaccuracy");
            record.isBrilliant = tag.getBoolean("isBrilliant");
            record.isGood = tag.getBoolean("isGood");
            
            return record;
        }

        public PieceColor getMovingColor(int moveIndex) {
            return moveIndex % 2 == 0 ? PieceColor.WHITE : PieceColor.BLACK;
        }
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("gameId", gameId);
        tag.putUUID("whitePlayerId", whitePlayerId);
        tag.putUUID("blackPlayerId", blackPlayerId);
        tag.putString("whitePlayerName", whitePlayerName);
        tag.putString("blackPlayerName", blackPlayerName);
        tag.putString("timeControl", timeControl.name());
        tag.putLong("startTime", startTime.toEpochSecond(ZoneOffset.UTC));
        tag.putLong("endTime", endTime.toEpochSecond(ZoneOffset.UTC));
        tag.putString("finalResult", finalResult.name());
        tag.putString("initialFen", initialFen);
        tag.putInt("whiteEloAtStart", whiteEloAtStart);
        tag.putInt("blackEloAtStart", blackEloAtStart);
        tag.putInt("whiteEloChange", whiteEloChange);
        tag.putInt("blackEloChange", blackEloChange);
        tag.putBoolean("wasRated", wasRated);
        tag.putInt("hintsAllowed", hintsAllowed);
        tag.putInt("whiteHintsUsed", whiteHintsUsed);
        tag.putInt("blackHintsUsed", blackHintsUsed);
        tag.putBoolean("hadBet", hadBet);
        tag.putString("terminationReason", terminationReason);

        ListTag movesTag = new ListTag();
        for (MoveRecord move : moves) {
            movesTag.add(move.toNBT());
        }
        tag.put("moves", movesTag);

        return tag;
    }

    public static GameHistory fromNBT(CompoundTag tag) {
        List<MoveRecord> moves = new ArrayList<>();
        ListTag movesTag = tag.getList("moves", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < movesTag.size(); i++) {
            moves.add(MoveRecord.fromNBT(movesTag.getCompound(i)));
        }

        return new GameHistory(
            tag.getUUID("gameId"),
            tag.getUUID("whitePlayerId"),
            tag.getUUID("blackPlayerId"),
            tag.getString("whitePlayerName"),
            tag.getString("blackPlayerName"),
            TimeControl.valueOf(tag.getString("timeControl")),
            LocalDateTime.ofEpochSecond(tag.getLong("startTime"), 0, ZoneOffset.UTC),
            LocalDateTime.ofEpochSecond(tag.getLong("endTime"), 0, ZoneOffset.UTC),
            GameState.valueOf(tag.getString("finalResult")),
            tag.getString("initialFen"),
            moves,
            tag.getInt("whiteEloAtStart"),
            tag.getInt("blackEloAtStart"),
            tag.getInt("whiteEloChange"),
            tag.getInt("blackEloChange"),
            tag.getBoolean("wasRated"),
            tag.getInt("hintsAllowed"),
            tag.getInt("whiteHintsUsed"),
            tag.getInt("blackHintsUsed"),
            tag.getBoolean("hadBet"),
            tag.getString("terminationReason")
        );
    }

    public String getFormattedDate() {
        return startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getResultString() {
        return switch (finalResult) {
            case CHECKMATE_WHITE_WINS -> "1-0";
            case CHECKMATE_BLACK_WINS -> "0-1";
            case STALEMATE, DRAW_FIFTY_MOVE, DRAW_THREEFOLD, DRAW_INSUFFICIENT, DRAW_AGREEMENT -> "½-½";
            case RESIGN_WHITE_WINS -> "1-0 (Black resigned)";
            case RESIGN_BLACK_WINS -> "0-1 (White resigned)";
            case TIME_OUT_WHITE_WINS -> "1-0 (Black time out)";
            case TIME_OUT_BLACK_WINS -> "0-1 (White time out)";
            default -> "Unknown";
        };
    }

    public int getTotalMoves() {
        return moves.size();
    }

    public String getDuration() {
        long seconds = java.time.Duration.between(startTime, endTime).getSeconds();
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public boolean isPlayerWhite(UUID playerId) {
        return whitePlayerId.equals(playerId);
    }

    public String getOpponentName(UUID playerId) {
        return isPlayerWhite(playerId) ? blackPlayerName : whitePlayerName;
    }

    public int getPlayerEloAtStart(UUID playerId) {
        return isPlayerWhite(playerId) ? whiteEloAtStart : blackEloAtStart;
    }

    public int getOpponentEloAtStart(UUID playerId) {
        return isPlayerWhite(playerId) ? blackEloAtStart : whiteEloAtStart;
    }

    public int getPlayerEloChange(UUID playerId) {
        return isPlayerWhite(playerId) ? whiteEloChange : blackEloChange;
    }
}