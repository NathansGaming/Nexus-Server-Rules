package com.nexusuniverse.dreams.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

public class NexusDreamsConfig {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public NexusDreamsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
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

    public String getString(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    /** Picks one random line from flavor.<tier>.<bucket>, falling back to flavor.<tier>.default, then a generic line if even that's missing/empty. */
    public String randomFlavorLine(String tier, String bucket) {
        List<String> lines = plugin.getConfig().getStringList("flavor." + tier + "." + bucket);
        if (lines.isEmpty()) {
            lines = plugin.getConfig().getStringList("flavor." + tier + ".default");
        }
        if (lines.isEmpty()) {
            return "You dream, but nothing about it stays with you.";
        }
        return lines.get(random.nextInt(lines.size()));
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
