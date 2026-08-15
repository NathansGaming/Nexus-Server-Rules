package com.nexusuniverse.realms.guidebook;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Deliberately tracks "has this player ever been given one," not "does this player currently
 * have one in their inventory" -- a one-time give means exactly that even if they later lose,
 * sell, or drop the book. This is NOT for new players specifically; it's for anyone on the server
 * who hasn't received one yet, checked on every join regardless of how long they've played.
 */
public class GuidebookManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> recipients = new HashSet<>();

    public GuidebookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guidebook-recipients.yml");
        load();
    }

    public boolean hasReceived(UUID playerId) {
        return recipients.contains(playerId);
    }

    public void markReceived(UUID playerId) {
        if (recipients.add(playerId)) {
            save();
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("recipients", recipients.stream().map(UUID::toString).toList());
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save guidebook-recipients.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        List<String> ids = data.getStringList("recipients");
        for (String id : ids) {
            try {
                recipients.add(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped a corrupt entry (\"" + id + "\") in guidebook-recipients.yml");
            }
        }
    }
}
