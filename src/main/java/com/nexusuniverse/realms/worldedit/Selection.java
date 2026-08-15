package com.nexusuniverse.realms.worldedit;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Matches WorldEdit's own convention: left-click with the wand sets pos1, right-click sets pos2.
 * Both corners can be set in any order/any two opposite corners -- min()/max() below sort them
 * into an actual axis-aligned bounding box regardless of which corner is which.
 */
public class Selection {

    private Location pos1;
    private Location pos2;

    public void setPos1(Location location) {
        this.pos1 = location.clone();
    }

    public void setPos2(Location location) {
        this.pos2 = location.clone();
    }

    public Location pos1() {
        return pos1;
    }

    public Location pos2() {
        return pos2;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }

    public World world() {
        return pos1 != null ? pos1.getWorld() : null;
    }

    public int minX() {
        return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    public int maxX() {
        return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    public int minY() {
        return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    public int maxY() {
        return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    public int minZ() {
        return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int maxZ() {
        return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public long volume() {
        return (long) (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
    }
}
