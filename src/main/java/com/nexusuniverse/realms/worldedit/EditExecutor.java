package com.nexusuniverse.realms.worldedit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EditExecutor {

    private final EditHistoryManager history;

    public EditExecutor(EditHistoryManager history) {
        this.history = history;
    }

    public int set(Player player, Selection selection, Material material) {
        return apply(player, selection, (world, x, y, z) -> true, material);
    }

    public int replace(Player player, Selection selection, Material from, Material to) {
        return apply(player, selection, (world, x, y, z) -> world.getBlockAt(x, y, z).getType() == from, to);
    }

    /** The outer shell of the selection's bounding box on the X/Z sides, full height -- a box's four walls. */
    public int walls(Player player, Selection selection, Material material) {
        return apply(player, selection, (world, x, y, z) ->
                x == selection.minX() || x == selection.maxX() || z == selection.minZ() || z == selection.maxZ(), material);
    }

    /** Every face of the bounding box -- walls plus the top and bottom. */
    public int outline(Player player, Selection selection, Material material) {
        return apply(player, selection, (world, x, y, z) ->
                x == selection.minX() || x == selection.maxX()
                        || z == selection.minZ() || z == selection.maxZ()
                        || y == selection.minY() || y == selection.maxY(), material);
    }

    /**
     * Reads every block in the selection into a Clipboard, stored as offsets relative to the
     * player's OWN current position (not the selection's corner) -- see Clipboard's javadoc for
     * why. Read-only against the world, so it doesn't touch undo history.
     */
    public int copy(Player player, Selection selection, ClipboardManager clipboards) {
        World world = selection.world();
        Location anchor = player.getLocation();
        int anchorX = anchor.getBlockX();
        int anchorY = anchor.getBlockY();
        int anchorZ = anchor.getBlockZ();

        List<Clipboard.Entry> entries = new ArrayList<>();
        int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;

        for (int x = selection.minX(); x <= selection.maxX(); x++) {
            for (int y = selection.minY(); y <= selection.maxY(); y++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    int dx = x - anchorX;
                    int dy = y - anchorY;
                    int dz = z - anchorZ;
                    entries.add(new Clipboard.Entry(dx, dy, dz, block.getBlockData().getAsString()));
                    minDx = Math.min(minDx, dx);
                    maxDx = Math.max(maxDx, dx);
                    minDz = Math.min(minDz, dz);
                    maxDz = Math.max(maxDz, dz);
                }
            }
        }

        clipboards.set(player.getUniqueId(), new Clipboard(entries, minDx, maxDx, minDz, maxDz));
        return entries.size();
    }

    /**
     * Stamps a previously copied Clipboard down with its anchor at {@code origin} -- normally the
     * player's current location. Goes through the same undo history as set/replace/walls/outline,
     * so a bad paste is one /redit undo away from gone, same as any other operation.
     */
    public int paste(Player player, Clipboard clipboard, Location origin) {
        World world = origin.getWorld();
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();

        List<EditHistoryManager.Snapshot> snapshot = EditHistoryManager.newSnapshotList();
        int changed = 0;

        for (Clipboard.Entry entry : clipboard.entries()) {
            int x = originX + entry.dx();
            int y = originY + entry.dy();
            int z = originZ + entry.dz();

            Block block = world.getBlockAt(x, y, z);
            snapshot.add(new EditHistoryManager.Snapshot(world.getName(), x, y, z, block.getBlockData().getAsString()));
            try {
                block.setBlockData(Bukkit.createBlockData(entry.blockData()), false);
                changed++;
            } catch (IllegalArgumentException ignored) {
                // a stored BlockData string that no longer parses on this server version -- skip that one block rather than fail the whole paste
            }
        }

        if (changed > 0) {
            history.push(player.getUniqueId(), snapshot);
        }
        return changed;
    }

    @FunctionalInterface
    private interface BlockFilter {
        boolean matches(World world, int x, int y, int z);
    }

    private int apply(Player player, Selection selection, BlockFilter filter, Material material) {
        World world = selection.world();
        List<EditHistoryManager.Snapshot> snapshot = EditHistoryManager.newSnapshotList();
        int changed = 0;

        for (int x = selection.minX(); x <= selection.maxX(); x++) {
            for (int y = selection.minY(); y <= selection.maxY(); y++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    if (!filter.matches(world, x, y, z)) continue;

                    Block block = world.getBlockAt(x, y, z);
                    snapshot.add(new EditHistoryManager.Snapshot(world.getName(), x, y, z, block.getBlockData().getAsString()));
                    block.setType(material, false);
                    changed++;
                }
            }
        }

        if (changed > 0) {
            history.push(player.getUniqueId(), snapshot);
        }
        return changed;
    }

    public int undo(Player player) {
        return history.undo(player.getUniqueId());
    }
}
