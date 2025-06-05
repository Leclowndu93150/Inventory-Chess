package com.leclowndu93150.guichess.util.visual;

import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceType;

import java.util.LinkedHashMap;
import java.util.Map;

public class OverlayModelDataRegistry {

    private static final Map<String, Integer> OVERLAY_MODEL_DATA = new LinkedHashMap<>();
    private static final int STARTING_MODEL_DATA = 500;

    static {
        generateOverlayModelData();
    }

    private static void generateOverlayModelData() {
        int modelDataCounter = STARTING_MODEL_DATA;

        ChessPiece[] pieces = {
                ChessPiece.WHITE_KING, ChessPiece.WHITE_QUEEN, ChessPiece.WHITE_ROOK,
                ChessPiece.WHITE_BISHOP, ChessPiece.WHITE_KNIGHT, ChessPiece.WHITE_PAWN,
                ChessPiece.BLACK_KING, ChessPiece.BLACK_QUEEN, ChessPiece.BLACK_ROOK,
                ChessPiece.BLACK_BISHOP, ChessPiece.BLACK_KNIGHT, ChessPiece.BLACK_PAWN
        };

        for (ChessPiece piece : pieces) {
            String baseName = piece.getModelName();

            OVERLAY_MODEL_DATA.put(baseName + "_light", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_dark", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_selected_light", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_selected_dark", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_capture_light", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_capture_dark", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_lastmove_light", modelDataCounter++);
            OVERLAY_MODEL_DATA.put(baseName + "_lastmove_dark", modelDataCounter++);

            if (piece.getType() == PieceType.KING) {
                OVERLAY_MODEL_DATA.put(baseName + "_check_light", modelDataCounter++);
                OVERLAY_MODEL_DATA.put(baseName + "_check_dark", modelDataCounter++);
            }
        }
    }

    public static Integer getModelData(String overlayKey) {
        return OVERLAY_MODEL_DATA.get(overlayKey);
    }

    public static Map<String, Integer> getAllModelData() {
        return new LinkedHashMap<>(OVERLAY_MODEL_DATA);
    }

    public static int getStartingModelData() {
        return STARTING_MODEL_DATA;
    }
}