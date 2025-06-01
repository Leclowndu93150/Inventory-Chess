package com.leclowndu93150.guichess.datagen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Collections;

public class DummyExistingFileHelper extends ExistingFileHelper {

    public DummyExistingFileHelper() {
        super(Collections.emptyList(), Collections.emptySet(), false, null, null);
    }

    @Override
    public boolean exists(ResourceLocation loc, PackType packType) {
        return true;
    }

    @Override
    public boolean exists(ResourceLocation loc, IResourceType type) {
        return true;
    }

    @Override
    public boolean exists(ResourceLocation loc, PackType packType, String pathSuffix, String pathPrefix) {
        return true;
    }

}