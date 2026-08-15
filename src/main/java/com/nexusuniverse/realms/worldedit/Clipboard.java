package com.nexusuniverse.realms.worldedit;

import java.util.List;

/**
 * A copied region, stored as block offsets relative to wherever the player was standing at copy
 * time -- not relative to the selection's own corner. That's what lets /redit paste drop the same
 * shape down at the player's CURRENT location, matching real WorldEdit's own copy/paste
 * convention, instead of only being able to paste back at the exact coordinates it was copied
 * from. World is deliberately not stored here either, for the same reason: a clipboard is just a
 * shape, so it can be copied in one world/claim and pasted in another.
 */
public class Clipboard {

    public record Entry(int dx, int dy, int dz, String blockData) {
    }

    private final List<Entry> entries;
    private final int minDx;
    private final int maxDx;
    private final int minDz;
    private final int maxDz;

    public Clipboard(List<Entry> entries, int minDx, int maxDx, int minDz, int maxDz) {
        this.entries = entries;
        this.minDx = minDx;
        this.maxDx = maxDx;
        this.minDz = minDz;
        this.maxDz = maxDz;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Horizontal footprint of the clipboard relative to its anchor -- used to check the PASTE destination, not the original copy site. */
    public int minDx() {
        return minDx;
    }

    public int maxDx() {
        return maxDx;
    }

    public int minDz() {
        return minDz;
    }

    public int maxDz() {
        return maxDz;
    }

    public int size() {
        return entries.size();
    }
}
