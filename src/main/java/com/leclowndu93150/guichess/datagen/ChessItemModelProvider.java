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
        // Generate base gray_dye model
        generateBaseGrayDyeModel();

        // Generate all chess piece models
        generateChessPieceModels();

        // Generate board square models
        generateBoardSquareModels();

        // Generate utility item models
        generateUtilityModels();
    }

    private void generateBaseGrayDyeModel() {
        // Base gray_dye model (vanilla)
        withExistingParent("gray_dye", "item/generated")
                .texture("layer0", "item/gray_dye");
    }

    private void generateChessPieceModels() {
        for (ChessPiece piece : ChessPiece.values()) {
            // Create model with custom model data override
            ItemModelBuilder builder = withExistingParent("gray_dye_" + piece.modelName, "item/generated")
                    .texture("layer0", modLoc("item/chess/" + piece.modelName));

            // Add this as an override to the base gray_dye
            getBuilder("gray_dye")
                    .override()
                    .predicate(ResourceLocation.withDefaultNamespace("custom_model_data"), piece.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + piece.modelName)));
        }
    }

    private void generateBoardSquareModels() {
        for (BoardSquare square : BoardSquare.values()) {
            // Create model for board squares
            ItemModelBuilder builder = withExistingParent("gray_dye_" + square.modelName, "item/generated")
                    .texture("layer0", modLoc("item/board/" + square.modelName));

            // Add override to base gray_dye
            getBuilder("gray_dye")
                    .override()
                    .predicate(ResourceLocation.withDefaultNamespace("custom_model_data"), square.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + square.modelName)));
        }
    }

    private void generateUtilityModels() {
        for (GameUtility utility : GameUtility.values()) {
            // Create model for utility items
            ItemModelBuilder builder = withExistingParent("gray_dye_" + utility.modelName, "item/generated")
                    .texture("layer0", modLoc("item/ui/" + utility.modelName));

            // Add override to base gray_dye
            getBuilder("gray_dye")
                    .override()
                    .predicate(ResourceLocation.withDefaultNamespace("custom_model_data"), utility.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + utility.modelName)));
        }
    }
}