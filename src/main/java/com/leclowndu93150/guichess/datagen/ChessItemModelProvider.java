package com.leclowndu93150.guichess.datagen;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import com.leclowndu93150.guichess.util.OverlayModelDataRegistry;
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

        // Register chess piece models (original pieces)
        for (ChessPiece piece : ChessPiece.values()) {
            withExistingParent("gray_dye_" + piece.modelName, "item/generated")
                    .texture("layer0", modLoc("item/chess/" + piece.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), piece.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + piece.modelName)));
        }

        // Register piece overlay models (pieces with background overlays)
        registerPieceOverlays(grayDyeBuilder);

        // Register board square models
        for (BoardSquare square : BoardSquare.values()) {
            withExistingParent("gray_dye_" + square.modelName, "item/generated")
                    .texture("layer0", modLoc("item/board/" + square.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), square.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + square.modelName)));
        }

        // Register game utility models
        for (GameUtility utility : GameUtility.values()) {
            withExistingParent("gray_dye_" + utility.modelName, "item/generated")
                    .texture("layer0", modLoc("item/ui/" + utility.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), utility.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + utility.modelName)));
        }

        System.out.println("Generated models for:");
        System.out.println("- " + ChessPiece.values().length + " chess pieces");
        System.out.println("- " + OverlayModelDataRegistry.getAllModelData().size() + " piece overlay variations");
        System.out.println("- " + BoardSquare.values().length + " board squares");
        System.out.println("- " + GameUtility.values().length + " game utility items");
    }

    private void registerPieceOverlays(ItemModelBuilder grayDyeBuilder) {
        // Use the centralized registry to ensure consistent model data values
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
}