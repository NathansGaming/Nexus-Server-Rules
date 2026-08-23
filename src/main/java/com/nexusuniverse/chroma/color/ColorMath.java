package com.nexusuniverse.chroma.color;

import java.awt.Color;

/**
 * Small RGB/HSB color-math helper. Used only to derive CONCRETE and
 * TERRACOTTA default colors from the 16 WOOL dye colors (see
 * DefaultColors) -- nothing here ever touches a display or GUI, it's
 * pure arithmetic, so it's safe to run headless on a server.
 *
 * If java.awt ever turns out to be unavailable on a stripped-down JRE
 * (a custom jlink image without the java.desktop module), this is the
 * one class that would need a hand-rolled RGB<->HSB replacement -- a
 * normal full JDK install (what "mvn clean package" / a standard
 * server JRE uses) has it by default.
 */
public final class ColorMath {

    private ColorMath() {
    }

    /** Concrete reads as more saturated and a touch darker than the same-named wool. */
    public static int concreteFromDye(int rgb) {
        return shift(rgb, 0f, 0.12f, -0.08f);
    }

    /** Terracotta reads as desaturated, warmed, and slightly darker -- an earthy, baked-clay version of the dye. */
    public static int terracottaFromDye(int rgb) {
        return shift(rgb, 0.01f, -0.30f, -0.10f);
    }

    private static int shift(int rgb, float hueShift, float satShift, float brightShift) {
        Color c = new Color(rgb);
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float h = wrap01(hsb[0] + hueShift);
        float s = clamp01(hsb[1] + satShift);
        float b = clamp01(hsb[2] + brightShift);
        return Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float wrap01(float v) {
        float r = v % 1f;
        return r < 0 ? r + 1f : r;
    }
}
