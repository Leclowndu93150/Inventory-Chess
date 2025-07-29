package com.leclowndu93150.guichess.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Tracks if player got guide book on first join.
 */
public class PlayerDataAttachment {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = 
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "guichess");
    
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CHESS_PLAYER_DATA =
        ATTACHMENT_TYPES.register("chess_player_data", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
}