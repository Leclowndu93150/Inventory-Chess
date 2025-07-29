package com.leclowndu93150.guichess.util.visual;

import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceType;

import java.util.Map;

/**
 * Maps piece visual states to model data values.
 * Handles selection, capture highlights, etc.
 */
public class PieceOverlayHelper {

    public enum OverlayType {
        NORMAL_LIGHT,
        NORMAL_DARK,
        SELECTED_LIGHT,
        SELECTED_DARK,
        CAPTURE_LIGHT,
        CAPTURE_DARK,
        LASTMOVE_LIGHT,
        LASTMOVE_DARK,
        CHECK_LIGHT,    // Kings only
        CHECK_DARK      // Kings only
    }

    public static int getOverlayModelData(ChessPiece piece, OverlayType overlayType, boolean isLightSquare) {
        if (piece == null) return 0;

        String suffix = switch (overlayType) {
            case NORMAL_LIGHT -> "_light";
            case NORMAL_DARK -> "_dark";
            case SELECTED_LIGHT -> "_selected_light";
            case SELECTED_DARK -> "_selected_dark";
            case CAPTURE_LIGHT -> "_capture_light";
            case CAPTURE_DARK -> "_capture_dark";
            case LASTMOVE_LIGHT -> "_lastmove_light";
            case LASTMOVE_DARK -> "_lastmove_dark";
            case CHECK_LIGHT -> "_check_light";
            case CHECK_DARK -> "_check_dark";
        };

        if (overlayType == OverlayType.NORMAL_LIGHT || overlayType == OverlayType.NORMAL_DARK) {
            suffix = isLightSquare ? "_light" : "_dark";
        }

        String key = piece.getModelName() + suffix;
        Integer modelData = OverlayModelDataRegistry.getModelData(key);

        return modelData != null ? modelData : piece.getModelData();
    }

    public static int getModelDataForPieceState(ChessPiece piece, boolean isLightSquare,
                                                boolean isSelected, boolean canBeCaptured,
                                                boolean isLastMoved, boolean isInCheck) {
        if (piece == null) return 0;

        if (isInCheck && piece.getType() == PieceType.KING) {
            return getOverlayModelData(piece, isLightSquare ? OverlayType.CHECK_LIGHT : OverlayType.CHECK_DARK, isLightSquare);
        }

        if (isSelected) {
            return getOverlayModelData(piece, isLightSquare ? OverlayType.SELECTED_LIGHT : OverlayType.SELECTED_DARK, isLightSquare);
        }

        if (canBeCaptured) {
            return getOverlayModelData(piece, isLightSquare ? OverlayType.CAPTURE_LIGHT : OverlayType.CAPTURE_DARK, isLightSquare);
        }

        if (isLastMoved) {
            return getOverlayModelData(piece, isLightSquare ? OverlayType.LASTMOVE_LIGHT : OverlayType.LASTMOVE_DARK, isLightSquare);
        }

        return getOverlayModelData(piece, isLightSquare ? OverlayType.NORMAL_LIGHT : OverlayType.NORMAL_DARK, isLightSquare);
    }

    public static Map<String, Integer> getAllOverlayModelData() {
        return OverlayModelDataRegistry.getAllModelData();
    }
}