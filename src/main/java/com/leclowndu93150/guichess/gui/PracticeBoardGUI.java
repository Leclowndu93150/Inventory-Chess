package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessBoard;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

// Practice board GUI for analyzing positions
public class PracticeBoardGUI extends SimpleGui {
    private ChessBoard board;
    private final ServerPlayer player;

    public PracticeBoardGUI(ServerPlayer player) {
        super(MenuType.GENERIC_9x6, player, true);
        this.player = player;
        this.board = new ChessBoard();
        setTitle(Component.literal("§dPractice Board"));
        setupBoard();
    }

    public PracticeBoardGUI(ServerPlayer player, String fen) {
        super(MenuType.GENERIC_9x6, player, true);
        this.player = player;
        setTitle(Component.literal("§dPosition Analysis"));

        // Load from FEN - this would require FEN parsing implementation
        this.board = new ChessBoard(); // For now, start with initial position
        setupBoard();
    }

    private void setupBoard() {
        updateBoardDisplay();
        setupPracticeUtilities();
    }

    private void updateBoardDisplay() {
        // Clear all slots first
        for (int i = 0; i < 72; i++) {
            clearSlot(i);
        }

        // Update the 8x8 chess board using Bukkit-style positioning
        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;

            ChessPosition position = new ChessPosition(col, row);
            ChessPiece piece = board.getPiece(position);

            // Calculate slot index using Bukkit formula
            int slotIndex = i + i / 8;

            if (piece != null) {
                setSlot(slotIndex, new GuiElementBuilder(Items.GRAY_DYE)
                        .setCustomModelData(piece.modelData)
                        .setName(piece.displayName.copy().append(Component.literal(" - " + position.toNotation()))));
            } else {
                boolean isLight = (col + row) % 2 == 0;
                BoardSquare square = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;
                setSlot(slotIndex, new GuiElementBuilder(Items.GRAY_DYE)
                        .setCustomModelData(square.modelData)
                        .setName(Component.literal(position.toNotation()))
                        .hideDefaultTooltip());
            }
        }
    }

    private void setupPracticeUtilities() {
        // Reset board
        setSlot(8, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal("§cReset Board"))
                .setCallback((index, type, action, gui) -> {
                    board = new ChessBoard();
                    updateBoardDisplay();
                }));

        // Analyze with Stockfish
        setSlot(17, new GuiElementBuilder(Items.ENDER_EYE)
                .setName(Component.literal("§dAnalyze Position"))
                .setCallback((index, type, action, gui) -> {
                    StockfishIntegration.getInstance().analyzePosition(board.toFEN(), analysis -> {
                        player.sendSystemMessage(Component.literal("§dAnalysis: " + analysis));
                    });
                }));

        // Get FEN
        setSlot(26, new GuiElementBuilder(Items.BOOK)
                .setName(Component.literal("§7Show FEN"))
                .setCallback((index, type, action, gui) -> {
                    player.sendSystemMessage(Component.literal("§7FEN: " + board.toFEN()));
                }));

        // Exit button
        setSlot(35, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cClose Practice Board"))
                .setCallback((index, type, action, gui) -> {
                    close();
                }));
    }
}