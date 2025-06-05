package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.pieces.PieceColor;
import com.leclowndu93150.guichess.chess.util.TimeControl;
import com.leclowndu93150.guichess.game.ChessChallenge;
import com.leclowndu93150.guichess.game.GameManager;
import com.leclowndu93150.guichess.util.ChessSoundManager;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Advanced challenge creation interface implementing a step-by-step wizard flow for comprehensive game setup.
 * 
 * <p>This GUI provides a more sophisticated challenge creation experience compared to the basic configuration
 * interface, implementing a multi-step wizard that guides players through comprehensive game setup including
 * advanced features like item betting and detailed bot configuration.
 * 
 * <h3>Wizard Flow Steps:</h3>
 * <ol>
 *   <li><strong>OPPONENT_TYPE:</strong> Choose between human players and AI bots</li>
 *   <li><strong>BOT_CONFIG:</strong> Configure bot difficulty (ELO) and hint settings (bot games only)</li>
 *   <li><strong>HUMAN_SELECT:</strong> Select specific human opponent from available players</li>
 *   <li><strong>TIME_CONTROL:</strong> Choose from standard chess time control formats</li>
 *   <li><strong>BET_CHOICE:</strong> Decide whether to enable item betting (human games only)</li>
 *   <li><strong>BET_ITEMS:</strong> Select specific items to wager if betting is enabled</li>
 *   <li><strong>SIDE_SELECT:</strong> Choose to play as White, Black, or random assignment</li>
 *   <li><strong>CONFIRM:</strong> Review all settings and send the challenge</li>
 * </ol>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><strong>Wizard Navigation:</strong> Forward/backward navigation with validation</li>
 *   <li><strong>Dynamic Flow:</strong> Step progression adapts based on previous selections</li>
 *   <li><strong>Item Integration:</strong> Direct inventory interaction for betting system</li>
 *   <li><strong>Real-time Validation:</strong> Prevents invalid configurations at each step</li>
 *   <li><strong>State Preservation:</strong> Maintains configuration across navigation</li>
 *   <li><strong>Error Recovery:</strong> Returns items if GUI is closed unexpectedly</li>
 * </ul>
 * 
 * <h3>Advanced Configuration Options:</h3>
 * <ul>
 *   <li><strong>Bot ELO Scaling:</strong> Granular difficulty adjustment (500-3000 ELO)</li>
 *   <li><strong>Hint Limits:</strong> Configurable analysis assistance (0-10 hints)</li>
 *   <li><strong>Item Betting:</strong> Up to 6 items can be wagered per challenge</li>
 *   <li><strong>Visual Feedback:</strong> Real-time display of selected items and configuration</li>
 * </ul>
 * 
 * <h3>State Management:</h3>
 * The GUI maintains comprehensive state throughout the wizard flow:
 * <ul>
 *   <li>All configuration options are preserved during navigation</li>
 *   <li>Invalid steps prevent progression with clear feedback</li>
 *   <li>Items are safely managed with automatic return on cancellation</li>
 *   <li>Configuration validation occurs at each step transition</li>
 * </ul>
 * 
 * <h3>Integration Points:</h3>
 * <ul>
 *   <li><strong>GameManager:</strong> Creates bot games and human challenges</li>
 *   <li><strong>Inventory System:</strong> Direct interaction for item betting</li>
 *   <li><strong>ChessChallenge:</strong> Creates properly configured challenge objects</li>
 *   <li><strong>Player Management:</strong> Real-time availability checking</li>
 * </ul>
 * 
 * <p>This interface is designed for players who want comprehensive control over their
 * challenge configuration, providing access to all available game options through
 * an intuitive step-by-step process.
 * 
 * @see ChallengeConfigGUI For the simplified configuration interface
 * @see ChessChallenge For the challenge data structure created by this wizard
 * @see GameManager For challenge and game creation integration
 */
public class ChallengeFlowGUI extends SimpleGui {
    private enum Step {
        OPPONENT_TYPE,      // Bot or Human
        BOT_CONFIG,        // Bot ELO/hints configuration
        HUMAN_SELECT,      // Select which player
        TIME_CONTROL,      // Choose time control
        BET_CHOICE,        // Choose whether to bet items
        BET_ITEMS,         // Select items to bet
        SIDE_SELECT,       // Choose black/white/random
        CONFIRM            // Final confirmation
    }
    
