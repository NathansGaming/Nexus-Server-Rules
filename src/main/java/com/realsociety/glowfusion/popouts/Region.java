package com.realsociety.glowfusion.popouts;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * An axis-aligned block region, normalized so min/max are always in the
 * right order regardless of which corner the player selected first.
 */
public final class Region {

    private final World world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public Region(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Region of(Location pos1, Location pos2) {
        return new Region(pos1.getWorld(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
    }

    public World getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long blockCount() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** Whether this region occupies the exact same coordinates as another (world included). */
    public boolean sameBoundsAs(Region other) {
        if (other == null) {
            return false;
        }
        return world.getName().equals(other.world.getName())
                && minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }

    public String describeSize() {
        return sizeX() + "x" + sizeY() + "x" + sizeZ() + " (" + blockCount() + " blocks)";
    }
}
