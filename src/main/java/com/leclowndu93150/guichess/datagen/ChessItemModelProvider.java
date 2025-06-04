package com.leclowndu93150.guichess.datagen;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import com.leclowndu93150.guichess.util.OverlayModelDataRegistry;
import com.leclowndu93150.guichess.util.TimeHelper;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Map;

public class ChessItemModelProvider extends ItemModelProvider {

    public ChessItemModelProvider(PackOutput output, String modid, ExistingFileHelper existingFileHelper) {
        super(output, modid, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        ItemModelBuilder grayDyeBuilder = withExistingParent("gray_dye", "item/generated")
                .texture("layer0", "minecraft:item/gray_dye");

        for (ChessPiece piece : ChessPiece.values()) {
            withExistingParent("gray_dye_" + piece.getModelName(), "item/generated")
                    .texture("layer0", modLoc("item/chess/" + piece.getModelName()));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), piece.getModelData())
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + piece.getModelName())));
        }

        registerPieceOverlays(grayDyeBuilder);

        for (BoardSquare square : BoardSquare.values()) {
            withExistingParent("gray_dye_" + square.getModelName(), "item/generated")
                    .texture("layer0", modLoc("item/board/" + square.getModelName()));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), square.getModelData())
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + square.getModelName())));
        }

        for (GameUtility utility : GameUtility.values()) {
            withExistingParent("gray_dye_" + utility.getModelName(), "item/generated")
                    .texture("layer0", modLoc("item/ui/" + utility.getModelName()));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), utility.getModelData())
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + utility.getModelName())));
        }

        // Register clock models
        registerClockModels(grayDyeBuilder);

        System.out.println("Generated models for:");
        System.out.println("- " + ChessPiece.values().length + " chess pieces");
        System.out.println("- " + OverlayModelDataRegistry.getAllModelData().size() + " piece overlay variations");
        System.out.println("- " + BoardSquare.values().length + " board squares");
        System.out.println("- " + GameUtility.values().length + " game utility items");
        System.out.println("- " + TimeHelper.getTotalTimeEntries() + " clock time variations");
    }

    private void registerPieceOverlays(ItemModelBuilder grayDyeBuilder) {
        Map<String, Integer> overlayModelData = OverlayModelDataRegistry.getAllModelData();

        for (Map.Entry<String, Integer> entry : overlayModelData.entrySet()) {
            String modelName = entry.getKey();
            Integer modelData = entry.getValue();

            withExistingParent("gray_dye_" + modelName, "item/generated")
                    .texture("layer0", modLoc("item/pieces_overlay/" + modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + modelName)));
        }

        int minModelData = overlayModelData.values().stream().min(Integer::compareTo).orElse(2000);
        int maxModelData = overlayModelData.values().stream().max(Integer::compareTo).orElse(2999);
        System.out.println("Piece overlay model data range: " + minModelData + "-" + maxModelData);
    }
    
    private void registerClockModels(ItemModelBuilder grayDyeBuilder) {
        // Register clock models for each dye item that will be used
        for (net.minecraft.world.item.Item clockItem : TimeHelper.getClockItems()) {
            String itemName = clockItem.toString().toLowerCase().replace("minecraft:", "");
            ItemModelBuilder itemBuilder;
            
            if (clockItem == net.minecraft.world.item.Items.GRAY_DYE) {
                itemBuilder = grayDyeBuilder;
            } else {
                // Create new builders for other dye items
                itemBuilder = withExistingParent(itemName, "item/generated")
                        .texture("layer0", "minecraft:item/" + itemName);
            }
            
            Map<String, Integer> timeData = TimeHelper.getTimeModelDataForItem(clockItem);
            
            // Create clock models with layered digit textures
            for (Map.Entry<String, Integer> entry : timeData.entrySet()) {
                String timeString = entry.getKey();
                int modelData = entry.getValue();
                
                // Parse time digits
                String[] parts = timeString.split(":");
                char minuteTens = parts[0].charAt(0);
                char minuteOnes = parts[0].charAt(1);
                char secondTens = parts[1].charAt(0);
                char secondOnes = parts[1].charAt(1);
                
                // Create model with clock base and digit overlays
                String modelName = itemName + "_clock_" + timeString.replace(":", "_");
                ItemModelBuilder clockModel = withExistingParent(modelName, "item/generated")
                        .texture("layer0", modLoc("item/clock/clock"))  // Base clock texture
                        .texture("layer1", modLoc("item/numbers/" + minuteTens + "_pos1"))  // Minute tens at position 1
                        .texture("layer2", modLoc("item/numbers/" + minuteOnes + "_pos2"))  // Minute ones at position 2
                        .texture("layer3", modLoc("item/numbers/" + secondTens + "_pos3"))  // Second tens at position 3
                        .texture("layer4", modLoc("item/numbers/" + secondOnes + "_pos4")); // Second ones at position 4
                
                // Register the override on the item
                itemBuilder.override()
                        .predicate(ResourceLocation.parse("custom_model_data"), modelData)
                        .model(new ModelFile.UncheckedModelFile(modLoc("item/" + modelName)));
            }
        }
    }
}