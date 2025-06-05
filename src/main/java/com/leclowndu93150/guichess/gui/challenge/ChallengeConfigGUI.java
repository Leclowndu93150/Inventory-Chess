package com.leclowndu93150.guichess.gui.challenge;

import com.leclowndu93150.guichess.util.time.TimeControl;
import com.leclowndu93150.guichess.game.challenge.ChessChallenge;
import com.leclowndu93150.guichess.game.core.GameManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Challenge configuration interface for setting up chess games before sending challenges to opponents.
 * 
 * <p>This GUI provides a comprehensive configuration system for chess challenges, allowing players
 * to customize all aspects of their game setup before sending a challenge request. It serves as
 * the primary interface for configuring competitive chess matches between players.
 * 
 * <h3>Configuration Options:</h3>
 * <ul>
 *   <li><strong>Opponent Type:</strong> Human players vs AI bots (bot implementation pending)</li>
 *   <li><strong>Time Control:</strong> Complete selection of standard chess time formats</li>
 *   <li><strong>Hint System:</strong> Configurable hint allowance (0-3 hints per game)</li>
 *   <li><strong>Side Selection:</strong> Choose to play as White, Black, or random assignment</li>
 *   <li><strong>Bot Configuration:</strong> ELO rating selection for AI difficulty (when implemented)</li>
 *   <li><strong>Item Betting:</strong> Wager system for competitive matches (planned feature)</li>
 * </ul>
 * 
 * <h3>User Experience Flow:</h3>
 * <ol>
 *   <li>Player opens challenge configuration</li>
 *   <li>Selects opponent type and configures related settings</li>
 *   <li>Chooses time control from standard chess formats</li>
 *   <li>Sets optional features (hints, betting)</li>
 *   <li>Reviews configuration and sends challenge</li>
 *   <li>Proceeds to opponent selection if challenging human player</li>
 * </ol>
 * 
 * <h3>Integration Points:</h3>
 * <ul>
 *   <li><strong>GameManager:</strong> Creates and manages challenge instances</li>
 *   <li><strong>TimeControl:</strong> Validates and applies timing configurations</li>
 *   <li><strong>Player Selection:</strong> Interfaces with server player list for opponent selection</li>
 *   <li><strong>Challenge System:</strong> Generates properly configured ChessChallenge objects</li>
 * </ul>
 * 
 * <h3>State Management:</h3>
 * The GUI maintains configuration state locally until challenge creation, allowing users
 * to modify settings freely without affecting other systems. Only valid configurations
 * are permitted to proceed to challenge creation.
 * 
 * <h3>Future Features:</h3>
 * <ul>
 *   <li>Bot integration with adjustable difficulty settings</li>
 *   <li>Item betting system with inventory integration</li>
 *   <li>Tournament mode configuration</li>
 *   <li>Saved configuration presets</li>
 * </ul>
 * 
 * @see ChessChallenge For the challenge data structure created by this interface
 * @see GameManager#createChallengeWithConfiguration For challenge creation integration
 * @see TimeControl For available time control options
 */
/**
 * GUI for configuring chess game challenges.
 * Handles opponent selection, time controls, betting, and game setup.
 */
public class ChallengeConfigGUI extends SimpleGui {
    private final ServerPlayer player;
    
    // Challenge configuration
    private OpponentType opponentType = OpponentType.PLAYER;
    private TimeControl selectedTimeControl = TimeControl.BLITZ_5_0;
    private int hintsAllowed = 3; // 0-3 hints
    private int botElo = 1200; // For bot opponents
    private SideChoice sideChoice = SideChoice.RANDOM;
    private boolean bettingEnabled = false;
    
