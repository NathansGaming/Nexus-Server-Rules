package com.nexus.serverrules.storage;

import com.nexus.serverrules.detection.ViolationResult;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Holds flagged incidents for the staff review GUI/command, and
 * independently appends every one to violations-log.txt on disk
 * (same "log even if in-game systems fail" pattern used by NexusAdmin's
 * audit log) so there's always a record even if the GUI queue is
 * cleared or the server restarts.
 */
public final class ReviewQueue {

    private final JavaPlugin plugin;
    private final List<ViolationResult> incidents = new CopyOnWriteArrayList<>();
    private final java.io.File logFile;

    public ReviewQueue(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new java.io.File(plugin.getDataFolder(), "violations-log.txt");
    }

    public void add(ViolationResult violation) {
        incidents.add(violation);
        appendToDisk(violation);
    }

    public List<ViolationResult> all() {
        return Collections.unmodifiableList(incidents);
    }

    /** Most recent unresolved-looking incidents per player, newest first, for the GUI. */
    public Map<UUID, ViolationResult> latestByPlayer() {
        Map<UUID, ViolationResult> latest = new LinkedHashMap<>();
        for (int i = incidents.size() - 1; i >= 0; i--) {
            ViolationResult v = incidents.get(i);
            latest.putIfAbsent(v.playerId(), v);
        }
        return latest;
    }

    /** Called after staff clear a player - removes their incidents from the live queue (history stays in the log file). */
    public void clearForPlayer(UUID playerId) {
        incidents.removeIf(v -> v.playerId().equals(playerId));
    }

    private void appendToDisk(ViolationResult v) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(String.format("[%s] %s (%s) - %s - category=%s confidence=%s root=\"%s\" - message=\"%s\"%n",
                        DateTimeFormatter.ISO_INSTANT.format(v.timestamp()),
                        v.playerName(), v.playerId(),
                        "AUTO-RESTRICTED",
                        v.category(), v.confidence(), v.matchedRoot(), v.rawMessage()));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NexusServerRules] FAILED to write violations-log.txt", e);
        }
    }
}
