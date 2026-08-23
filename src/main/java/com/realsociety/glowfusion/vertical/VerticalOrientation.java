package com.realsociety.glowfusion.vertical;

/**
 * FLAT is a normal, untouched vanilla slab (lying flat, real collision).
 * The other four are the "standing up" states, facing the compass
 * direction the panel is flush against - e.g. NORTH means the thin panel
 * sits against the block's north face.
 */
public enum VerticalOrientation {
    FLAT, NORTH, EAST, SOUTH, WEST;

    public VerticalOrientation next() {
        VerticalOrientation[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
