package com.nexusuniverse.morality.karma;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists per-player karma to karma.yml so it survives a restart, same
 * "write on every mutation, not batched" approach RestrictionStore uses
 * in NexusServerRules - karma feeding a future reputation system is
 * exactly the kind of thing that shouldn't silently reset because the
 * server happened to restart between encounters.
 */
public final class KarmaStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> karma = new LinkedHashMap<>();

    public KarmaStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "karma.yml");
    }

    public void load() {
        karma.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("karma");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                karma.put(id, section.getInt(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "[NexusMorality] Skipped malformed karma entry: " + key, e);
            }
        }
        plugin.getLogger().info("[NexusMorality] Loaded karma for " + karma.size() + " player(s) from disk");
    }

    public int get(UUID playerId) {
        return karma.getOrDefault(playerId, 0);
    }

    /** Applies delta (positive or negative) and returns the new total. */
    public synchronized int adjust(UUID playerId, int delta) {
        int updated = get(playerId) + delta;
        karma.put(playerId, updated);
        save();
        return updated;
    }

    public Map<UUID, Integer> all() {
        return karma;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : karma.entrySet()) {
            yaml.set("karma." + entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NexusMorality] FAILED to save karma.yml - "
                    + "a karma change may not survive a restart!", e);
        }
    }
}
