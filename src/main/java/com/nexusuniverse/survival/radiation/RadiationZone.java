package com.nexusuniverse.survival.radiation;

import org.bukkit.Location;

/**
 * A simple axis-aligned cuboid region, defined by two corner locations in
 * the same world. No overlap detection between zones -- if two zones
 * overlap, a player just counts as "in radiation" (drain doesn't stack).
 */
public class RadiationZone {

    private final String name;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public RadiationZone(String name, Location a, Location b) {
        this.name = name;
        this.worldName = a.getWorld().getName();
        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String getName() {
        return name;
    }
}
