package com.nexusuniverse.chroma.map;

import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Fills the entire 128x128 map canvas with one flat color, once, then
 * does nothing on every later call -- this is a static square, not a
 * live-updating display.
 *
 * This is the actual trick the whole plugin depends on: item frames
 * render a FILLED_MAP edge-to-edge and flat against the frame, unlike
 * a block item (which renders as a small floating isometric cube). A
 * one-color map is what turns "frame holding a stone block" into
 * "frame that just looks like a flat stone-colored square."
 *
 * The requested color is snapped to Minecraft's built-in map color
 * palette via MapPalette.matchColor() -- maps can only ever draw from
 * that fixed palette, not arbitrary RGB, so the rendered shade can
 * differ slightly from the exact hex in colors.yml. In practice that
 * palette is itself built from real block colors, so for most of the
 * default table the snap should be small to unnoticeable.
 */
public class SolidColorMapRenderer extends MapRenderer {

    private final byte paletteColor;
    private boolean rendered = false;

    public SolidColorMapRenderer(Color color) {
        super(false); // not per-player contextual -- every viewer sees the same fill
        this.paletteColor = MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, paletteColor);
            }
        }
        rendered = true;
    }
}
