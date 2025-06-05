package com.leclowndu93150.guichess.gui.debug;

import com.leclowndu93150.guichess.game.core.GameManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * Debug GUI for visualizing slot indices in a double chest (9x6 = 54 slots).
 * Each slot contains a light gray glass pane with the slot index number displayed.
 */
public class DebugDoubleGUI extends SimpleGui {
    
    public DebugDoubleGUI(ServerPlayer player) {
        super(MenuType.GENERIC_9x6, player, true);  // Changed to true to include player inventory visually
        setTitle(Component.literal("§6Debug Double GUI - Slot Indices"));
        setupDebugSlots();
    }
    
    private void setupDebugSlots() {
        // Fill all accessible slots with numbered glass panes
        // Now including player inventory visually to show all slots with glass panes
        int totalSlots = this.getSize(); // Gets the actual total number of slots accessible
        
        for (int slot = 0; slot < totalSlots; slot++) {
            String slotType;
            String colorCode;
            
            if (slot < 54) {
                // GUI container slots (0-53)
                slotType = "Container";
                colorCode = "§e"; // Yellow for container
            } else {
                // Player inventory slots (54+) - now visually included and accessible
                slotType = "Inventory";
                colorCode = "§b"; // Aqua for inventory
            }
            
            GuiElementBuilder glassPane = new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(colorCode + slotType + " Slot " + slot))
                    .setCount(Math.max(1, Math.min(slot, 64))) // Use slot index as count (0 shows as 1, cap at 64)
                    .addLoreLine(Component.literal("§7Index: " + slot))
                    .addLoreLine(Component.literal("§7Count: " + Math.max(1, Math.min(slot, 64))))
                    .addLoreLine(Component.literal("§7Type: " + slotType));
            
            if (slot < 54) {
                // Container slot information
                glassPane.addLoreLine(Component.literal("§7Row: " + (slot / 9) + ", Column: " + (slot % 9)))
                         .addLoreLine(Component.literal("§7Chess Board: " + isChessBoardSlot(slot)));
            } else {
                // Player inventory slot information
                glassPane.addLoreLine(Component.literal("§7Player Slot: " + (slot - 54)));
            }
            
            glassPane.setCallback((index, type, action, gui) -> {
                String typeInfo;
                if (index < 54) {
                    String chessBoardInfo = isChessBoardSlot(index) ? 
                            " §a(Chess board slot)" : " §c(Outside chess board)";
                    typeInfo = " §e(Container - Row: " + (index / 9) + ", Col: " + (index % 9) + ")" + chessBoardInfo;
                } else {
                    typeInfo = " §b(Player Inventory - Slot: " + (index - 54) + ")";
                }
                player.sendSystemMessage(Component.literal(
                        "§aClicked slot " + index + typeInfo
                ));
            });
            
            setSlot(slot, glassPane);
        }
    }
    
    /**
     * Checks if a slot index corresponds to a chess board position.
     * Chess board uses an 8x8 grid within the 9x6 layout.
     */
    private boolean isChessBoardSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        
        // Chess board typically occupies columns 0-7 and rows 0-5 (or a subset)
        // This is just an example - actual chess GUI might use different mapping
        return col < 8 && row < 6;
    }
    
    @Override
    public void onOpen() {
        // Save player inventory before opening GUI
        GameManager.getInstance().savePlayerInventory(player);
        
        player.sendSystemMessage(Component.literal("§6=== Debug Double GUI Opened ==="));
        player.sendSystemMessage(Component.literal("§7Total accessible slots: " + this.getSize()));
        player.sendSystemMessage(Component.literal("§7Click any slot to see its index"));
        player.sendSystemMessage(Component.literal("§7Glass pane count shows slot index"));
        player.sendSystemMessage(Component.literal("§e Yellow = Container slots (0-53)"));
        if (this.getSize() > 54) {
            player.sendSystemMessage(Component.literal("§b Aqua = Player inventory slots (54+)"));
        }
        player.sendSystemMessage(Component.literal("§7Green lore = potential chess board slot"));
        player.sendSystemMessage(Component.literal("§7Red lore = outside chess board area"));
    }
    
    @Override
    public void onClose() {
        // Restore player inventory when closing GUI
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        super.onClose();
    }
}