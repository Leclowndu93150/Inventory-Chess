package com.leclowndu93150.guichess.util.audio;

import com.leclowndu93150.guichess.chess.board.ChessMove;
import com.leclowndu93150.guichess.chess.util.GameState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Manages sound effects for chess game events and user interface interactions.
 * 
 * <p>Provides contextual audio feedback for different types of chess moves and game states,
 * including captures, checks, checkmates, castling, and UI interactions.
 * 
 * @author GUIChess
 * @since 1.0
 */
public class ChessSoundManager {

    /**
     * Plays appropriate sound effect for a chess move based on move type and game state.
     * 
     * @param player the player to play the sound for
     * @param move the chess move that was made
     * @param gameState the current state of the game
     */
    public static void playMoveSound(ServerPlayer player, ChessMove move, GameState gameState) {
        if (player == null || move == null) return;

        SoundEvent sound;
        float volume = 0.5f;
        float pitch = 1.0f;

        if (gameState == GameState.CHECKMATE_WHITE_WINS || gameState == GameState.CHECKMATE_BLACK_WINS) {
            sound = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            volume = 0.7f;
        } else if (move.isCheckmate) {
            sound = SoundEvents.ANVIL_PLACE;
            volume = 0.6f;
        } else if (move.isCheck) {
            sound = SoundEvents.BELL_BLOCK;
            pitch = 1.2f;
        } else if (move.isCastling) {
            sound = SoundEvents.PISTON_EXTEND;
            pitch = 0.8f;
        } else if (move.isCapture) {
            sound = SoundEvents.ITEM_BREAK;
            pitch = 1.1f;
        } else if (move.promotionPiece != null) {
            sound = SoundEvents.PLAYER_LEVELUP;
            volume = 0.6f;
        } else {
            sound = SoundEvents.NOTE_BLOCK_HARP.value();
            pitch = 1.0f + (float) Math.random() * 0.2f - 0.1f;
        }

        playSound(player, sound, volume, pitch);
    }

    /**
     * Plays sound effect for game ending events.
     * 
     * @param player the player to play the sound for
     * @param gameState the final game state
     */
    public static void playGameEndSound(ServerPlayer player, GameState gameState) {
        if (player == null) return;

        SoundEvent sound;
        float volume = 0.6f;
        float pitch = 1.0f;

        switch (gameState) {
            case CHECKMATE_WHITE_WINS, CHECKMATE_BLACK_WINS:
                sound = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
                break;
            case STALEMATE:
            case DRAW_AGREED:
            case DRAW_FIFTY_MOVE:
            case DRAW_THREEFOLD:
            case DRAW_INSUFFICIENT:
                sound = SoundEvents.VILLAGER_TRADE;
                break;
            case WHITE_RESIGNED:
            case BLACK_RESIGNED:
                sound = SoundEvents.VILLAGER_DEATH;
                volume = 0.4f;
                break;
            case WHITE_TIME_OUT:
            case BLACK_TIME_OUT:
                sound = SoundEvents.BELL_BLOCK;
                pitch = 0.5f;
                break;
            default:
                return;
        }

        playSound(player, sound, volume, pitch);
    }

    public static void playUISound(ServerPlayer player, UISound uiSound) {
        if (player == null) return;

        SoundEvent sound;
        float volume = 0.5f;
        float pitch = 1.0f;

        switch (uiSound) {
            case CLICK:
                sound = SoundEvents.UI_BUTTON_CLICK.value();
                break;
            case SELECT:
                sound = SoundEvents.UI_BUTTON_CLICK.value();
                pitch = 1.2f;
                break;
            case ERROR:
                sound = SoundEvents.VILLAGER_NO;
                break;
            case SUCCESS:
                sound = SoundEvents.VILLAGER_YES;
                break;
            case DRAW_OFFER:
                sound = SoundEvents.VILLAGER_TRADE;
                break;
            case PROMOTION_DIALOG:
                sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
                break;
            case HINT:
                sound = SoundEvents.EXPERIENCE_ORB_PICKUP;
                pitch = 1.3f;
                break;
            case ANALYSIS:
                sound = SoundEvents.ENCHANTMENT_TABLE_USE;
                break;
            case RESIGN:
                sound = SoundEvents.VILLAGER_DEATH;
                volume = 0.4f;
                break;
            default:
                return;
        }

        playSound(player, sound, volume, pitch);
    }

    private static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public enum UISound {
        CLICK,
        SELECT,
        ERROR,
        SUCCESS,
        DRAW_OFFER,
        PROMOTION_DIALOG,
        HINT,
        ANALYSIS,
        RESIGN
    }
}