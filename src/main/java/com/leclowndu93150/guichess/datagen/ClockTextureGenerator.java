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
 * Generates positioned digit textures for the clock overlay system.
 * Instead of generating thousands of clock combinations, we generate 40 textures:
 * 10 digits (0-9) × 4 positions = 40 textures
 */
public class ClockTextureGenerator {
    
    // Item texture size (Minecraft standard)
    private static final int TEXTURE_SIZE = 16;
    
    // Clock dimensions
    private static final int CLOCK_WIDTH = 31;
    private static final int CLOCK_HEIGHT = 19;
    
    // Digit positions on the clock (x, y coordinates for top-left of each digit)
    private static final int[][] DIGIT_POSITIONS = {
        {5, 6},   // Position 1: First digit (minute tens) - 5:6 to 8:12
        {10, 6},  // Position 2: Second digit (minute ones) - 10:6 to 13:12
        {17, 6},  // Position 3: Third digit (second tens) - 17:6 to 20:12
        {22, 6}   // Position 4: Fourth digit (second ones) - 22:6 to 25:12
    };
    
    // Number dimensions - 4 pixels wide, 7 pixels high
    private static final int DIGIT_WIDTH = 4;
    private static final int DIGIT_HEIGHT = 7;
    
    /**
     * Generates positioned digit textures for clock overlays.
     * Uses existing digit textures from 0.png to 9.png and creates positioned versions.
     */
    public static void generatePositionedDigitTextures(Path baseDir) throws IOException {
        Path texturesDir = baseDir.resolve("assets/guichess/textures/item");
        Path numbersDir = texturesDir.resolve("numbers");
        
        // Ensure the numbers directory exists
        if (!Files.exists(numbersDir)) {
            throw new IOException("Numbers directory not found: " + numbersDir + 
                ". Please ensure digit textures 0.png through 9.png exist.");
        }
        
        // For each digit (0-9)
        for (int digit = 0; digit <= 9; digit++) {
            // Load the existing digit texture
            Path digitPath = numbersDir.resolve(digit + ".png");
            
            if (!Files.exists(digitPath)) {
                System.err.println("Warning: Digit texture not found: " + digitPath);
                System.err.println("Creating placeholder for digit " + digit);
                // Create placeholder and save it
                BufferedImage placeholder = createPlaceholderDigit(digit);
                ImageIO.write(placeholder, "PNG", digitPath.toFile());
            }
            
            BufferedImage digitTexture = ImageIO.read(digitPath.toFile());
            
            // Validate digit texture dimensions
            if (digitTexture.getWidth() != DIGIT_WIDTH || digitTexture.getHeight() != DIGIT_HEIGHT) {
                System.out.println("Note: Digit " + digit + " has dimensions " + 
                    digitTexture.getWidth() + "x" + digitTexture.getHeight() + 
                    ", expected " + DIGIT_WIDTH + "x" + DIGIT_HEIGHT);
            }
            
            // For each position (1-4)
            for (int pos = 0; pos < 4; pos++) {
                // Create a transparent texture the size of the clock
                BufferedImage positionedTexture = new BufferedImage(
                    CLOCK_WIDTH, CLOCK_HEIGHT, BufferedImage.TYPE_INT_ARGB
                );
                
                Graphics2D g = positionedTexture.createGraphics();
                // Use nearest neighbor to preserve pixel art
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                                   RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                
                // Draw the digit at the correct position for this slot
                int x = DIGIT_POSITIONS[pos][0];
                int y = DIGIT_POSITIONS[pos][1];
                
                // Scale the digit to fit the target size if needed
                g.drawImage(digitTexture, x, y, x + DIGIT_WIDTH, y + DIGIT_HEIGHT,
                           0, 0, digitTexture.getWidth(), digitTexture.getHeight(), null);
                
                g.dispose();
                
                // Save the positioned texture
                String filename = digit + "_pos" + (pos + 1) + ".png";
                Path outputPath = numbersDir.resolve(filename);
                ImageIO.write(positionedTexture, "PNG", outputPath.toFile());
                
                System.out.println("Generated: " + filename + " (digit " + digit + 
                    " at position " + (pos + 1) + ": " + x + "," + y + ")");
            }
        }
        
        System.out.println("Successfully generated positioned digit textures in: " + numbersDir);
        System.out.println("Created 40 positioned textures from 10 base digit textures.");
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
            generatePositionedDigitTextures(baseDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}