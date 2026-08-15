package com.nexusuniverse.realms.protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The actual undo button. rollback() finds every not-yet-rolled-back block_log row matching the
 * filter, most recent first, and restores each one to its old_block_data -- a BREAK gets the
 * original block put back exactly as it was (right orientation, right block-entity type, not
 * just the right Material), a PLACE gets removed back to whatever was there before it. Rows are
 * marked rolled_back=1 as they're restored, so re-running the same or a wider rollback later
 * doesn't double-apply or fight itself.
 *
 * DB reads happen off the main thread (this can be a genuinely large scan on a busy log); the
 * actual block edits happen back on the main thread, since touching the world off-thread isn't
 * safe. lookup() (read-only, for /nexusrealms protect lookup) follows the same async-read
 * pattern without the second main-thread hop, since it doesn't touch any blocks.
 */
public class RollbackManager {

    private final JavaPlugin plugin;
    private final ProtectionDatabase db;

    public RollbackManager(JavaPlugin plugin, ProtectionDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    public record RollbackResult(int restored, int matched) {
    }

    public void rollback(RollbackFilter filter, Consumer<RollbackResult> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BlockLogRow> rows = findMatching(filter);

            Bukkit.getScheduler().runTask(plugin, () -> {
                int restored = 0;
                for (BlockLogRow row : rows) {
                    if (applyRestore(row)) restored++;
                }
                callback.accept(new RollbackResult(restored, rows.size()));
            });
        });
    }

    /** Read-only version of the same lookup, for /nexusrealms protect lookup -- no block edits, just reports what would match. */
    public void lookup(RollbackFilter filter, Consumer<List<BlockLogRow>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BlockLogRow> rows = findMatching(filter);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(rows));
        });
    }

    private boolean applyRestore(BlockLogRow row) {
        World world = Bukkit.getWorld(row.world());
        if (world == null) return false;

        try {
            Block block = world.getBlockAt(row.x(), row.y(), row.z());
            block.setBlockData(Bukkit.createBlockData(row.oldBlockData()), false);
            markRolledBack(row.id());
            return true;
        } catch (IllegalArgumentException e) {
            // a stored BlockData string that no longer parses (e.g. a block type removed in a
            // later Minecraft version) -- skip it rather than fail the whole rollback batch
            plugin.getLogger().warning("NexusRealms: couldn't restore logged block \"" + row.oldBlockData()
                    + "\" at " + row.world() + " " + row.x() + "," + row.y() + "," + row.z()
                    + " -- skipping this one entry.");
            return false;
        }
    }

    private void markRolledBack(long id) {
        db.update("UPDATE block_log SET rolled_back = 1 WHERE id = ?", statement -> {
            try {
                statement.setLong(1, id);
            } catch (Exception ignored) {
            }
        });
    }

    private List<BlockLogRow> findMatching(RollbackFilter filter) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, timestamp, player_name, world, x, y, z, action, old_block_data, new_block_data "
                        + "FROM block_log WHERE rolled_back = 0 AND timestamp >= ?");
        List<Object> params = new ArrayList<>();
        params.add(filter.sinceMillis());

        if (filter.playerName() != null) {
            sql.append(" AND player_name = ?");
            params.add(filter.playerName());
        }

        Location center = filter.center();
        if (center != null && center.getWorld() != null) {
            sql.append(" AND world = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?");
            double r = filter.radius();
            params.add(center.getWorld().getName());
            params.add((int) Math.floor(center.getX() - r));
            params.add((int) Math.ceil(center.getX() + r));
            params.add((int) Math.floor(center.getY() - r));
            params.add((int) Math.ceil(center.getY() + r));
            params.add((int) Math.floor(center.getZ() - r));
            params.add((int) Math.ceil(center.getZ() + r));
        }

        sql.append(" ORDER BY timestamp DESC LIMIT 5000"); // a hard ceiling so one command can't accidentally try to touch an unbounded number of blocks in a single pass

        return db.query(sql.toString(), statement -> {
            try {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
            } catch (Exception ignored) {
            }
        }, rs -> new BlockLogRow(
                rs.getLong("id"),
                rs.getLong("timestamp"),
                rs.getString("player_name"),
                rs.getString("world"),
                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                rs.getString("action"),
                rs.getString("old_block_data"),
                rs.getString("new_block_data")
        ));
    }
}