    private final ServerPlayer player;
    private Step currentStep = Step.OPPONENT_TYPE;
    
    // Challenge configuration
    private boolean isBot = false;
    private int botElo = 1500;
    private int hintsAllowed = 0;
    private ServerPlayer humanOpponent = null;
    private boolean wantsToBet = false;
    private List<ItemStack> betItems = new ArrayList<>();
    private PieceColor preferredSide = null; // null = random
    private TimeControl timeControl = TimeControl.RAPID_10_0;
    
    // GUI Constants
    private static final int BACK_BUTTON_SLOT = 18;
    private static final int NEXT_BUTTON_SLOT = 26;
    
    public ChallengeFlowGUI(ServerPlayer player) {
        super(MenuType.GENERIC_9x3, player, false);
        this.player = player;
        setTitle(Component.literal("§0Create Chess Challenge"));
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
        
        switch (currentStep) {
            case OPPONENT_TYPE -> setupOpponentTypeScreen();
            case BOT_CONFIG -> setupBotConfigScreen();
            case HUMAN_SELECT -> setupHumanSelectScreen();
            case TIME_CONTROL -> setupTimeControlScreen();
            case BET_CHOICE -> setupBetChoiceScreen();
            case BET_ITEMS -> setupBetItemsScreen();
            case SIDE_SELECT -> setupSideSelectScreen();
            case CONFIRM -> setupConfirmScreen();
        }
        
        setupNavigationButtons();
    }
    
    private void refreshCurrentScreen() {
        // Update only the current screen content without clearing - prevents flickering
        switch (currentStep) {
            case OPPONENT_TYPE -> setupOpponentTypeScreen();
            case BOT_CONFIG -> setupBotConfigScreen();
            case HUMAN_SELECT -> setupHumanSelectScreen();
            case TIME_CONTROL -> setupTimeControlScreen();
            case BET_CHOICE -> setupBetChoiceScreen();
            case BET_ITEMS -> setupBetItemsScreen();
            case SIDE_SELECT -> setupSideSelectScreen();
            case CONFIRM -> setupConfirmScreen();
        }
        
        setupNavigationButtons();
    }
    
    private void setupNavigationButtons() {
        // Add navigation buttons
        if (currentStep != Step.OPPONENT_TYPE) {
            setSlot(BACK_BUTTON_SLOT, createBackButton());
        }
        
        if (currentStep != Step.CONFIRM) {
            setSlot(NEXT_BUTTON_SLOT, createNextButton());
        }
    }
    
