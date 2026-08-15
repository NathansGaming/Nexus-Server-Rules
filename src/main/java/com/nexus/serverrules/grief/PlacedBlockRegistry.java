package com.nexus.serverrules.grief;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory only (deliberately not persisted to disk - this is a
 * same-session heuristic, not a claim/ownership system). Tracks which
 * player placed a given block so GriefListener can tell "breaking
 * natural terrain / your own build" apart from "breaking a block
 * someone else placed." Bounded size with simple eviction so long
 * uptime on a busy server doesn't grow this unbounded.
 */
public final class PlacedBlockRegistry {

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Location loc) {
            return new BlockKey(loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }

    private final Map<BlockKey, UUID> placedBy;

    public PlacedBlockRegistry(int capacity) {
        this.placedBy = new LinkedHashMap<>(1024, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockKey, UUID> eldest) {
                return size() > capacity;
            }
        };
    }

    public void recordPlaced(BlockKey key, UUID player) {
        placedBy.put(key, player);
    }

    public UUID ownerOf(BlockKey key) {
        return placedBy.get(key);
    }

    public void forget(BlockKey key) {
        placedBy.remove(key);
    }
}
