package com.nexusuniverse.realms.land;

import java.util.Objects;

public final class ChunkKey {

    private final String world;
    private final int chunkX;
    private final int chunkZ;

    public ChunkKey(String world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String world() {
        return world;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkKey other)) return false;
        return chunkX == other.chunkX && chunkZ == other.chunkZ && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public static ChunkKey parse(String raw) {
        String[] parts = raw.split(":");
        return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
