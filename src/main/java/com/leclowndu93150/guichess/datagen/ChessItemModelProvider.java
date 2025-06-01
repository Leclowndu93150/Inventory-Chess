package com.leclowndu93150.guichess.datagen;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.chess.util.GameUtility;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

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
        System.out.println("- " + (ChessPiece.values().length * 8 + 4) + " piece overlay variations"); // 8 standard + 2 check for kings
        System.out.println("- " + BoardSquare.values().length + " board squares");
        System.out.println("- " + GameUtility.values().length + " game utility items");
    }

    private void registerPieceOverlays(ItemModelBuilder grayDyeBuilder) {
        int modelDataCounter = 2000; // Starting range for piece overlays

        for (ChessPiece piece : ChessPiece.values()) {
            String baseName = piece.modelName;

            // Register all overlay variations for this piece
            String[] overlayTypes = {
                    "_light",           // Normal on light square
                    "_dark",            // Normal on dark square
                    "_selected_light",  // Selected on light square
                    "_selected_dark",   // Selected on dark square
                    "_capture_light",   // Can be captured on light square
                    "_capture_dark",    // Can be captured on dark square
                    "_lastmove_light",  // Last moved on light square
                    "_lastmove_dark"    // Last moved on dark square
            };

            for (String overlayType : overlayTypes) {
                String modelName = baseName + overlayType;

                withExistingParent("gray_dye_" + modelName, "item/generated")
                        .texture("layer0", modLoc("item/pieces_overlay/" + modelName));

                grayDyeBuilder.override()
                        .predicate(ResourceLocation.parse("custom_model_data"), modelDataCounter)
                        .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + modelName)));

                modelDataCounter++;
            }

            // Special check overlays for kings only
            if (piece.getType().name().equals("KING")) {
                String[] checkOverlays = {"_check_light", "_check_dark"};

                for (String checkOverlay : checkOverlays) {
                    String modelName = baseName + checkOverlay;

                    withExistingParent("gray_dye_" + modelName, "item/generated")
                            .texture("layer0", modLoc("item/pieces_overlay/" + modelName));

                    grayDyeBuilder.override()
                            .predicate(ResourceLocation.parse("custom_model_data"), modelDataCounter)
                            .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + modelName)));

                    modelDataCounter++;
                }
            }
        }

        System.out.println("Piece overlay model data range: 2000-" + (modelDataCounter - 1));
    }
}