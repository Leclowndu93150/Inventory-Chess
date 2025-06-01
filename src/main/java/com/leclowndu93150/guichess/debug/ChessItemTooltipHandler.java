package com.leclowndu93150.guichess.debug;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.util.GameUtility;
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

@EventBusSubscriber(value = Dist.CLIENT, modid = "guichess")
public class ChessItemTooltipHandler {

    @SubscribeEvent
    public static void onChessItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(Items.GRAY_DYE)) return;

        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return;

        int modelData = cmd.value();
        List<Component> tooltip = event.getToolTip();

        // Check if it's a chess piece
        ChessPiece piece = getChessPieceByModelData(modelData);
        if (piece != null) {
            addChessPieceTooltip(tooltip, piece, modelData);
            return;
        }

        // Check if it's a board square
        BoardSquare square = getBoardSquareByModelData(modelData);
        if (square != null) {
            addBoardSquareTooltip(tooltip, square, modelData);
            return;
        }

        // Check if it's a game utility
        GameUtility utility = getGameUtilityByModelData(modelData);
        if (utility != null) {
            addGameUtilityTooltip(tooltip, utility, modelData);
            return;
        }

        // If we have custom model data but it's not recognized
        if (modelData >= 1000) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.advancedItemTooltips) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("§6Chess Item - Model Data: §f" + modelData));
                addDataComponentInfo(tooltip, stack);
            }
        }
    }

    private static void addChessPieceTooltip(List<Component> tooltip, ChessPiece piece, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lChess Piece"));
        tooltip.add(Component.literal("§7Type: §f" + piece.getType().name()));
        tooltip.add(Component.literal("§7Color: " + (piece.isWhite() ? "§fWhite" : "§8Black")));
        tooltip.add(Component.literal("§7Symbol: §f" + piece.symbol));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8Internal: " + piece.name()));
        }
    }

    private static void addBoardSquareTooltip(List<Component> tooltip, BoardSquare square, int modelData) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§lBoard Square"));
        tooltip.add(Component.literal("§7Type: §f" + formatSquareType(square.name())));
        tooltip.add(Component.literal("§7Model ID: §b" + modelData));

        if (!square.displayName.getString().isEmpty()) {
            tooltip.add(Component.literal("§7Status: ").append(square.displayName));
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
                .filter(piece -> piece.modelData == modelData)
                .findFirst()
                .orElse(null);
    }

    private static BoardSquare getBoardSquareByModelData(int modelData) {
        return Arrays.stream(BoardSquare.values())
                .filter(square -> square.modelData == modelData)
                .findFirst()
                .orElse(null);
    }

    private static GameUtility getGameUtilityByModelData(int modelData) {
        return Arrays.stream(GameUtility.values())
                .filter(utility -> utility.modelData == modelData)
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