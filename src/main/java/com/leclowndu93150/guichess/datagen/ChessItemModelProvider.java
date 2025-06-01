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

        for (ChessPiece piece : ChessPiece.values()) {
            withExistingParent("gray_dye_" + piece.modelName, "item/generated")
                    .texture("layer0", modLoc("item/chess/" + piece.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), piece.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + piece.modelName)));
        }

        for (BoardSquare square : BoardSquare.values()) {
            withExistingParent("gray_dye_" + square.modelName, "item/generated")
                    .texture("layer0", modLoc("item/board/" + square.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), square.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + square.modelName)));
        }

        for (GameUtility utility : GameUtility.values()) {
            withExistingParent("gray_dye_" + utility.modelName, "item/generated")
                    .texture("layer0", modLoc("item/ui/" + utility.modelName));

            grayDyeBuilder.override()
                    .predicate(ResourceLocation.parse("custom_model_data"), utility.modelData)
                    .model(new ModelFile.UncheckedModelFile(modLoc("item/gray_dye_" + utility.modelName)));
        }
    }
}