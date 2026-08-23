package com.realsociety.glowfusion.popouts;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists popout definitions (one YAML file per name, under
 * {@code plugins/GlowFusion/popouts/}) and the lever-location -> popout-name
 * bindings (a single {@code bindings.yml}).
 */
public final class PopoutStore {

    private final JavaPlugin plugin;
    private final File popoutsDir;
    private final File bindingsFile;

    private final Map<String, PopoutDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> bindings = new LinkedHashMap<>(); // leverLocKey -> popout name

    public PopoutStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.popoutsDir = new File(plugin.getDataFolder(), "popouts");
        this.bindingsFile = new File(plugin.getDataFolder(), "bindings.yml");
    }

    public void load() {
        definitions.clear();
        bindings.clear();

        if (popoutsDir.isDirectory()) {
            File[] files = popoutsDir.listFiles((dir, fileName) -> fileName.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    PopoutDefinition def = loadDefinition(file);
                    if (def != null) {
                        definitions.put(def.getName().toLowerCase(), def);
                    }
                }
            }
        }

        if (bindingsFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bindingsFile);
            for (String key : yaml.getKeys(false)) {
                String value = yaml.getString(key);
                if (value != null) {
                    bindings.put(key, value);
                }
            }
        }
    }

    private PopoutDefinition loadDefinition(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Skipping popout '" + file.getName()
                    + "' - its world '" + worldName + "' isn't loaded.");
            return null;
        }
        String name = yaml.getString("name", file.getName().replace(".yml", ""));
        Region region = new Region(world,
                yaml.getInt("min-x"), yaml.getInt("min-y"), yaml.getInt("min-z"),
                yaml.getInt("max-x"), yaml.getInt("max-y"), yaml.getInt("max-z"));
        List<String> off = yaml.contains("off") ? yaml.getStringList("off") : null;
        List<String> on = yaml.contains("on") ? yaml.getStringList("on") : null;
        return new PopoutDefinition(name, region, off, on);
    }

    public void saveDefinition(PopoutDefinition def) {
        definitions.put(def.getName().toLowerCase(), def);
        YamlConfiguration yaml = new YamlConfiguration();
        Region region = def.getRegion();
        yaml.set("name", def.getName());
        yaml.set("world", region.getWorld().getName());
        yaml.set("min-x", region.getMinX());
        yaml.set("min-y", region.getMinY());
        yaml.set("min-z", region.getMinZ());
        yaml.set("max-x", region.getMaxX());
        yaml.set("max-y", region.getMaxY());
        yaml.set("max-z", region.getMaxZ());
        if (def.hasOff()) {
            yaml.set("off", def.getOffEntries());
        }
        if (def.hasOn()) {
            yaml.set("on", def.getOnEntries());
        }
        try {
            if (!popoutsDir.exists()) {
                popoutsDir.mkdirs();
            }
            yaml.save(new File(popoutsDir, def.getName().toLowerCase() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save popout '" + def.getName() + "'", e);
        }
    }

    public PopoutDefinition getDefinition(String name) {
        return definitions.get(name.toLowerCase());
    }

    public boolean removeDefinition(String name) {
        PopoutDefinition removed = definitions.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        File file = new File(popoutsDir, name.toLowerCase() + ".yml");
        if (file.exists()) {
            file.delete();
        }
        bindings.values().removeIf(bound -> bound.equalsIgnoreCase(name));
        saveBindings();
        return true;
    }

    public List<String> listNames() {
        return definitions.values().stream().map(PopoutDefinition::getName).sorted().toList();
    }

    public void bind(Location leverLocation, String name) {
        bindings.put(key(leverLocation), name.toLowerCase());
        saveBindings();
    }

    public boolean unbind(Location leverLocation) {
        if (bindings.remove(key(leverLocation)) != null) {
            saveBindings();
            return true;
        }
        return false;
    }

    public String getBoundPopoutName(Location leverLocation) {
        return bindings.get(key(leverLocation));
    }

    private void saveBindings() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(bindingsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save bindings.yml", e);
        }
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }
}
