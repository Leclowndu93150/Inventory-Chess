package com.leclowndu93150.guichess.gui.debug;

import com.leclowndu93150.guichess.game.core.GameManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * Debug GUI for visualizing slot indices in a simple chest (9x3 = 27 slots).
 * Each slot contains a light gray glass pane with the slot index number displayed.
 */
public class DebugSimpleGUI extends SimpleGui {
    
    public DebugSimpleGUI(ServerPlayer player) {
        super(MenuType.GENERIC_9x3, player, true);  // Changed to true to include player inventory visually
        setTitle(Component.literal("§6Debug Simple GUI - Slot Indices"));
        setupDebugSlots();
    }
    
    private void setupDebugSlots() {
        // Fill all accessible slots with numbered glass panes
        // Now including player inventory visually to show all slots with glass panes
        int totalSlots = this.getSize(); // Gets the actual total number of slots accessible
        
        for (int slot = 0; slot < totalSlots; slot++) {
            String slotType;
            String colorCode;
            
            if (slot < 27) {
                // GUI container slots (0-26)
                slotType = "Container";
                colorCode = "§e"; // Yellow for container
            } else {
                // Player inventory slots (27+) - now visually included and accessible
                slotType = "Inventory";
                colorCode = "§b"; // Aqua for inventory
            }
            
            GuiElementBuilder glassPane = new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(colorCode + slotType + " Slot " + slot))
                    .setCount(Math.max(1, Math.min(slot, 64))) // Use slot index as count (0 shows as 1)
                    .addLoreLine(Component.literal("§7Index: " + slot))
                    .addLoreLine(Component.literal("§7Count: " + Math.max(1, Math.min(slot, 64))))
                    .addLoreLine(Component.literal("§7Type: " + slotType))
                    .addLoreLine(Component.literal(slot < 27 ? 
                            "§7Row: " + (slot / 9) + ", Column: " + (slot % 9) :
                            "§7Player Slot: " + (slot - 27)))
                    .setCallback((index, type, action, gui) -> {
                        String typeInfo = index < 27 ? 
                                " §e(Container - Row: " + (index / 9) + ", Col: " + (index % 9) + ")" :
                                " §b(Player Inventory - Slot: " + (index - 27) + ")";
                        player.sendSystemMessage(Component.literal(
                                "§aClicked slot " + index + typeInfo
                        ));
                    });
            
            setSlot(slot, glassPane);
        }
    }
    
    @Override
    public void onOpen() {
        // Save player inventory before opening GUI
        GameManager.getInstance().savePlayerInventory(player);
        
        player.sendSystemMessage(Component.literal("§6=== Debug Simple GUI Opened ==="));
        player.sendSystemMessage(Component.literal("§7Total accessible slots: " + this.getSize()));
        player.sendSystemMessage(Component.literal("§7Click any slot to see its index"));
        player.sendSystemMessage(Component.literal("§7Glass pane count shows slot index"));
        player.sendSystemMessage(Component.literal("§e Yellow = Container slots (0-26)"));
        if (this.getSize() > 27) {
            player.sendSystemMessage(Component.literal("§b Aqua = Player inventory slots (27+)"));
        }
    }
    
    @Override
    public void onClose() {
        // Restore player inventory when closing GUI
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        super.onClose();
    }
}