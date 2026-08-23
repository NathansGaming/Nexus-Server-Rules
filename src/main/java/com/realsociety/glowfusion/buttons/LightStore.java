package com.realsociety.glowfusion.buttons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists the mapping of "button block location" -> "light block location"
 * that {@link GlowingButtonListener} places to make powered buttons glow.
 *
 * <p>Regular (non-tile-entity) blocks like {@code minecraft:light} can't hold
 * a PersistentDataContainer, so this mapping is tracked in a small flat file
 * instead, loaded on enable and saved on disable (and after every change).</p>
 */
public final class LightStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, String> buttonToLight = new LinkedHashMap<>();

    public LightStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lights.yml");
    }

    public void load() {
        buttonToLight.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String value = yaml.getString(key);
            if (value != null) {
                buttonToLight.put(key, value);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> entry : buttonToLight.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save lights.yml", e);
        }
    }

    public void put(Location button, Location light) {
        buttonToLight.put(serialize(button), serialize(light));
        save();
    }

    public void remove(Location button) {
        if (buttonToLight.remove(serialize(button)) != null) {
            save();
        }
    }

    public Location getLight(Location button) {
        String value = buttonToLight.get(serialize(button));
        return value == null ? null : deserialize(value);
    }

    public boolean hasLight(Location button) {
        return buttonToLight.containsKey(serialize(button));
    }

    /** Snapshot of every currently-tracked button location. */
    public Iterable<Location> allButtonLocations() {
        return buttonToLight.keySet().stream()
                .map(LightStore::deserialize)
                .filter(loc -> loc != null)
                .toList();
    }

    private static String serialize(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private static Location deserialize(String s) {
        String[] parts = s.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
