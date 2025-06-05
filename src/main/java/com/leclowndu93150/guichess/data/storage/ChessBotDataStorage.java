package com.leclowndu93150.guichess.data.storage;

import com.leclowndu93150.guichess.data.models.BotProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * SavedData storage for chess bot profiles and statistics.
 */
public class ChessBotDataStorage extends SavedData {
    private static final String DATA_NAME = "chess_bot_data";
    
    private final Map<Integer, BotProfile> botProfiles = new HashMap<>();
    
    public ChessBotDataStorage() {
        super();
    }
    
    public ChessBotDataStorage(CompoundTag tag, HolderLookup.Provider registries) {
        this();
        if (tag.contains("bots", ListTag.TAG_COMPOUND)) {
            ListTag botsNBT = tag.getList("bots", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < botsNBT.size(); i++) {
                BotProfile profile = BotProfile.fromNBT(botsNBT.getCompound(i));
                botProfiles.put(profile.getTargetElo(), profile);
            }
        }
    }
    
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag botsNBT = new ListTag();
        for (BotProfile profile : botProfiles.values()) {
            botsNBT.add(profile.toNBT());
        }
        tag.put("bots", botsNBT);
        return tag;
    }
    
    public BotProfile getBotProfile(int targetElo) {
        return botProfiles.computeIfAbsent(targetElo, elo -> {
            BotProfile profile = new BotProfile(elo);
            setDirty();
            return profile;
        });
    }
    
    public void updateBotProfile(BotProfile profile) {
        botProfiles.put(profile.getTargetElo(), profile);
        setDirty();
    }
    
    public Map<Integer, BotProfile> getAllBotProfiles() {
        return new HashMap<>(botProfiles);
    }
    
    public static Factory<ChessBotDataStorage> factory() {
        return new Factory<>(
            ChessBotDataStorage::new,
            ChessBotDataStorage::new,
            null
        );
    }
    
    public static String getDataName() {
        return DATA_NAME;
    }
}