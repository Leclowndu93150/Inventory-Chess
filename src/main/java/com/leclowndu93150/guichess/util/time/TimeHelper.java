package com.leclowndu93150.guichess.util.time;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Utility class for creating timer items in the chess GUI.
 */
public class TimeHelper {

    // There used to be a feature where you had a digital clock item and i made a texture for each possible state
    // but it was removed because the larger the json model, the buggier it got.
    
    /**
     * Creates a clock item for displaying time in the chess GUI.
     * 
     * @param totalSeconds the time in seconds (unused, kept for compatibility)
     * @return a Minecraft clock ItemStack
     */
    public static ItemStack getClockItem(int totalSeconds) {
        return new ItemStack(Items.CLOCK);
    }
}