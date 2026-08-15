package com.nexus.serverrules.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Local name -> UUID cache, populated on every player join and
 * persisted to disk. Exists so /ban, /nexusrules clear, and
 * /nexusrules info never have to call the deprecated
 * Bukkit.getOfflinePlayer(String) - which, for a name that's never
 * been seen locally, can make a BLOCKING synchronous web request to
 * Mojang on the calling thread (the main thread, mid-command). Looking
 * up from this cache instead is instant with zero network calls, at
 * the cost of only knowing players who have actually joined this
 * server at least once - which covers every name a moderation command
 * would realistically ever need (you can't grief or chat-violate
 * without having joined first).
 */
public final class PlayerNameCache {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    public PlayerNameCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-cache.yml");
    }

    public void load() {
        nameToUuid.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                nameToUuid.put(key, UUID.fromString(section.getString(key)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Skipped malformed player-cache entry: " + key, ex);
            }
        }
    }

    /** Called from JoinListener on every login. Cheap no-op if the mapping hasn't changed. */
    public synchronized void record(String name, UUID uuid) {
        String key = name.toLowerCase(Locale.ROOT);
        if (uuid.equals(nameToUuid.get(key))) return;
        nameToUuid.put(key, uuid);
        save();
    }

    public UUID lookup(String name) {
        return nameToUuid.get(name.toLowerCase(Locale.ROOT));
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue().toString());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Failed to save player-cache.yml", e);
        }
    }
}
