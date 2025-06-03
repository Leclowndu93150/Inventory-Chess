package com.leclowndu93150.guichess.util;

import com.leclowndu93150.guichess.chess.pieces.ChessPiece;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SquareGenerator {

    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);  // Original beige/cream
    private static final Color DARK_SQUARE = new Color(181, 136, 99);    // Original brown

    private static final Color SELECTED_OVERLAY = new Color(255, 235, 59, 100);     // Soft golden yellow
    private static final Color LAST_MOVE_OVERLAY = new Color(255, 183, 77, 90);     // Soft orange
    private static final Color CAPTURE_OVERLAY = new Color(244, 67, 54, 90);        // Soft red
    private static final Color CHECK_OVERLAY = new Color(198, 40, 40, 120);         // Deep red

    public static void main(String[] args) throws IOException {
        String basePath = "src/main/resources/assets/guichess/textures/item/";

        // Create directories
        new File(basePath + "board/").mkdirs();
        new File(basePath + "chess/").mkdirs();
        new File(basePath + "pieces_overlay/").mkdirs();

        System.out.println("Generating chess textures...");

        // Generate basic board squares
        generateBasicSquares(basePath + "board/");

        // Generate piece overlay backgrounds for all chess pieces
        generatePieceOverlays(basePath + "pieces_overlay/");

        System.out.println("✓ Generated all chess textures:");
        System.out.println("  - Basic board squares");
        System.out.println("  - Special highlighting squares");
        System.out.println("  - " + (ChessPiece.values().length * 6) + " piece overlay variations");
        System.out.println("  - Each piece now has: normal_light, normal_dark, selected_light, selected_dark, capture_light, capture_dark backgrounds");
    }

    private static void generateBasicSquares(String boardPath) throws IOException {
        // Basic squares
        createSquare(boardPath + "light_square.png", LIGHT_SQUARE);
        createSquare(boardPath + "dark_square.png", DARK_SQUARE);

        // Special highlighting squares
        createHighlightedSquare(boardPath + "selected_square.png", LIGHT_SQUARE, SELECTED_OVERLAY);
        createHighlightedSquare(boardPath + "last_move_from.png", LIGHT_SQUARE, LAST_MOVE_OVERLAY);
        createHighlightedSquare(boardPath + "last_move_to.png", LIGHT_SQUARE, LAST_MOVE_OVERLAY);
        createHighlightedSquare(boardPath + "check_square.png", LIGHT_SQUARE, CHECK_OVERLAY);
        createHighlightedSquare(boardPath + "capture_move.png", LIGHT_SQUARE, CAPTURE_OVERLAY);
    }

    private static void generatePieceOverlays(String overlayPath) throws IOException {
        for (ChessPiece piece : ChessPiece.values()) {
            String baseName = piece.getModelName();

            BufferedImage pieceTexture = loadPieceTexture(baseName);
            if (pieceTexture == null) {
                System.out.println("⚠ Warning: Could not load texture for " + baseName + ", creating placeholder");
                pieceTexture = createPlaceholderPieceTexture(piece);
            }

            createPieceWithBackground(overlayPath + baseName + "_light.png", pieceTexture, LIGHT_SQUARE, null);
            createPieceWithBackground(overlayPath + baseName + "_dark.png", pieceTexture, DARK_SQUARE, null);

            createPieceWithBackground(overlayPath + baseName + "_selected_light.png", pieceTexture, LIGHT_SQUARE, SELECTED_OVERLAY);
            createPieceWithBackground(overlayPath + baseName + "_selected_dark.png", pieceTexture, DARK_SQUARE, SELECTED_OVERLAY);

            createPieceWithBackground(overlayPath + baseName + "_capture_light.png", pieceTexture, LIGHT_SQUARE, CAPTURE_OVERLAY);
            createPieceWithBackground(overlayPath + baseName + "_capture_dark.png", pieceTexture, DARK_SQUARE, CAPTURE_OVERLAY);

            createPieceWithBackground(overlayPath + baseName + "_lastmove_light.png", pieceTexture, LIGHT_SQUARE, LAST_MOVE_OVERLAY);
            createPieceWithBackground(overlayPath + baseName + "_lastmove_dark.png", pieceTexture, DARK_SQUARE, LAST_MOVE_OVERLAY);

            if (piece.getType().name().equals("KING")) {
                createPieceWithBackground(overlayPath + baseName + "_check_light.png", pieceTexture, LIGHT_SQUARE, CHECK_OVERLAY);
                createPieceWithBackground(overlayPath + baseName + "_check_dark.png", pieceTexture, DARK_SQUARE, CHECK_OVERLAY);
            }
        }
    }

    private static BufferedImage loadPieceTexture(String pieceName) {
        try {
            String piecePath = "src/main/resources/assets/guichess/textures/item/chess/" + pieceName + ".png";
            File pieceFile = new File(piecePath);
            if (pieceFile.exists()) {
                return ImageIO.read(pieceFile);
            }
        } catch (IOException e) {
            System.err.println("Could not load piece texture: " + pieceName);
        }
        return null;
    }

    private static BufferedImage createPlaceholderPieceTexture(ChessPiece piece) {
        BufferedImage placeholder = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color pieceColor = piece.isWhite() ? Color.WHITE : Color.BLACK;
        g.setColor(pieceColor);
        g.fillOval(2, 2, 12, 12);
        g.setColor(pieceColor.darker());
        g.drawOval(2, 2, 12, 12);

        g.setColor(piece.isWhite() ? Color.BLACK : Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        String typeChar = piece.getType().name().substring(0, 1);
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(typeChar)) / 2;
        int y = (16 + fm.getAscent()) / 2;
        g.drawString(typeChar, x, y);

        g.dispose();
        return placeholder;
    }

    private static void createPieceWithBackground(String filename, BufferedImage pieceTexture, Color backgroundColor, Color overlay) throws IOException {
        BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(backgroundColor);
        g.fillRect(0, 0, 16, 16);

        if (overlay != null) {
            g.setColor(overlay);
            g.fillRect(0, 0, 16, 16);
        }

        g.drawImage(pieceTexture, 0, 0, null);

        g.dispose();
        ImageIO.write(result, "PNG", new File(filename));
    }

    private static void createSquare(String filename, Color color) throws IOException {
        BufferedImage square = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(color);
        g.fillRect(0, 0, 16, 16);

        g.dispose();
        ImageIO.write(square, "PNG", new File(filename));
    }

    private static void createHighlightedSquare(String filename, Color baseColor, Color overlayColor) throws IOException {
        BufferedImage square = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(baseColor);
        g.fillRect(0, 0, 16, 16);

        g.setColor(overlayColor);
        g.fillRect(0, 0, 16, 16);

        g.dispose();
        ImageIO.write(square, "PNG", new File(filename));
    }
}