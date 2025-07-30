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

    public static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        
        List<Filterable<Component>> pages = List.of(
            // Page 1: Welcome
            Filterable.passThrough(Component.literal(
                "§l§nWelcome to GUIChess!§r\n\n" +
                "Complete chess in Minecraft!\n\n" +
                "§lQuick Start:§r\n" +
                "• §a/chess board§r - Practice\n" +
                "• §a/chess bot§r - vs AI\n" +
                "• §a/chess challenge§r - vs Players"
            )),
            
            // Page 2: Essential Commands
            Filterable.passThrough(Component.literal(
                "§l§nEssential Commands§r\n\n" +
                "§a/chess play <player>§r\nChallenge someone\n\n" +
                "§a/chess accept§r\nAccept challenge\n\n" +
                "§a/chess bot [elo]§r\nPlay AI (500-3000)"
            )),
            
            // Page 3: Game Commands
            Filterable.passThrough(Component.literal(
                "§l§nGame Commands§r\n\n" +
                "§a/chess resign§r\nGive up current game\n\n" +
                "§a/chess draw§r\nOffer a draw\n\n" +
                "§a/chess spectate§r\nWatch active games"
            )),
            
            // Page 4: Stats & History
            Filterable.passThrough(Component.literal(
                "§l§nStats & History§r\n\n" +
                "§a/chess stats§r\nYour game statistics\n\n" +
                "§a/chess leaderboard§r\nTop 10 players\n\n" +
                "§a/chess history§r\nRecent games"
            )),
            
            // Page 5: Analysis Tools
            Filterable.passThrough(Component.literal(
                "§l§nAnalysis Tools§r\n\n" +
                "§a/chess analyze§r\nReview last game\n\n" +
                "§a/chess hint§r\nGet move suggestion\n\n" +
                "§a/chess fen§r\nPosition notation"
            )),
            
            // Page 6: How to Play
            Filterable.passThrough(Component.literal(
                "§l§nControls§r\n\n" +
                "§lLeft-click:§r\n" +
                "• Select piece\n" +
                "• Make move\n\n" +
                "§lRight-click:§r\n" +
                "• Show valid moves"
            )),
            
            // Page 7: Board Colors
            Filterable.passThrough(Component.literal(
                "§l§nBoard Colors§r\n\n" +
                "§aGreen squares:§r\nValid moves\n\n" +
                "§cRed squares:§r\nCapture moves\n\n" +
                "§6Gold outline:§r\nKing in check"
            )),
            
            // Page 8: Time Controls
            Filterable.passThrough(Component.literal(
                "§l§nTime Controls§r\n\n" +
                "§6Bullet:§r 1+0, 2+1\n§eBlitz:§r 3+0, 5+0, 5+3\n§aRapid:§r 10+0, 15+10\n§bClassical:§r 60+0\n\n" +
                "Format: Minutes+Increment"
            )),
            
            // Page 9: Bot Games
            Filterable.passThrough(Component.literal(
                "§l§nBot Games§r\n\n" +
                "§a/chess bot§r\nAuto-matched difficulty\n\n" +
                "§a/chess bot 1500§r\nSpecific ELO level\n\n" +
                "§a/chess bvb 1200 1800§r\nWatch bots play"
            )),
            
            // Page 10: ELO System
            Filterable.passThrough(Component.literal(
                "§l§nELO Rating§r\n\n" +
                "• Start at 1200\n" +
                "• Win: gain points\n" +
                "• Lose: lose points\n" +
                "• Draw: small change\n\n" +
                "Climb the leaderboard!"
            )),
            
            // Page 11: Win Conditions
            Filterable.passThrough(Component.literal(
                "§l§nWin Conditions§r\n\n" +
                "§lWin by:§r\n" +
                "• Checkmate\n" +
                "• Time runs out\n" +
                "• Opponent resigns"
            )),
            
            // Page 12: Draw Conditions
            Filterable.passThrough(Component.literal(
                "§l§nDraw Conditions§r\n\n" +
                "• Stalemate\n" +
                "• Insufficient material\n" +
                "• 50-move rule\n" +
                "• Threefold repetition\n" +
                "• Mutual agreement"
            )),
            
            // Page 13: Chess Tips
            Filterable.passThrough(Component.literal(
                "§l§nChess Tips§r\n\n" +
                "§lOpening:§r\n" +
                "• Control center\n" +
                "• Develop pieces\n" +
                "• Castle early\n\n" +
                "§lTactics:§r Look for forks!"
            )),
            
            // Page 14: Improvement
            Filterable.passThrough(Component.literal(
                "§l§nGet Better§r\n\n" +
                "• Play daily\n" +
                "• Analyze your games\n" +
                "• Use hints when stuck\n" +
                "• Watch bot games\n\n" +
                "§aGood luck!§r"
            )),
            
            // Page 15: Advanced Features
            Filterable.passThrough(Component.literal(
                "§l§nAdvanced§r\n\n" +
                "§a/chess fen <notation>§r\nLoad custom position\n\n" +
                "§a/chess spectate <player>§r\nWatch specific player\n\n" +
                "Perfect for learning!"
            ))
        );
        
        WrittenBookContent bookContent = new WrittenBookContent(
            Filterable.passThrough("Inventory Chess Player Guide"),
            "Leclowndu93150",
            0,
            pages,
            true
        );
        
        
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);
        return book;
    }
}