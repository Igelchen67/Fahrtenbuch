package de.eedesign.fahrtenbuch;

public class Vehicle {
    public String name;
    /** Farbe als 0xRRGGBB. */
    public int color;

    public Vehicle(String name, int color) {
        this.name = name;
        this.color = color & 0xFFFFFF;
    }

    /** Liefert Schwarz oder Weiß als lesbare Schriftfarbe für den Hintergrund. */
    public static int textColorFor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 150 ? 0x000000 : 0xFFFFFF;
    }

    public static final int[] PALETTE = {
            0xE53935, 0xD81B60, 0x8E24AA, 0x5E35B1, 0x3949AB, 0x1E88E5,
            0x039BE5, 0x00ACC1, 0x00897B, 0x43A047, 0x7CB342, 0xC0CA33,
            0xFDD835, 0xFFB300, 0xFB8C00, 0xF4511E, 0x6D4C41, 0x757575
    };
}
