package com.nexusuniverse.chroma.color;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Holds the live Material -> Color mapping and is the single source of
 * truth for both "what can be converted" (FrameListener) and "what does
 * it look like" (ChromaItemFactory). Backed by plugins/NexusChroma/colors.yml:
 *   - If the file doesn't exist yet, it's generated from DefaultColors.
 *   - Every load after that reads ONLY the file -- DefaultColors is
 *     never consulted again once colors.yml exists.
 *   - add()/remove() edit both the in-memory map and the file together,
 *     so runtime changes via /nexuschroma survive a restart without a
 *     separate save step.
 */
public class ColorTable {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Material, Color> colors = new TreeMap<>(Comparator.comparing(Enum::name));

    public ColorTable(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "colors.yml");
    }

    public void load() {
        if (!file.exists()) {
            writeDefaults();
        }

        colors.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("colors");
        if (section == null) {
            plugin.getLogger().warning("[NexusChroma] colors.yml has no 'colors' section -- no blocks are convertible until it's fixed.");
            return;
        }

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            Material material;
            try {
                material = Material.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[NexusChroma] colors.yml has an unknown material '" + key + "' -- skipped.");
                continue;
            }
            Color color = parseHex(section.getString(key));
            if (color == null) {
                plugin.getLogger().warning("[NexusChroma] colors.yml has an invalid hex value for '" + key + "' -- skipped.");
                continue;
            }
            colors.put(material, color);
            loaded++;
        }
        plugin.getLogger().info("[NexusChroma] Loaded " + loaded + " block color mapping(s) from colors.yml.");
    }

    private void writeDefaults() {
        YamlConfiguration data = new YamlConfiguration();
        Map<Material, String> defaults = new TreeMap<>(Comparator.comparing(Enum::name));
        defaults.putAll(DefaultColors.build());
        for (Map.Entry<Material, String> entry : defaults.entrySet()) {
            data.set("colors." + entry.getKey().name(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(file);
            plugin.getLogger().info("[NexusChroma] First run -- wrote default colors.yml with " + defaults.size() + " entries.");
        } catch (IOException e) {
            plugin.getLogger().severe("[NexusChroma] Could not write default colors.yml: " + e.getMessage());
        }
    }

    public Color get(Material material) {
        return colors.get(material);
    }

    public boolean has(Material material) {
        return colors.containsKey(material);
    }

    public Map<Material, Color> all() {
        return colors;
    }

    /** Sets (or overwrites) one mapping, in memory and on disk. */
    public void set(Material material, Color color) {
        colors.put(material, color);
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set("colors." + material.name(), toHex(color));
        save(data);
    }

    /** Removes one mapping, in memory and on disk. Returns false if it wasn't present. */
    public boolean remove(Material material) {
        if (colors.remove(material) == null) {
            return false;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set("colors." + material.name(), null);
        save(data);
        return true;
    }

    private void save(YamlConfiguration data) {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[NexusChroma] Could not save colors.yml: " + e.getMessage());
        }
    }

    public static Color parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toHex(Color color) {
        return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