    private void setupOpponentTypeScreen() {
        setTitle(Component.literal("§0Choose Opponent Type"));
        
        // Bot option
        setSlot(11, new GuiElementBuilder(Items.IRON_GOLEM_SPAWN_EGG)
                .setName(Component.literal("§6Play vs Bot"))
                .addLoreLine(Component.literal("§7Play against computer"))
                .addLoreLine(Component.literal("§7Configure difficulty and hints"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§eClick to select"))
                .glow(isBot)
                .setCallback((index, type, action, gui) -> {
                    isBot = true;
                    humanOpponent = null;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
        
        // Human option
        setSlot(15, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Component.literal("§aPlay vs Human"))
                .addLoreLine(Component.literal("§7Challenge another player"))
                .addLoreLine(Component.literal("§7Option to bet items"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§eClick to select"))
                .glow(!isBot)
                .setCallback((index, type, action, gui) -> {
                    isBot = false;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
    }
    
    private void setupBotConfigScreen() {
        setTitle(Component.literal("§0Configure Bot"));
        
        // ELO Display
        setSlot(4, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .setName(Component.literal("§6Bot ELO: §f" + botElo))
                .addLoreLine(Component.literal("§7Current difficulty rating"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§aLeft click: +100"))
                .addLoreLine(Component.literal("§cRight click: -100"))
                .addLoreLine(Component.literal("§eShift click: ±500"))
                .setCallback((index, type, action, gui) -> {
                    int change = type.shift ? 500 : 100;
                    if (type.isLeft) {
                        botElo = Math.min(3000, botElo + change);
                    } else if (type.isRight) {
                        botElo = Math.max(500, botElo - change);
                    }
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
        
        // Hints Display
        setSlot(13, new GuiElementBuilder(Items.GLOWSTONE_DUST)
                .setName(Component.literal("§eHints Allowed: §f" + hintsAllowed))
                .addLoreLine(Component.literal("§7Number of hints you can use"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§aLeft click: +1"))
                .addLoreLine(Component.literal("§cRight click: -1"))
                .setCount(Math.max(1, hintsAllowed))
                .setCallback((index, type, action, gui) -> {
                    if (type.isLeft) {
                        hintsAllowed = Math.min(10, hintsAllowed + 1);
                    } else if (type.isRight) {
                        hintsAllowed = Math.max(0, hintsAllowed - 1);
                    }
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
    }
    
    private void setupHumanSelectScreen() {
        setTitle(Component.literal("§0Select Opponent"));
        
        List<ServerPlayer> availablePlayers = new ArrayList<>();
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            if (!p.equals(player) && !GameManager.getInstance().isPlayerBusy(p)) {
                availablePlayers.add(p);
            }
        }
        
        if (availablePlayers.isEmpty()) {
            setSlot(13, new GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("§cNo players available"))
                    .addLoreLine(Component.literal("§7All players are in games")));
            return;
        }
        
        // Display up to 18 players (2 rows)
        int slot = 0;
        for (ServerPlayer p : availablePlayers) {
            if (slot >= 18) break;
            
            boolean selected = p.equals(humanOpponent);
            setSlot(slot, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setSkullOwner(p.getGameProfile(), player.getServer())
                    .setName(Component.literal((selected ? "§a" : "§f") + p.getName().getString()))
                    .addLoreLine(Component.literal("§7Click to select"))
                    .glow(selected)
                    .setCallback((index, type, action, gui) -> {
                        humanOpponent = p;
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SELECT);
                        refreshCurrentScreen();
                    }));
            slot++;
        }
    }
    
    private void setupTimeControlScreen() {
        setTitle(Component.literal("§0Choose Time Control"));
        
        TimeControl[] timeControls = TimeControl.values();
        int slot = 0;
        
        for (TimeControl tc : timeControls) {
            if (slot >= 18) break; // Only show first 18 options
            
            boolean selected = tc.equals(timeControl);
            setSlot(slot, new GuiElementBuilder(getTimeControlItem(tc))
                    .setName(Component.literal((selected ? "§a" : "§f") + tc.displayName))
                    .addLoreLine(Component.literal("§7" + tc.name()))
                    .addLoreLine(Component.literal(""))
                    .addLoreLine(Component.literal("§eClick to select"))
                    .glow(selected)
                    .setCallback((index, type, action, gui) -> {
                        timeControl = tc;
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SELECT);
                        refreshCurrentScreen();
                    }));
            slot++;
        }
    }
    
    private void setupBetChoiceScreen() {
        setTitle(Component.literal("§0Bet Items?"));
        
        setSlot(11, new GuiElementBuilder(Items.GOLD_INGOT)
                .setName(Component.literal("§6Yes, bet items"))
                .addLoreLine(Component.literal("§7Winner takes all"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§eClick to continue"))
                .glow(wantsToBet)
                .setCallback((index, type, action, gui) -> {
                    wantsToBet = true;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
        
        setSlot(15, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cNo betting"))
                .addLoreLine(Component.literal("§7Just a friendly game"))
                .addLoreLine(Component.literal(""))
                .addLoreLine(Component.literal("§eClick to continue"))
                .glow(!wantsToBet)
                .setCallback((index, type, action, gui) -> {
                    wantsToBet = false;
                    betItems.clear();
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
    }
    
    private void setupBetItemsScreen() {
        setTitle(Component.literal("§0Select Items to Bet"));
        
        // Create black stained glass border
        GuiElementBuilder blackGlass = new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(""))
                .hideDefaultTooltip();
        
        // Fill GUI with black glass except betting area
        for (int i = 0; i < 27; i++) {
            if (i != BACK_BUTTON_SLOT && i != NEXT_BUTTON_SLOT) {
                setSlot(i, blackGlass);
            }
        }
        
        // Clear the betting area (slots 3-5, 12-14)
        int[] betSlots = {3, 4, 5, 12, 13, 14};
        for (int i = 0; i < betSlots.length; i++) {
            int slot = betSlots[i];
            clearSlot(slot);
            
            // If we have a bet item for this position, show it
            if (i < betItems.size()) {
                ItemStack item = betItems.get(i).copy();
                final int itemIndex = i;
                setSlot(slot, new GuiElementBuilder(item)
                        .setCallback((index, type, action, gui) -> {
                            if (type.isRight) {
                                // Right click to remove - return item to player
                                ItemStack removed = betItems.remove(itemIndex);
                                if (!player.getInventory().add(removed)) {
                                    player.drop(removed, false);
                                }
                                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                                refreshCurrentScreen();
                            }
                        }));
            }
        }
        
        // Allow clicking on empty bet slots to add items from inventory
        for (int i = 0; i < betSlots.length; i++) {
            if (i >= betItems.size()) {
                int slot = betSlots[i];
                setSlot(slot, new GuiElementBuilder(Items.AIR)
                        .setName(Component.literal("§7Click with item to add"))
                        .setCallback((index, type, action, gui) -> {
                            ItemStack cursor = player.containerMenu.getCarried();
                            if (!cursor.isEmpty() && betItems.size() < 6) {
                                // Store the item and remove from cursor
                                betItems.add(cursor.copy());
                                player.containerMenu.setCarried(ItemStack.EMPTY);
                                ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                                refreshCurrentScreen();
                            }
                        }));
            }
        }
    }
    
    private void setupSideSelectScreen() {
        setTitle(Component.literal("§0Choose Your Side"));
        
        setSlot(10, new GuiElementBuilder(Items.WHITE_WOOL)
                .setName(Component.literal("§fPlay as White"))
                .addLoreLine(Component.literal("§7Move first"))
                .glow(preferredSide == PieceColor.WHITE)
                .setCallback((index, type, action, gui) -> {
                    preferredSide = PieceColor.WHITE;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
        
        setSlot(13, new GuiElementBuilder(Items.GRAY_WOOL)
                .setName(Component.literal("§7Random Side"))
                .addLoreLine(Component.literal("§7Let fate decide"))
                .glow(preferredSide == null)
                .setCallback((index, type, action, gui) -> {
                    preferredSide = null;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
        
        setSlot(16, new GuiElementBuilder(Items.BLACK_WOOL)
                .setName(Component.literal("§8Play as Black"))
                .addLoreLine(Component.literal("§7Move second"))
                .glow(preferredSide == PieceColor.BLACK)
                .setCallback((index, type, action, gui) -> {
                    preferredSide = PieceColor.BLACK;
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                    refreshCurrentScreen();
                }));
    }
    
    private void setupConfirmScreen() {
        setTitle(Component.literal("§0Confirm Challenge"));
        
        // Summary
        GuiElementBuilder summary = new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§6Challenge Summary"));
        
        if (isBot) {
            summary.addLoreLine(Component.literal("§7Opponent: §fBot (ELO " + botElo + ")"));
            summary.addLoreLine(Component.literal("§7Hints: §f" + hintsAllowed));
        } else {
            summary.addLoreLine(Component.literal("§7Opponent: §f" + 
                    (humanOpponent != null ? humanOpponent.getName().getString() : "None")));
            summary.addLoreLine(Component.literal("§7Betting: §f" + (wantsToBet ? "Yes" : "No")));
            if (wantsToBet) {
                summary.addLoreLine(Component.literal("§7Items: §f" + betItems.size()));
            }
        }
        
        summary.addLoreLine(Component.literal("§7Side: §f" + 
                (preferredSide == null ? "Random" : preferredSide.toString())));
        
        setSlot(4, summary);
        
        // Send button
        setSlot(13, new GuiElementBuilder(Items.LIME_WOOL)
                .setName(Component.literal("§aSend Challenge"))
                .addLoreLine(Component.literal("§7Click to send"))
                .setCallback((index, type, action, gui) -> {
                    sendChallenge();
                }));
    }
    
    private GuiElementBuilder createBackButton() {
        return new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§cBack"))
                .setCallback((index, type, action, gui) -> {
                    goToPreviousStep();
                    ChessSoundManager.playUISound(player, ChessSoundManager.UISound.CLICK);
                });
    }
    
    private GuiElementBuilder createNextButton() {
        return new GuiElementBuilder(Items.LIME_DYE)
                .setName(Component.literal("§aNext"))
                .setCallback((index, type, action, gui) -> {
                    if (canProceed()) {
                        goToNextStep();
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
                    } else {
                        ChessSoundManager.playUISound(player, ChessSoundManager.UISound.ERROR);
                        player.sendSystemMessage(Component.literal("§cPlease complete this step first!"));
                    }
                });
    }
    
    private boolean canProceed() {
        return switch (currentStep) {
            case OPPONENT_TYPE -> true; // Always can proceed after choosing type
            case BOT_CONFIG -> true; // Bot config has defaults
            case HUMAN_SELECT -> humanOpponent != null;
            case TIME_CONTROL -> true; // Default time control is selected
            case BET_CHOICE -> true; // Choice is made either way
            case BET_ITEMS -> !wantsToBet || !betItems.isEmpty();
            case SIDE_SELECT -> true; // Random is a valid choice
            case CONFIRM -> false; // No next from confirm
        };
    }
    
    private void goToNextStep() {
        currentStep = switch (currentStep) {
            case OPPONENT_TYPE -> isBot ? Step.BOT_CONFIG : Step.HUMAN_SELECT;
            case BOT_CONFIG -> Step.TIME_CONTROL;
            case HUMAN_SELECT -> Step.TIME_CONTROL;
            case TIME_CONTROL -> isBot ? Step.SIDE_SELECT : Step.BET_CHOICE;
            case BET_CHOICE -> wantsToBet ? Step.BET_ITEMS : Step.SIDE_SELECT;
            case BET_ITEMS -> Step.SIDE_SELECT;
            case SIDE_SELECT -> Step.CONFIRM;
            case CONFIRM -> Step.CONFIRM; // Shouldn't happen
        };
        updateDisplay();
    }
    
    private void goToPreviousStep() {
        currentStep = switch (currentStep) {
            case OPPONENT_TYPE -> Step.OPPONENT_TYPE; // Can't go back from first
            case BOT_CONFIG -> Step.OPPONENT_TYPE;
            case HUMAN_SELECT -> Step.OPPONENT_TYPE;
            case TIME_CONTROL -> isBot ? Step.BOT_CONFIG : Step.HUMAN_SELECT;
            case BET_CHOICE -> Step.TIME_CONTROL;
            case BET_ITEMS -> Step.BET_CHOICE;
            case SIDE_SELECT -> {
                if (isBot) {
                    yield Step.TIME_CONTROL;
                } else if (wantsToBet) {
                    yield Step.BET_ITEMS;
                } else {
                    yield Step.BET_CHOICE;
                }
            }
            case CONFIRM -> Step.SIDE_SELECT;
        };
        updateDisplay();
    }
    
    private void clearAllSlots() {
        for (int i = 0; i < 27; i++) {
            clearSlot(i);
        }
    }
    
    private void sendChallenge() {
        if (isBot) {
            // Create bot game directly
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
            
            // Return bet items since bot games don't support betting yet
            for (ItemStack item : betItems) {
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
            }
            betItems.clear();
            
            // Determine player color
            PieceColor playerColor;
            if (preferredSide != null) {
                playerColor = preferredSide;
            } else {
                playerColor = Math.random() < 0.5 ? PieceColor.WHITE : PieceColor.BLACK;
            }
            
            // Create the bot game
            GameManager.getInstance().createBotGame(player, playerColor, timeControl, botElo, hintsAllowed);
            
            close();
        } else if (humanOpponent != null) {
            // Create challenge with bet items
            ChessChallenge challenge = new ChessChallenge(
                    player,
                    humanOpponent,
                    timeControl,
                    wantsToBet ? new ArrayList<>(betItems) : new ArrayList<>(),
                    preferredSide
            );
            
            // Clear our local list since the challenge now owns these items
            betItems.clear();
            
            GameManager.getInstance().createChallenge(challenge);
            ChessSoundManager.playUISound(player, ChessSoundManager.UISound.SUCCESS);
            close();
        }
    }
    
    @Override
    public void onClose() {
        // Return all bet items to player when closing
        for (ItemStack item : betItems) {
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
        }
        betItems.clear();
        
        // Restore original inventory to ensure survival safety
        GameManager.getInstance().restoreInventoryAfterAnalysis(player);
        super.onClose();
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
}