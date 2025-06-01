package com.leclowndu93150.guichess.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SquareGenerator {
    public static void main(String[] args) throws IOException {
        String basePath = "src/main/resources/assets/guichess/textures/item/board/";
        new File(basePath).mkdirs();

        // Light square (beige/cream)
        BufferedImage lightSquare = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = lightSquare.createGraphics();
        g1.setColor(new Color(240, 217, 181));
        g1.fillRect(0, 0, 16, 16);
        g1.dispose();
        ImageIO.write(lightSquare, "PNG", new File(basePath + "light_square.png"));

        // Dark square (brown)
        BufferedImage darkSquare = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = darkSquare.createGraphics();
        g2.setColor(new Color(181, 136, 99));
        g2.fillRect(0, 0, 16, 16);
        g2.dispose();
        ImageIO.write(darkSquare, "PNG", new File(basePath + "dark_square.png"));

        System.out.println("Created light_square.png and dark_square.png");
    }
}