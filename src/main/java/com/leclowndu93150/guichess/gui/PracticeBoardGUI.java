package com.leclowndu93150.guichess.gui;

import com.leclowndu93150.guichess.chess.board.BoardSquare;
import com.leclowndu93150.guichess.chess.board.ChessPosition;
import com.leclowndu93150.guichess.chess.pieces.ChessPiece;
import com.leclowndu93150.guichess.engine.StockfishIntegration;
import com.leclowndu93150.guichess.game.ChessBoard;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

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
        // Similar to ChessGUI but without game restrictions
        updateBoardDisplay();
        setupPracticeUtilities();
    }

    private void updateBoardDisplay() {
        // Board display logic similar to ChessGUI
        for (int i = 0; i < 48; i++) {
            int row = i / 8;
            int col = i % 8;

            ChessPosition position = new ChessPosition(col, row);
            ChessPiece piece = board.getPiece(position);

            if (piece != null) {
                setSlot(i, new GuiElementBuilder(Items.GRAY_DYE)
                        .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(piece.modelData))
                        .setComponent(DataComponents.CUSTOM_NAME, piece.displayName));
            } else {
                boolean isLight = (col + row) % 2 == 0;
                BoardSquare square = isLight ? BoardSquare.LIGHT_SQUARE : BoardSquare.DARK_SQUARE;
                setSlot(i, new GuiElementBuilder(Items.GRAY_DYE)
                        .setComponent(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(square.modelData))
                        .setComponent(DataComponents.CUSTOM_NAME, Component.literal(position.toNotation())));
            }
        }
    }

    private void setupPracticeUtilities() {
        // Reset board
        setSlot(72, new GuiElementBuilder(Items.PAPER)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§cReset Board"))
                .setCallback((slot, type, action, gui) -> {
                    board = new ChessBoard();
                    updateBoardDisplay();
                }));

        // Analyze with Stockfish
        setSlot(73, new GuiElementBuilder(Items.ENDER_EYE)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§dAnalyze Position"))
                .setCallback((slot, type, action, gui) -> {
                    StockfishIntegration.getInstance().analyzePosition(board.toFEN(), analysis -> {
                        player.sendSystemMessage(Component.literal("§dAnalysis: " + analysis));
                    });
                }));

        // Get FEN
        setSlot(74, new GuiElementBuilder(Items.BOOK)
                .setComponent(DataComponents.CUSTOM_NAME, Component.literal("§7Show FEN"))
                .setCallback((slot, type, action, gui) -> {
                    player.sendSystemMessage(Component.literal("§7FEN: " + board.toFEN()));
                }));
    }
}