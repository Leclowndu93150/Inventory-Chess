package com.leclowndu93150.guichess.datagen;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates composite clock textures with digits overlaid on the base clock.
 * Creates 181 textures for times from 00:00 to 30:00 in 10-second intervals.
 */
public class ClockTextureGenerator {
    
    private static final int TEXTURE_SIZE = 16;
    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 32;
    private static final int CLOCK_WIDTH = 31;
    private static final int CLOCK_HEIGHT = 19;
    
    private static final int CLOCK_OFFSET_X = (TEXTURE_WIDTH - CLOCK_WIDTH) / 2;
    private static final int CLOCK_OFFSET_Y = (TEXTURE_HEIGHT - CLOCK_HEIGHT) / 2;
    
    /** Digit positions on the clock face: minute tens, minute ones, second tens, second ones */
    private static final int[][] BASE_DIGIT_POSITIONS = {
        {5, 6},   // Position 1: First digit (minute tens) - 5:6 to 8:12
        {10, 6},  // Position 2: Second digit (minute ones) - 10:6 to 13:12
        {17, 6},  // Position 3: Third digit (second tens) - 17:6 to 20:12
        {22, 6}   // Position 4: Fourth digit (second ones) - 22:6 to 25:12
    };
    
    private static final int[][] DIGIT_POSITIONS = new int[4][2];
    
    static {
        for (int i = 0; i < 4; i++) {
            DIGIT_POSITIONS[i][0] = BASE_DIGIT_POSITIONS[i][0] + CLOCK_OFFSET_X;
            DIGIT_POSITIONS[i][1] = BASE_DIGIT_POSITIONS[i][1] + CLOCK_OFFSET_Y;
        }
    }
    
    private static final int DIGIT_WIDTH = 4;
    private static final int DIGIT_HEIGHT = 7;
    
    /**
     * Generates composite clock textures for times in 10-second intervals.
     * Creates textures by overlaying digit images onto a base clock texture.
     * 
     * @param baseDir the base resource directory containing texture assets
     * @throws IOException if texture files cannot be read or written
     */
    public static void generateCompositeClockTextures(Path baseDir) throws IOException {
        Path texturesDir = baseDir.resolve("assets/guichess/textures/item");
        Path numbersDir = texturesDir.resolve("numbers");
        Path clockDir = texturesDir.resolve("clock");
        Path outputDir = clockDir.resolve("times");
        
        Files.createDirectories(outputDir);
        
        Path clockPath = clockDir.resolve("clock.png");
        if (!Files.exists(clockPath)) {
            throw new IOException("Base clock texture not found: " + clockPath);
        }
        
        BufferedImage baseClock = ImageIO.read(clockPath.toFile());
        
        BufferedImage[] digitTextures = new BufferedImage[10];
        for (int i = 0; i < 10; i++) {
            Path digitPath = numbersDir.resolve(i + ".png");
            if (!Files.exists(digitPath)) {
                System.err.println("Creating placeholder for digit " + i);
                digitTextures[i] = createPlaceholderDigit(i);
                ImageIO.write(digitTextures[i], "PNG", digitPath.toFile());
            } else {
                digitTextures[i] = ImageIO.read(digitPath.toFile());
            }
        }
        int generated = 0;
        for (int minutes = 0; minutes <= 30; minutes++) {
            for (int seconds = 0; seconds < 60; seconds += 10) {
                // Include 30:00 but nothing beyond
                if (minutes == 30 && seconds > 0) continue;
                
                String timeString = String.format("%02d_%02d", minutes, seconds);

                BufferedImage clockImage = new BufferedImage(
                    TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D g = clockImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                                   RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                g.drawImage(baseClock, 0, 0, null);

                int minuteTens = minutes / 10;
                int minuteOnes = minutes % 10;
                int secondTens = seconds / 10;
                int secondOnes = seconds % 10;
                
                int[] digits = {minuteTens, minuteOnes, secondTens, secondOnes};
                
                for (int pos = 0; pos < 4; pos++) {
                    BufferedImage digitTexture = digitTextures[digits[pos]];
                    int x = DIGIT_POSITIONS[pos][0];
                    int y = DIGIT_POSITIONS[pos][1];
                    
                    g.drawImage(digitTexture, x, y, x + DIGIT_WIDTH, y + DIGIT_HEIGHT,
                               0, 0, digitTexture.getWidth(), digitTexture.getHeight(), null);
                }
                
                g.dispose();

                String filename = timeString + ".png";
                ImageIO.write(clockImage, "PNG", outputDir.resolve(filename).toFile());
                generated++;
            }
        }
        
        System.out.println("Successfully generated " + generated + " composite clock textures in: " + outputDir);
    }
    
    /**
     * Creates a simple placeholder digit texture for testing
     */
    private static BufferedImage createPlaceholderDigit(int digit) {
        BufferedImage img = new BufferedImage(DIGIT_WIDTH, DIGIT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        
        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, DIGIT_WIDTH, DIGIT_HEIGHT);
        
        // Black digit
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 6));
        g.drawString(String.valueOf(digit), 1, 5);
        
        g.dispose();
        return img;
    }
    
    public static void main(String[] args) {
        try {
            // This would be run from the project root
            Path baseDir = Paths.get("src/main/resources");
            
            // Generate composite time textures only
            generateCompositeClockTextures(baseDir);
            
            System.out.println("\nGeneration complete!");
            System.out.println("- 181 composite time textures in 10-second intervals (00:00 to 30:00)");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}