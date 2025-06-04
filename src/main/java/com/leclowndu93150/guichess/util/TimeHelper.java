package com.leclowndu93150.guichess.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.HashMap;
import java.util.Map;

public class TimeHelper {
    
    // We need 3601 combinations (00:00 to 60:00)
    // Split across 4 items: GRAY_DYE, LIGHT_GRAY_DYE, BLACK_DYE, WHITE_DYE
    // Each can hold up to 1200 model data values
    private static final int MAX_MODEL_DATA_PER_ITEM = 1200;
    
    // Item assignments for different time ranges
    private static final Item[] CLOCK_ITEMS = {
        Items.PINK_DYE,       // 00:00 - 19:59 (1200 values)
        Items.LIGHT_GRAY_DYE, // 20:00 - 39:59 (1200 values)
        Items.BLACK_DYE,      // 40:00 - 59:59 (1200 values)
        Items.WHITE_DYE       // 60:00 (1 value)
    };
    
    private static final Map<String, ClockData> TIME_MODEL_DATA = new HashMap<>();
    
    static {
        generateTimeModelData();
    }
    
    private static class ClockData {
        final Item item;
        final int modelData;
        
        ClockData(Item item, int modelData) {
            this.item = item;
            this.modelData = modelData;
        }
    }
    
    private static void generateTimeModelData() {
        int currentItemIndex = 0;
        int modelDataCounter = 1; // Start at 1 to avoid 0
        
        // Generate for all possible time combinations from 00:00 to 60:00
        for (int minutes = 0; minutes <= 60; minutes++) {
            for (int seconds = 0; seconds < 60; seconds++) {
                // Skip if we're past 60:00
                if (minutes == 60 && seconds > 0) continue;
                
                String timeString = String.format("%02d:%02d", minutes, seconds);
                
                // Move to next item if we've exceeded the limit
                if (modelDataCounter > MAX_MODEL_DATA_PER_ITEM) {
                    currentItemIndex++;
                    modelDataCounter = 1;
                }
                
                TIME_MODEL_DATA.put(timeString, new ClockData(
                    CLOCK_ITEMS[currentItemIndex], 
                    modelDataCounter
                ));
                
                modelDataCounter++;
            }
        }
    }
    
    public static ItemStack getClockItem(int totalSeconds) {
        // Clamp to valid range
        totalSeconds = Math.max(0, Math.min(3600, totalSeconds));
        
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        String timeString = String.format("%02d:%02d", minutes, seconds);
        ClockData clockData = TIME_MODEL_DATA.get(timeString);
        
        if (clockData != null) {
            ItemStack clockItem = new ItemStack(clockData.item);
            clockItem.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(clockData.modelData));
            return clockItem;
        }
        
        // Fallback
        return new ItemStack(Items.GRAY_DYE);
    }
    
    public static int getItemIndex(int minutes, int seconds) {
        // Calculate which item this time belongs to
        int totalSeconds = minutes * 60 + seconds;
        return Math.min(totalSeconds / 1200, CLOCK_ITEMS.length - 1);
    }
    
    public static Item getItemForTime(int minutes, int seconds) {
        return CLOCK_ITEMS[getItemIndex(minutes, seconds)];
    }
    
    public static int getModelDataForTime(int minutes, int seconds) {
        String timeString = String.format("%02d:%02d", minutes, seconds);
        ClockData clockData = TIME_MODEL_DATA.get(timeString);
        return clockData != null ? clockData.modelData : 1;
    }
    
    public static Map<String, Integer> getTimeModelDataForItem(Item item) {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, ClockData> entry : TIME_MODEL_DATA.entrySet()) {
            if (entry.getValue().item == item) {
                result.put(entry.getKey(), entry.getValue().modelData);
            }
        }
        return result;
    }
    
    public static int getTotalTimeEntries() {
        return TIME_MODEL_DATA.size();
    }
    
    public static Item[] getClockItems() {
        return CLOCK_ITEMS;
    }
}