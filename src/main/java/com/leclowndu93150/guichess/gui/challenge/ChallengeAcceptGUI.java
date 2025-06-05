package com.leclowndu93150.guichess.gui.challenge;

import com.leclowndu93150.guichess.game.challenge.ChessChallenge;
import com.leclowndu93150.guichess.game.core.GameManager;
import com.leclowndu93150.guichess.util.audio.ChessSoundManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ChallengeAcceptGUI extends SimpleGui {
    private final ServerPlayer player;
    private final ChessChallenge challenge;
    private final List<ItemStack> additionalBetItems = new ArrayList<>();
    private boolean showingBetScreen = false;
    private boolean acceptProcessed = false;
    
    public ChallengeAcceptGUI(ServerPlayer player, ChessChallenge challenge) {
        super(MenuType.GENERIC_9x3, player, false);
        this.player = player;
        this.challenge = challenge;
        setTitle(Component.literal("§0Chess Challenge from " + challenge.getChallenger().getName().getString()));
        updateDisplay();
    }
    
    @Override
    public void onOpen() {
        // Save player inventory before opening GUI to protect against item loss
        GameManager.getInstance().savePlayerInventory(player);
        super.onOpen();
    }
    
    private void updateDisplay() {
        clearAllSlots();
        
        if (showingBetScreen && challenge.hasBet()) {
            setupBetModificationScreen();
        } else {
            setupMainScreen();
        }
    }
    
    private void setupMainScreen() {
        // Challenger info
        setSlot(0, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setSkullOwner(challenge.getChallenger().getGameProfile(), player.getServer())
                .setName(Component.literal("§6Challenger: §f" + challenge.getChallenger().getName().getString()))
                .addLoreLine(Component.literal("§7Time Control: §f" + challenge.timeControl.displayName)));
        
        // Bet info
        if (challenge.hasBet()) {
            GuiElementBuilder betInfo = new GuiElementBuilder(Items.GOLD_INGOT)
                    .setName(Component.literal("§6Bet Items"))
                    .addLoreLine(Component.literal("§7Challenger bet: §f" + challenge.getChallengerBet().size() + " items"));
            
            if (!additionalBetItems.isEmpty()) {
                betInfo.addLoreLine(Component.literal("§7Your additional bet: §f" + additionalBetItems.size() + " items"));
            }
            
            betInfo.addLoreLine(Component.literal(""))
                    .addLoreLine(Component.literal("§eClick to view/modify bet"));
            
            setSlot(4, betInfo.setCallback((index, type, action, gui) -> {
                showingBetScreen = true;
                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                updateDisplay();
            }));
        }
        
        // Side info
        String sideInfo = challenge.getChallengerPreferredSide() == null ? "Random" : 
                "Challenger wants " + challenge.getChallengerPreferredSide();
        setSlot(8, new GuiElementBuilder(Items.COMPASS)
                .setName(Component.literal("§6Side Selection"))
                .addLoreLine(Component.literal("§7" + sideInfo)));
        
        // Accept button
        setSlot(22, new GuiElementBuilder(Items.LIME_WOOL)
                .setName(Component.literal("§aAccept Challenge"))
                .addLoreLine(Component.literal("§7Start the game"))
                .setCallback((index, type, action, gui) -> {
                    if (type.isLeft) { // Only process left clicks
                        acceptChallenge();
                    }
                }));
        
        // Decline button
        setSlot(18, new GuiElementBuilder(Items.RED_WOOL)
                .setName(Component.literal("§cDecline Challenge"))
                .addLoreLine(Component.literal("§7Reject this challenge"))
                .setCallback((index, type, action, gui) -> {
                    if (type.isLeft) { // Only process left clicks
                        declineChallenge();
                    }
                }));
    }
    
    private void setupBetModificationScreen() {
        setTitle(Component.literal("§0Review Bet"));
        
        // Back button
        setSlot(0, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§cBack"))
                .setCallback((index, type, action, gui) -> {
                    showingBetScreen = false;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    updateDisplay();
                }));
        
        // Show challenger's bet items
        setSlot(0, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§6Challenger's Bet:")));
        
        List<ItemStack> challengerBet = challenge.getChallengerBet();
        for (int i = 0; i < Math.min(challengerBet.size(), 6); i++) {
            setSlot(1 + i, new GuiElementBuilder(challengerBet.get(i))
                    .addLoreLine(Component.literal("§7From challenger")));
        }
        
        // No separator needed in single chest
        
        // Your additional bet area
        setSlot(9, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§aYour Additional Bet (Optional):")));
        
        // Show your bet slots
        int[] yourBetSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < yourBetSlots.length; i++) {
            int slot = yourBetSlots[i];
            
            if (i < additionalBetItems.size()) {
                ItemStack item = additionalBetItems.get(i).copy();
                final int itemIndex = i;
                setSlot(slot, new GuiElementBuilder(item)
                        .addLoreLine(Component.literal("§7Right-click to remove"))
                        .setCallback((index, type, action, gui) -> {
                            if (type.isRight) {
                                ItemStack removed = additionalBetItems.remove(itemIndex);
                                if (!player.getInventory().add(removed)) {
                                    player.drop(removed, false);
                                }
                                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                                updateDisplay();
                            }
                        }));
            } else {
                setSlot(slot, new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .setName(Component.literal("§7Click with item to add"))
                        .setCallback((index, type, action, gui) -> {
                            ItemStack cursor = player.containerMenu.getCarried();
                            if (!cursor.isEmpty() && additionalBetItems.size() < 7) {
                                // Take item from cursor
                                additionalBetItems.add(cursor.copy());
                                player.containerMenu.setCarried(ItemStack.EMPTY);
                                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                                updateDisplay();
                            }
                        }));
            }
        }
        
        // Confirm button
        setSlot(26, new GuiElementBuilder(Items.EMERALD)
                .setName(Component.literal("§aConfirm Bet"))
                .addLoreLine(Component.literal("§7Return to challenge screen"))
                .setCallback((index, type, action, gui) -> {
                    showingBetScreen = false;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    updateDisplay();
                }));
    }
    
    private void clearAllSlots() {
        for (int i = 0; i < 27; i++) {
            clearSlot(i);
        }
    }
    
    private void acceptChallenge() {
        if (acceptProcessed) {
            return; // Prevent duplicate processing
        }
        acceptProcessed = true;
        
        challenge.setAcceptedBet(additionalBetItems);
        // Clear our local list since GameManager will handle the items
        additionalBetItems.clear();
        GameManager.getInstance().acceptChallenge(player, challenge);
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
        close();
    }
    
    private void declineChallenge() {
        GameManager.getInstance().declineChallenge(player, challenge);
        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
        close();
    }
    
    @Override
    public void onClose() {
        // Return additional bet items to player if not accepting
        for (ItemStack item : additionalBetItems) {
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
        }
        additionalBetItems.clear();
        
        // Restore original inventory to ensure survival safety
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        super.onClose();
    }
}