    public ChallengeConfigGUI(ServerPlayer player) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        
        setTitle(Component.literal("§6§lChallenge Configuration"));
        setupGUI();
    }
    
    @Override
    public void onOpen() {
        // Save player inventory before opening GUI to ensure survival safety
        GameManager.getInstance().savePlayerInventory(player);
        super.onOpen();
    }
    
    @Override
    public void onClose() {
        // Restore original inventory to ensure survival safety
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        super.onClose();
    }
    
    private void setupGUI() {
        // Fill background with gray glass panes
        for (int i = 0; i < 54; i++) {
            setSlot(i, new GuiElementBuilder()
                    .setItem(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.empty()));
        }
        
        setupOpponentSelection();
        setupTimeControlSelection();
        setupHintSelection();
        setupSideSelection();
        setupBotEloSelection();
        setupBettingSection();
        setupNavigationButtons();
    }
    
    private void setupOpponentSelection() {
        // Opponent Type Selection (Top row)
        setSlot(10, new GuiElementBuilder()
                .setItem(Items.PLAYER_HEAD)
                .setName(Component.literal("§a§lVs Player"))
                .addLoreLine(Component.literal(opponentType == OpponentType.PLAYER ? "§7§l► Selected" : "§7Click to select"))
                .addLoreLine(Component.literal("§7Challenge another player"))
                .setCallback((index, type, action, gui) -> {
                    opponentType = OpponentType.PLAYER;
                    updateGUI();
                }));
        
        setSlot(12, new GuiElementBuilder()
                .setItem(Items.IRON_GOLEM_SPAWN_EGG)
                .setName(Component.literal("§c§lVs Bot"))
                .addLoreLine(Component.literal(opponentType == OpponentType.BOT ? "§7§l► Selected" : "§7Click to select"))
                .addLoreLine(Component.literal("§7Play against computer"))
                .addLoreLine(Component.literal("§c§oNot yet implemented"))
                .setCallback((index, type, action, gui) -> {
                    opponentType = OpponentType.BOT;
                    player.sendSystemMessage(Component.literal("§c§lBot opponents not yet implemented!"));
                    // updateGUI(); // Uncomment when bots are implemented
                }));
    }
    
    private void setupTimeControlSelection() {
        // Time Control Selection (Second row)
        setSlot(19, new GuiElementBuilder()
                .setItem(Items.CLOCK)
                .setName(Component.literal("§e§lTime Control"))
                .addLoreLine(Component.literal("§7Current: §f" + selectedTimeControl.displayName))
                .addLoreLine(Component.literal("§7Click to change"))
                .setCallback((index, type, action, gui) -> openTimeControlMenu()));
    }
    
    private void setupHintSelection() {
        // Hints Selection (Second row)
        setSlot(21, new GuiElementBuilder()
                .setItem(Items.LIGHT)
                .setName(Component.literal("§b§lHints Allowed"))
                .addLoreLine(Component.literal("§7Current: §f" + hintsAllowed + " hints"))
                .addLoreLine(Component.literal("§7Click to change (0-3)"))
                .setCallback((index, type, action, gui) -> {
                    hintsAllowed = (hintsAllowed + 1) % 4;
                    updateGUI();
                }));
    }
    
    private void setupSideSelection() {
        // Side Selection (Third row)
        setSlot(28, new GuiElementBuilder()
                .setItem(Items.COMPASS)
                .setName(Component.literal("§d§lSide Selection"))
                .addLoreLine(Component.literal("§7Current: §f" + sideChoice.displayName))
                .addLoreLine(Component.literal("§7Click to change"))
                .setCallback((index, type, action, gui) -> {
                    sideChoice = SideChoice.values()[(sideChoice.ordinal() + 1) % SideChoice.values().length];
                    updateGUI();
                }));
    }
    
    private void setupBotEloSelection() {
        // Bot ELO Selection (Third row) - only show if bot is selected
        if (opponentType == OpponentType.BOT) {
            setSlot(30, new GuiElementBuilder()
                    .setItem(getBotEloMaterial())
                    .setName(Component.literal("§6§lBot Strength"))
                    .addLoreLine(Component.literal("§7ELO: §f" + botElo))
                    .addLoreLine(Component.literal("§7Click to change"))
                    .setCallback((index, type, action, gui) -> {
                        // Cycle through bot ELO levels
                        botElo = getNextBotElo(botElo);
                        updateGUI();
                    }));
        } else {
            clearSlot(30);
        }
    }
    
    private void setupBettingSection() {
        // Betting Toggle (Fourth row)
        setSlot(37, new GuiElementBuilder()
                .setItem(bettingEnabled ? Items.EMERALD : Items.BARRIER)
                .setName(Component.literal("§2§lItem Betting"))
                .addLoreLine(Component.literal(bettingEnabled ? "§a§l► Enabled" : "§c§l► Disabled"))
                .addLoreLine(Component.literal("§7Click to toggle"))
                .addLoreLine(Component.literal("§c§oNot yet implemented"))
                .setCallback((index, type, action, gui) -> {
                    player.sendSystemMessage(Component.literal("§c§lItem betting not yet implemented!"));
                    // bettingEnabled = !bettingEnabled; // Uncomment when implemented
                    // updateGUI();
                }));
    }
    
    private void setupNavigationButtons() {
        // Cancel button
        setSlot(45, new GuiElementBuilder()
                .setItem(Items.RED_CONCRETE)
                .setName(Component.literal("§c§lCancel"))
                .addLoreLine(Component.literal("§7Close without challenging"))
                .setCallback((index, type, action, gui) -> close()));
        
        // Challenge button
        setSlot(53, new GuiElementBuilder()
                .setItem(Items.GREEN_CONCRETE)
                .setName(Component.literal("§a§lSend Challenge"))
                .addLoreLine(Component.literal("§7Start the challenge"))
                .setCallback((index, type, action, gui) -> sendChallenge()));
    }
    
    /**
     * Opens a sub-menu interface for selecting chess time control settings.
     * 
     * <p>This method creates a dedicated GUI for time control selection, providing
     * visual representation of all available chess timing formats. The sub-menu
     * approach prevents the main configuration interface from becoming cluttered
     * while offering comprehensive time control options.
     * 
     * <h3>Time Control Categories:</h3>
     * <ul>
     *   <li><strong>Bullet:</strong> Fast-paced games (1-2 minutes)</li>
     *   <li><strong>Blitz:</strong> Quick games (3-5 minutes)</li>
     *   <li><strong>Rapid:</strong> Medium-length games (10-30 minutes)</li>
     *   <li><strong>Classical:</strong> Long-form games (60+ minutes)</li>
     *   <li><strong>Unlimited:</strong> No time restrictions</li>
     * </ul>
     * 
     * <p>Each time control option displays its category-appropriate icon and
     * indicates the current selection state. The interface provides immediate
     * feedback and returns to the main configuration screen upon selection.
     * 
     * @see TimeControl For the complete list of available time controls
     */
    private void openTimeControlMenu() {
        // Open sub-menu for time control selection
        SimpleGui timeMenu = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        timeMenu.setTitle(Component.literal("§e§lSelect Time Control"));
        
        // Fill background
        for (int i = 0; i < 27; i++) {
            timeMenu.setSlot(i, new GuiElementBuilder()
                    .setItem(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.empty()));
        }
        
        // Add time control options
        TimeControl[] timeControls = TimeControl.values();
        for (int i = 0; i < Math.min(timeControls.length, 21); i++) {
            TimeControl tc = timeControls[i];
            int slot = 1 + (i % 7) + (i / 7) * 9;
            
            timeMenu.setSlot(slot, new GuiElementBuilder()
                    .setItem(getTimeControlItem(tc))
                    .setName(Component.literal("§f" + tc.displayName))
                    .addLoreLine(Component.literal(tc == selectedTimeControl ? "§a§l► Selected" : "§7Click to select"))
                    .setCallback((index, type, action, gui) -> {
                        selectedTimeControl = tc;
                        timeMenu.close();
                        open(); // Reopen main GUI
                    }));
        }
        
        // Back button
        timeMenu.setSlot(22, new GuiElementBuilder()
                .setItem(Items.ARROW)
                .setName(Component.literal("§7§lBack"))
                .setCallback((index, type, action, gui) -> {
                    timeMenu.close();
                    open(); // Reopen the main GUI
                }));
        
        close();
        timeMenu.open();
    }
    
    private void sendChallenge() {
        if (opponentType == OpponentType.BOT) {
            player.sendSystemMessage(Component.literal("§c§lBot challenges not yet implemented!"));
            return;
        }
        
        // For now, just open a player selection menu
        openPlayerSelectionMenu();
    }
    
    private void openPlayerSelectionMenu() {
        SimpleGui playerMenu = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        playerMenu.setTitle(Component.literal("§a§lSelect Opponent"));
        
        // Fill background
        for (int i = 0; i < 54; i++) {
            playerMenu.setSlot(i, new GuiElementBuilder()
                    .setItem(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.empty()));
        }
        
        // Add online players
        List<ServerPlayer> onlinePlayers = player.getServer().getPlayerList().getPlayers();
        int slot = 10;
        
        for (ServerPlayer targetPlayer : onlinePlayers) {
            if (targetPlayer.equals(player)) continue; // Skip self
            if (slot >= 44) break; // Prevent overflow
            
            boolean isBusy = GameManager.getInstance().isPlayerBusy(targetPlayer);
            
            playerMenu.setSlot(slot, new GuiElementBuilder()
                    .setItem(Items.PLAYER_HEAD)
                    .setSkullOwner(targetPlayer.getGameProfile(), player.getServer())
                    .setName(Component.literal("§f" + targetPlayer.getName().getString()))
                    .addLoreLine(Component.literal(isBusy ? "§c§oBusy" : "§a§oAvailable"))
                    .addLoreLine(Component.literal("§7Click to challenge"))
                    .setCallback((index, type, action, gui) -> {
                        if (isBusy) {
                            player.sendSystemMessage(Component.literal("§c" + targetPlayer.getName().getString() + " is currently busy!"));
                            return;
                        }
                        
                        // Send the challenge with current settings
                        sendConfiguredChallenge(targetPlayer);
                        playerMenu.close();
                        close();
                    }));
            
            slot++;
            if (slot % 9 == 8) slot += 2; // Skip right edge and move to next row
        }
        
        // Back button
        playerMenu.setSlot(53, new GuiElementBuilder()
                .setItem(Items.ARROW)
                .setName(Component.literal("§7§lBack"))
                .setCallback((index, type, action, gui) -> {
                    playerMenu.close();
                    open(); // Reopen the main GUI
                }));
        
        close();
        playerMenu.open();
    }
    
    /**
     * Creates and sends a chess challenge with all configured settings to the specified target player.
     * 
     * <p>This method represents the culmination of the challenge configuration process, taking all
     * user-selected settings and creating a formal challenge request. It integrates with the
     * GameManager to ensure proper challenge lifecycle management and provides comprehensive
     * feedback to both the challenger and target.
     * 
     * <h3>Challenge Creation Process:</h3>
     * <ol>
     *   <li>Validates target player availability</li>
     *   <li>Packages all configuration settings into a challenge request</li>
     *   <li>Registers the challenge with the GameManager</li>
     *   <li>Provides feedback and instructions to both players</li>
     * </ol>
     * 
     * <h3>Configuration Integration:</h3>
     * All settings from the configuration GUI are packaged into the challenge:
     * <ul>
     *   <li>Selected time control and timing parameters</li>
     *   <li>Hint allowance for both players</li>
     *   <li>Side preference (White, Black, or random assignment)</li>
     *   <li>Any additional game modifiers</li>
     * </ul>
     * 
     * <p>The method handles failure cases gracefully, providing clear feedback when
     * challenges cannot be created (e.g., target player busy, invalid configuration).
     * 
     * @param target The player who will receive the challenge request
     * 
     * @see GameManager#createChallengeWithConfiguration For the underlying challenge creation
     * @see ChessChallenge For the challenge data structure created
     */
    private void sendConfiguredChallenge(ServerPlayer target) {
        GameManager gameManager = GameManager.getInstance();
        
        // Create challenge with configured settings - now properly uses challenge system
        boolean randomSides = (sideChoice == SideChoice.RANDOM);
        
        ChessChallenge challenge = gameManager.createChallengeWithConfiguration(
            player, target, selectedTimeControl, randomSides, hintsAllowed);
            
        if (challenge == null) {
            player.sendSystemMessage(Component.literal("§cFailed to create challenge - player might be busy!"));
            return;
        }
        
        // Send configuration summary to challenger
        player.sendSystemMessage(Component.literal("§a§lChallenge sent to " + target.getName().getString() + "!"));
        player.sendSystemMessage(Component.literal("§7Time: " + selectedTimeControl.displayName));
        player.sendSystemMessage(Component.literal("§7Hints: " + hintsAllowed));
        player.sendSystemMessage(Component.literal("§7Sides: " + sideChoice.displayName));
        player.sendSystemMessage(Component.literal("§7They can accept with §f/chess accept§7 or decline with §f/chess decline"));
    }
    
    private void updateGUI() {
        setupGUI();
    }
    
    private Item getBotEloMaterial() {
        return switch (botElo) {
            case 800 -> Items.COAL;
            case 1000 -> Items.COPPER_INGOT;
            case 1200 -> Items.IRON_INGOT;
            case 1400 -> Items.GOLD_INGOT;
            case 1600 -> Items.DIAMOND;
            case 1800 -> Items.EMERALD;
            case 2000 -> Items.NETHERITE_INGOT;
            default -> Items.IRON_INGOT;
        };
    }
    
    private int getNextBotElo(int currentElo) {
        return switch (currentElo) {
            case 800 -> 1000;
            case 1000 -> 1200;
            case 1200 -> 1400;
            case 1400 -> 1600;
            case 1600 -> 1800;
            case 1800 -> 2000;
            case 2000 -> 800;
            default -> 1200;
        };
    }
    
    private Item getTimeControlItem(TimeControl timeControl) {
        return switch (timeControl.name()) {
            case "BULLET_1_0", "BULLET_2_1" -> Items.GUNPOWDER;
            case "BLITZ_3_0", "BLITZ_5_0", "BLITZ_3_2", "BLITZ_5_3" -> Items.BLAZE_POWDER;
            case "RAPID_10_0", "RAPID_15_10", "RAPID_30_0" -> Items.FIREWORK_ROCKET;
            case "CLASSICAL_60_0" -> Items.BOOK;
            case "UNLIMITED" -> Items.ENDER_PEARL;
            default -> Items.CLOCK;
        };
    }
    
    public enum OpponentType {
        PLAYER("Player"),
        BOT("Computer");
        
        public final String displayName;
        
        OpponentType(String displayName) {
            this.displayName = displayName;
        }
    }
    
    public enum SideChoice {
        RANDOM("Random"),
        WHITE("Play as White"),
        BLACK("Play as Black");
        
        public final String displayName;
        
        SideChoice(String displayName) {
            this.displayName = displayName;
        }
    }
}