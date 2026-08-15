package com.nexusuniverse.survival.config;

import org.bukkit.plugin.java.JavaPlugin;

public class NexusSurvivalConfig {

    private final JavaPlugin plugin;

    public NexusSurvivalConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        // saveDefaultConfig() only writes config.yml the very first time this plugin is
        // installed -- copyDefaults(true) + saveConfig() merges in anything a later update adds
        // to an already-existing config.yml, instead of it silently never showing up.
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public double getDouble(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    /**
     * Master dial for disease rarity: every infection roll in the plugin
     * (biome/block/mob contact, person-to-person contagion, plague sites,
     * dirty-armor hygiene infection, raw water, and Plague Carrier bite/
     * proximity) is multiplied by this on top of its own individual
     * chance. 1.0 = no change from the rates below; lower = rarer overall
     * without having to retune every individual number by hand.
     */
    public double diseaseChanceMultiplier() {
        return getDouble("disease.chance-multiplier", 0.35);
    }

    public int getInt(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    public String getString(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
