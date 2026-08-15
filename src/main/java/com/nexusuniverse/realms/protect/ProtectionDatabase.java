package com.nexusuniverse.realms.protect;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Everything the protection/rollback system reads or writes goes through here, and ONLY through
 * here -- nothing else in this package touches java.sql directly. That matters for one specific
 * reason: every block break, block place, container transaction, and death on the server writes
 * a row. On a busy server that's a lot of writes, and a synchronous write on the main thread for
 * every single block break would be a real, direct source of lag -- the exact opposite of what a
 * protection plugin should cost you. So writes don't happen inline: log() just drops the entry on
 * a queue and returns immediately; one dedicated background thread drains that queue and does the
 * actual JDBC work, batching where it can. Reads (lookup/rollback queries) run on whatever thread
 * calls them -- callers are responsible for not calling those directly from the main thread on
 * anything that could be slow (RollbackManager and the lookup command already do this correctly
 * by running on Bukkit's async scheduler).
 *
 * SQLite specifically (not MySQL/Postgres) because this needs to work out of the box on a normal
 * Paper server with zero extra setup -- no separate database server to install, just one file in
 * the plugin's data folder. It comfortably handles the write volume a single Minecraft server
 * produces; it would NOT be the right choice for a networked, multi-server setup logging into one
 * shared database, but that's not what this is.
 */
public class ProtectionDatabase {

    private final JavaPlugin plugin;
    private Connection connection;
    private final LinkedBlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread writerThread;

    public ProtectionDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "protect.db");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement pragma = connection.createStatement()) {
                // WAL mode lets reads (lookups) and the single writer thread's writes happen
                // concurrently without blocking each other -- default SQLite locking would
                // otherwise make a lookup query wait behind whatever block-break write queue has
                // backed up, which defeats half the point of writing async in the first place
                pragma.execute("PRAGMA journal_mode=WAL;");
                pragma.execute("PRAGMA synchronous=NORMAL;");
            }
            createSchema();
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "NexusRealms: couldn't open protect.db -- "
                    + "the protection/rollback system will not function this session.", e);
            return;
        }

        this.writerThread = new Thread(this::writerLoop, "NexusRealms-Protect-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void stop() {
        running = false;
        if (writerThread != null) writerThread.interrupt();
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: error closing protect.db", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS block_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    old_block_data TEXT,
                    new_block_data TEXT,
                    rolled_back INTEGER NOT NULL DEFAULT 0
                )
            """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_block_log_location ON block_log(world, x, y, z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_block_log_time ON block_log(timestamp)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_block_log_player ON block_log(player_uuid)");

            statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    world TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER,
                    action TEXT NOT NULL,
                    material TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    item_data TEXT,
                    restored INTEGER NOT NULL DEFAULT 0
                )
            """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inv_log_location ON inventory_log(world, x, y, z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inv_log_time ON inventory_log(timestamp)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inv_log_player ON inventory_log(player_uuid)");
        }
    }

    private void writerLoop() {
        while (running) {
            try {
                Runnable task = writeQueue.take();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: protection log write failed", e);
            }
        }
        // drain whatever was still queued at shutdown so a burst of activity right before a
        // restart isn't silently lost
        Runnable remaining;
        while ((remaining = writeQueue.poll()) != null) {
            try {
                remaining.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: protection log write failed during shutdown drain", e);
            }
        }
    }

    /** Queues a write -- returns immediately, never blocks the calling thread on disk I/O. */
    public void queueWrite(Runnable task) {
        writeQueue.add(task);
    }

    public void logBlockChange(long timestamp, String playerUuid, String playerName, String world,
                                int x, int y, int z, String action, String oldBlockData, String newBlockData) {
        queueWrite(() -> {
            String sql = "INSERT INTO block_log (timestamp, player_uuid, player_name, world, x, y, z, "
                    + "action, old_block_data, new_block_data) VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, timestamp);
                statement.setString(2, playerUuid);
                statement.setString(3, playerName);
                statement.setString(4, world);
                statement.setInt(5, x);
                statement.setInt(6, y);
                statement.setInt(7, z);
                statement.setString(8, action);
                statement.setString(9, oldBlockData);
                statement.setString(10, newBlockData);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to log a block change", e);
            }
        });
    }

    public void logInventoryChange(long timestamp, String playerUuid, String playerName, String world,
                                    Integer x, Integer y, Integer z, String action, String material,
                                    int amount, String itemData) {
        queueWrite(() -> {
            String sql = "INSERT INTO inventory_log (timestamp, player_uuid, player_name, world, x, y, z, "
                    + "action, material, amount, item_data) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, timestamp);
                statement.setString(2, playerUuid);
                statement.setString(3, playerName);
                statement.setString(4, world);
                if (x != null) statement.setInt(5, x); else statement.setNull(5, java.sql.Types.INTEGER);
                if (y != null) statement.setInt(6, y); else statement.setNull(6, java.sql.Types.INTEGER);
                if (z != null) statement.setInt(7, z); else statement.setNull(7, java.sql.Types.INTEGER);
                statement.setString(8, action);
                statement.setString(9, material);
                statement.setInt(10, amount);
                statement.setString(11, itemData);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to log an inventory change", e);
            }
        });
    }

    /** A ResultSet-row mapper that's allowed to throw SQLException, since ResultSet#getX methods all do. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Runs a read query SYNCHRONOUSLY on whatever thread calls this -- callers must not call this
     * from the main server thread for anything that could scan a lot of rows. RollbackManager and
     * the lookup/inspect commands are responsible for dispatching to Bukkit's async scheduler
     * before calling in here.
     */
    public <T> List<T> query(String sql, Consumer<PreparedStatement> bind, RowMapper<T> mapper) {
        List<T> results = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind.accept(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: protection query failed", e);
        }
        return results;
    }

    /** Runs an arbitrary update (e.g. marking rows rolled_back) synchronously on the calling thread. */
    public int update(String sql, Consumer<PreparedStatement> bind) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind.accept(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: protection update failed", e);
            return 0;
        }
    }
}
