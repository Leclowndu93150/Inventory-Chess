package com.leclowndu93150.guichess.util;

import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.pieces.PieceType;

import java.util.Map;

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

    /**
     * Gets the model data for a piece with a specific overlay type on a specific square color
     * @param piece The chess piece
     * @param overlayType The type of overlay (selected, capture, etc.)
     * @param isLightSquare True if the piece is on a light square, false for dark square
     * @return The model data value to use, or the piece's original model data if overlay not found
     */
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

        // For normal overlays, choose based on square color
        if (overlayType == OverlayType.NORMAL_LIGHT || overlayType == OverlayType.NORMAL_DARK) {
            suffix = isLightSquare ? "_light" : "_dark";
        }

        String key = piece.modelName + suffix;
        Integer modelData = OverlayModelDataRegistry.getModelData(key);

        // Return overlay model data if found, otherwise fall back to original piece model data
        return modelData != null ? modelData : piece.modelData;
    }

    /**
     * Convenience method to get overlay model data based on piece state
     * @param piece The chess piece
     * @param isLightSquare True if on light square
     * @param isSelected True if piece is selected
     * @param canBeCaptured True if piece can be captured this turn
     * @param isLastMoved True if this piece was last moved
     * @param isInCheck True if this king is in check (only applies to kings)
     * @return The appropriate model data
     */
    public static int getModelDataForPieceState(ChessPiece piece, boolean isLightSquare,
                                                boolean isSelected, boolean canBeCaptured,
                                                boolean isLastMoved, boolean isInCheck) {
        if (piece == null) return 0;

        // Priority order: Check > Selected > Can be captured > Last moved > Normal
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

        // Normal piece
        return getOverlayModelData(piece, isLightSquare ? OverlayType.NORMAL_LIGHT : OverlayType.NORMAL_DARK, isLightSquare);
    }

    /**
     * Gets all model data values used by piece overlays (for debugging/validation)
     * @return Map of overlay key to model data value
     */
    public static Map<String, Integer> getAllOverlayModelData() {
        return OverlayModelDataRegistry.getAllModelData();
    }
}