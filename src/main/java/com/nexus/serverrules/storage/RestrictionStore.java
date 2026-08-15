package com.nexus.serverrules.storage;

import com.nexus.serverrules.detection.ViolationCategory;
import com.nexus.serverrules.punishment.Restriction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists restrictions to restrictions.yml so state survives a server
 * restart, not just a player relogin (an in-memory-only map would lose
 * everything on crash/restart, which would quietly undo every pending
 * restriction - not acceptable for something staff are relying on).
 *
 * Every mutation is saved to disk immediately rather than batched,
 * since a lost write here means a flagged player silently walks free.
 */
public final class RestrictionStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Restriction> active = new LinkedHashMap<>();

    public RestrictionStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "restrictions.yml");
    }

    public void load() {
        active.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("active");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                UUID id = UUID.fromString(key);
                Restriction r = new Restriction(
                        id,
                        sec.getString("playerName", "unknown"),
                        ViolationCategory.valueOf(sec.getString("category", "HARASSMENT")),
                        sec.getString("reason", ""),
                        sec.getString("triggeringMessage", ""),
                        Instant.parse(sec.getString("triggeredAt", Instant.now().toString())),
                        sec.getString("previousGameMode", "SURVIVAL")
                );
                r.active = sec.getBoolean("active", true);
                if (r.active) {
                    active.put(id, r);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Skipped malformed restriction entry: " + key, ex);
            }
        }
        plugin.getLogger().info("[NexusServerRules] Loaded " + active.size() + " active restriction(s) from disk");
    }

    public synchronized void put(Restriction r) {
        active.put(r.playerId, r);
        save();
    }

    public synchronized void remove(UUID playerId) {
        active.remove(playerId);
        save();
    }

    public Restriction get(UUID playerId) {
        return active.get(playerId);
    }

    public boolean isRestricted(UUID playerId) {
        return active.containsKey(playerId);
    }

    public Map<UUID, Restriction> allActive() {
        return active;
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Restriction> entry : active.entrySet()) {
            Restriction r = entry.getValue();
            String path = "active." + entry.getKey();
            yaml.set(path + ".playerName", r.playerName);
            yaml.set(path + ".category", r.category.name());
            yaml.set(path + ".reason", r.reason);
            yaml.set(path + ".triggeringMessage", r.triggeringMessage);
            yaml.set(path + ".triggeredAt", r.triggeredAt.toString());
            yaml.set(path + ".previousGameMode", r.previousGameMode);
            yaml.set(path + ".active", r.active);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NexusServerRules] FAILED to save restrictions.yml - "
                    + "a restriction may not survive a restart!", e);
        }
    }
}
