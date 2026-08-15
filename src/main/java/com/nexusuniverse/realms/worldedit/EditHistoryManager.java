package com.nexusuniverse.realms.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshotted in memory only (not persisted -- undo history isn't meant to survive a restart,
 * same reasoning as SelectionManager). Bounded per-player stack depth (config-driven, small by
 * default) since a snapshot holds one BlockData string per affected block -- fine at the scale a
 * personal claim's volume cap keeps things to, but not something to let grow unbounded for
 * operator-tier edits over a much larger area.
 */
public class EditHistoryManager {

    public record Snapshot(String world, int x, int y, int z, String blockData) {
    }

    private final Map<UUID, Deque<List<Snapshot>>> historyByPlayer = new HashMap<>();
    private final int maxDepth;

    public EditHistoryManager(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public void push(UUID playerId, List<Snapshot> snapshot) {
        Deque<List<Snapshot>> stack = historyByPlayer.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        stack.push(snapshot);
        while (stack.size() > maxDepth) {
            stack.removeLast();
        }
    }

    /** @return the number of blocks restored, or -1 if there was nothing to undo. */
    public int undo(UUID playerId) {
        Deque<List<Snapshot>> stack = historyByPlayer.get(playerId);
        if (stack == null || stack.isEmpty()) return -1;

        List<Snapshot> snapshot = stack.pop();
        int restored = 0;
        for (Snapshot entry : snapshot) {
            World world = Bukkit.getWorld(entry.world());
            if (world == null) continue;
            try {
                Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
                block.setBlockData(Bukkit.createBlockData(entry.blockData()), false);
                restored++;
            } catch (IllegalArgumentException ignored) {
                // a stored BlockData string that no longer parses -- skip that one block rather than fail the whole undo
            }
        }
        return restored;
    }

    /** Convenience for building a snapshot list while iterating a region -- see EditExecutor. */
    public static List<Snapshot> newSnapshotList() {
        return new ArrayList<>();
    }
}
