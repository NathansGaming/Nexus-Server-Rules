package com.nexusuniverse.morality.config;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class NexusMoralityConfig {

    private final JavaPlugin plugin;

    public NexusMoralityConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        // copyDefaults(true) + saveConfig() merges in anything a later update adds to config.yml
        // without clobbering values staff have already tuned - same pattern as NexusSurvival.
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public boolean getBoolean(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public double getDouble(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    /**
     * Parses a list of Bukkit Material names from config, skipping (and
     * logging) any entry that isn't a real Material rather than failing
     * the whole load - a typo in one line of a staff-edited YAML
     * shouldn't take the rest of the list down with it.
     */
    public Set<Material> getMaterialSet(String path) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        List<String> names = plugin.getConfig().getStringList(path);
        for (String name : names) {
            try {
                materials.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "[NexusMorality] Unknown material '" + name
                        + "' under '" + path + "' in config.yml - skipped.", e);
            }
        }
        return materials;
    }

    public List<Material> getMaterialList(String path) {
        List<Material> materials = new ArrayList<>();
        List<String> names = plugin.getConfig().getStringList(path);
        for (String name : names) {
            try {
                materials.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "[NexusMorality] Unknown material '" + name
                        + "' under '" + path + "' in config.yml - skipped.", e);
            }
        }
        return materials;
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
