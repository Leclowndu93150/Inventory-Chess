package com.leclowndu93150.guichess.debug;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import com.leclowndu93150.guichess.util.PieceOverlayHelper;
import com.leclowndu93150.guichess.util.TimeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT, modid = "guichess")
public class ChessItemTooltipHandler {

    @SubscribeEvent
    public static void onChessItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        
        // Check if this is a chess item (gray dye)
        if (!stack.is(Items.GRAY_DYE)) return;

        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return;

        int modelData = cmd.value();
        List<Component> tooltip = event.getToolTip();

        ChessPiece piece = getChessPieceByModelData(modelData);
        if (piece != null) {
            addChessPieceTooltip(tooltip, piece, modelData);
            return;
        }

        String overlayInfo = getPieceOverlayInfo(modelData);
        if (overlayInfo != null) {
            addPieceOverlayTooltip(tooltip, overlayInfo, modelData);
            return;
        }

        BoardSquare square = getBoardSquareByModelData(modelData);
        if (square != null) {
            addBoardSquareTooltip(tooltip, square, modelData);
            return;
        }

        GameUtility utility = getGameUtilityByModelData(modelData);
        if (utility != null) {
            addGameUtilityTooltip(tooltip, utility, modelData);
            return;
        }
        if (modelData >= 1000) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.advancedItemTooltips) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("§6Unknown Chess Item - Model Data: §f" + modelData));
                addDataComponentInfo(tooltip, stack);
            }
        }
    }

    private static void addClockTooltip(List<Component> tooltip, String clockInfo, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lDigital Clock"));
        tooltip.add(Component.literal(clockInfo));
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Clock system using layered textures"));
        }
    }

    private static void addChessPieceTooltip(List<Component> tooltip, ChessPiece piece, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lChess Piece"));
        tooltip.add(Component.literal("§7Type: §f" + piece.getType().name()));
        tooltip.add(Component.literal("§7Color: " + (piece.isWhite() ? "§fWhite" : "§8Black")));
        tooltip.add(Component.literal("§7Symbol: §f" + piece.getSymbol()));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Internal: " + piece.name()));
        }
    }

    private static void addPieceOverlayTooltip(List<Component> tooltip, String overlayInfo, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lChess Piece Overlay"));
        tooltip.add(Component.literal("§7Piece: §f" + overlayInfo));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));
        tooltip.add(Component.literal("§7Type: §eBackground Overlay"));

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Range: 2000+ (Piece Overlays)"));
        }
    }

    private static void addBoardSquareTooltip(List<Component> tooltip, BoardSquare square, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lBoard Square"));
        tooltip.add(Component.literal("§7Type: §f" + formatSquareType(square.name())));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));

        if (!square.getDisplayName().getString().isEmpty()) {
            tooltip.add(Component.literal("§7Status: ").append(square.getDisplayName()));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Internal: " + square.name()));
        }
    }

    private static void addGameUtilityTooltip(List<Component> tooltip, GameUtility utility, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lChess UI Element"));
        tooltip.add(Component.literal("§7Function: §f" + formatUtilityName(utility.name())));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Internal: " + utility.name()));
        }
    }

    private static void addDataComponentInfo(List<Component> tooltip, ItemStack stack) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§7§lData Components:"));

        var components = stack.getComponents();
        components.iterator().forEachRemaining(typedComponent -> {
            String typeName = typedComponent.type().toString();
            String value = typedComponent.value().toString();

            if (value.length() > 30) {
                value = value.substring(0, 27) + "...";
            }

            tooltip.add(Component.literal("§8  " + typeName + ": §7" + value));
        });
    }

    private static ChessPiece getChessPieceByModelData(int modelData) {
        return Arrays.stream(ChessPiece.values())
                .filter(piece -> piece.getModelData() == modelData)
                .findFirst()
                .orElse(null);
    }

    private static String getPieceOverlayInfo(int modelData) {
        if (modelData < 2000) return null;

        Map<String, Integer> overlayData = PieceOverlayHelper.getAllOverlayModelData();

        for (Map.Entry<String, Integer> entry : overlayData.entrySet()) {
            if (entry.getValue() == modelData) {
                return formatOverlayName(entry.getKey());
            }
        }

        return null;
    }

    private static String formatOverlayName(String overlayKey) {
        String[] parts = overlayKey.split("_");
        if (parts.length < 3) return overlayKey;

        StringBuilder result = new StringBuilder();

        // Color and piece type
        String color = capitalize(parts[0]);
        String pieceType = capitalize(parts[1]);
        result.append(color).append(" ").append(pieceType);

        // State and square color
        if (parts.length > 2) {
            String state = "";
            String squareColor = "";

            if (parts.length >= 3) {
                if (parts[2].equals("light") || parts[2].equals("dark")) {
                    squareColor = capitalize(parts[2]) + " Square";
                } else {
                    state = capitalize(parts[2]);
                    if (parts.length >= 4) {
                        squareColor = capitalize(parts[3]) + " Square";
                    }
                }
            }

            if (!state.isEmpty() && !squareColor.isEmpty()) {
                result.append(" (").append(state).append(" on ").append(squareColor).append(")");
            } else if (!squareColor.isEmpty()) {
                result.append(" (").append(squareColor).append(")");
            } else if (!state.isEmpty()) {
                result.append(" (").append(state).append(")");
            }
        }

        return result.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private static BoardSquare getBoardSquareByModelData(int modelData) {
        return Arrays.stream(BoardSquare.values())
                .filter(square -> square.getModelData() == modelData)
                .findFirst()
                .orElse(null);
    }

    private static GameUtility getGameUtilityByModelData(int modelData) {
        return Arrays.stream(GameUtility.values())
                .filter(utility -> utility.getModelData() == modelData)
                .findFirst()
                .orElse(null);
    }

    private static String formatSquareType(String name) {
        return name.toLowerCase().replace("_", " ");
    }

    private static String formatUtilityName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
    }
}