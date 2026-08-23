package com.realsociety.glowfusion.vertical;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists which blocks are currently "standing up": the original block
 * data to restore when flipped back to FLAT, and the current facing.
 * Needed because the real block becomes AIR while standing (see
 * VerticalSlabListener), so nothing about its original material survives
 * in the world itself.
 */
public final class VerticalSlabStore {

    public static final class Entry {
        public final String originalBlockData;
        public VerticalOrientation orientation;

        public Entry(String originalBlockData, VerticalOrientation orientation) {
            this.originalBlockData = originalBlockData;
            this.orientation = orientation;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public VerticalSlabStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vertical-slabs.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String block = yaml.getString(key + ".block");
            String orientationName = yaml.getString(key + ".orientation");
            if (block == null || orientationName == null) {
                continue;
            }
            try {
                entries.put(key, new Entry(block, VerticalOrientation.valueOf(orientationName)));
            } catch (IllegalArgumentException ignored) {
                // Corrupt/unknown orientation string - skip this entry.
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            yaml.set(e.getKey() + ".block", e.getValue().originalBlockData);
            yaml.set(e.getKey() + ".orientation", e.getValue().orientation.name());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save vertical-slabs.yml", e);
        }
    }

    public Entry get(Location loc) {
        return entries.get(key(loc));
    }

    public boolean has(Location loc) {
        return entries.containsKey(key(loc));
    }

    public void put(Location loc, Entry entry) {
        entries.put(key(loc), entry);
        save();
    }

    public void remove(Location loc) {
        if (entries.remove(key(loc)) != null) {
            save();
        }
    }

    public static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }
}
