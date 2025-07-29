package com.leclowndu93150.guichess.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/**
 * Creates guide book for new players.
 */
public class GuideBookProvider {

    /**
     * Creates guide book with 10 pages of chess help.
     */
    public static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        
        List<Filterable<Component>> pages = List.of(
            // Page 1: Welcome
            Filterable.passThrough(Component.literal(
                "§l§nWelcome to GUIChess!§r\n\n" +
                "Complete chess system in Minecraft!\n\n" +
                "§lQuick Start:§r\n" +
                "• §a/chess board§r - Practice\n" +
                "• §a/chess bot§r - vs AI\n" +
                "• §a/chess challenge§r - vs Players\n" +
                "• §a/chess stats§r - Your stats\n" +
                "• §a/chess leaderboard§r - Top players"
            )),
            
            // Page 2: Basic Commands
            Filterable.passThrough(Component.literal(
                "§l§nCommands§r\n\n" +
                "§a/chess board§r\nPractice mode\n\n" +
                "§a/chess challenge§r\nChallenge players\n\n" +
                "§a/chess bot [elo]§r\nPlay AI (500-3000)\n\n" +
                "§a/chess spectate§r\nWatch games\n\n" +
                "§a/chess resign§r\nGive up"
            )),
            
            // Page 3: Time Controls
            Filterable.passThrough(Component.literal(
                "§l§nTime Controls§r\n\n" +
                "§6Bullet:§r 1+0, 2+1\nVery fast games\n\n" +
                "§eBlitz:§r 3+0, 5+0, 3+2, 5+3\nFast games\n\n" +
                "§aRapid:§r 10+0, 15+10, 30+0\nMedium games\n\n" +
                "§bClassical:§r 60+0\nSlow, thoughtful games"
            )),
            
            // Page 4: Playing Chess
            Filterable.passThrough(Component.literal(
                "§l§nHow to Play§r\n\n" +
                "§lControls:§r\n" +
                "• Left-click: Select piece\n" +
                "• Left-click: Move\n" +
                "• Right-click: Show moves\n\n" +
                "§lColors:§r\n" +
                "• §aGreen§r: Valid moves\n" +
                "• §cRed§r: Captures\n" +
                "• §6Gold§r: In check"
            )),
            
            // Page 5: Betting System
            Filterable.passThrough(Component.literal(
                "§l§nBetting System§r\n\n" +
                "§lHow it works:§r\n" +
                "• Wager items on games\n" +
                "• Winner takes all\n" +
                "• Draws split items\n" +
                "• Items returned if expired\n\n" +
                "§lSafety:§r\n" +
                "Your inventory is always\nprotected during games!"
            )),
            
            // Page 6: Bot Games
            Filterable.passThrough(Component.literal(
                "§l§nBot Games§r\n\n" +
                "§a/chess bot§r\nMatches your ELO\n\n" +
                "§a/chess bot 1500§r\nSpecific difficulty\n\n" +
                "§a/chess bvb 1200 1800§r\nWatch bot vs bot\n\n" +
                "§a/chess hint§r\nGet move suggestions"
            )),
            
            // Page 7: ELO System
            Filterable.passThrough(Component.literal(
                "§l§nELO Rating§r\n\n" +
                "§lHow it works:§r\n" +
                "• Start at 1200\n" +
                "• Gain points for wins\n" +
                "• Lose points for losses\n" +
                "• Small changes for draws\n\n" +
                "§a/chess leaderboard§r\nSee top 10 players!"
            )),
            
            // Page 8: Game Analysis
            Filterable.passThrough(Component.literal(
                "§l§nGame Analysis§r\n\n" +
                "§a/chess history§r\nView recent games\n\n" +
                "§a/chess analyze§r\nReview last game\n\n" +
                "§a/chess fen§r\nShow position code\n\n" +
                "Perfect for learning!"
            )),
            
            // Page 9: Win Conditions
            Filterable.passThrough(Component.literal(
                "§l§nWin/Draw/Lose§r\n\n" +
                "§lWin by:§r\n" +
                "• Checkmate\n" +
                "• Timeout\n" +
                "• Resignation\n\n" +
                "§lDraw by:§r\n" +
                "• Stalemate\n" +
                "• Insufficient material\n" +
                "• 50-move rule\n" +
                "• Agreement"
            )),
            
            // Page 10: Tips
            Filterable.passThrough(Component.literal(
                "§l§nChess Tips§r\n\n" +
                "§lBasics:§r\n" +
                "• Control center squares\n" +
                "• Develop pieces early\n" +
                "• Castle for safety\n\n" +
                "§lImprove:§r\n" +
                "• Practice daily\n" +
                "• Analyze games\n" +
                "• Use hints\n\n" +
                "§aGood luck!§r"
            ))
        );
        
        WrittenBookContent bookContent = new WrittenBookContent(
            Filterable.passThrough("GUIChess Player Guide"),
            "Leclowndu93150",
            0,
            pages,
            true
        );
        
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);
        return book;
    }
}