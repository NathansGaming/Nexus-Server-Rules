package com.realsociety.glowfusion.popouts;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * A named popout: a fixed region plus two complete block snapshots of it -
 * one for the "off"/stowed look and one for the "on"/deployed look. Every
 * block in the region is recorded in both snapshots (including air), so
 * applying either one is a total, exact overwrite rather than a diff -
 * there's no ambiguity about what should disappear when switching states.
 */
public final class PopoutDefinition {

    public enum State { ON, OFF }

    private final String name;
    private Region region;
    private List<String> offEntries;
    private List<String> onEntries;

    public PopoutDefinition(String name, Region region, List<String> offEntries, List<String> onEntries) {
        this.name = name;
        this.region = region;
        this.offEntries = offEntries;
        this.onEntries = onEntries;
    }

    public String getName() { return name; }
    public Region getRegion() { return region; }
    public boolean hasOff() { return offEntries != null; }
    public boolean hasOn() { return onEntries != null; }
    public boolean isComplete() { return hasOff() && hasOn(); }

    public List<String> getOffEntries() { return offEntries; }
    public List<String> getOnEntries() { return onEntries; }

    public void setRegion(Region region) { this.region = region; }
    public void setOffEntries(List<String> entries) { this.offEntries = entries; }
    public void setOnEntries(List<String> entries) { this.onEntries = entries; }

    /** Captures every block currently in {@code region} as one of the two states. */
    public static List<String> capture(Region region) {
        List<String> entries = new ArrayList<>();
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block block = region.getWorld().getBlockAt(x, y, z);
                    String data = block.getBlockData().getAsString();
                    int dx = x - region.getMinX();
                    int dy = y - region.getMinY();
                    int dz = z - region.getMinZ();
                    entries.add(dx + ":" + dy + ":" + dz + "|" + data);
                }
            }
        }
        return entries;
    }

    /** Overwrites every block in the region with the given state's snapshot. */
    public void apply(State state) {
        List<String> entries = state == State.ON ? onEntries : offEntries;
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            int bar = entry.indexOf('|');
            if (bar < 0) {
                continue;
            }
            String[] coords = entry.substring(0, bar).split(":");
            if (coords.length != 3) {
                continue;
            }
            try {
                int dx = Integer.parseInt(coords[0]);
                int dy = Integer.parseInt(coords[1]);
                int dz = Integer.parseInt(coords[2]);
                BlockData data = Bukkit.createBlockData(entry.substring(bar + 1));
                Block block = region.getWorld().getBlockAt(
                        region.getMinX() + dx, region.getMinY() + dy, region.getMinZ() + dz);
                block.setBlockData(data, false);
            } catch (IllegalArgumentException ignored) {
                // Covers both NumberFormatException (bad coordinates) and an
                // unrecognized block data string - skip that one entry rather
                // than aborting the whole swap.
            }
        }
    }
}
