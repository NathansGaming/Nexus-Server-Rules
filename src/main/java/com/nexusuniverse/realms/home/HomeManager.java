package com.nexusuniverse.realms.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Named per-player homes -- unrelated to the team/land system entirely; every player gets their
 * own homes regardless of team membership or where they're standing when they set one. The
 * per-player CAP is what's configurable (RealmsConfig.homesMaxDefault/homesMaxAdmin, gated by the
 * nexusrealms.homes.admin permission) -- the location itself isn't restricted to your own or your
 * team's claimed land, since a home is a personal teleport point, not a claim.
 */
public class HomeManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Location>> homesByPlayer = new HashMap<>();

    public HomeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    public enum SetResult { OK, TOO_MANY }

    public SetResult set(UUID playerId, String name, Location location, int maxHomes) {
        Map<String, Location> homes = homesByPlayer.computeIfAbsent(playerId, id -> new LinkedHashMap<>());
        String key = name.toLowerCase();
        if (!homes.containsKey(key) && homes.size() >= maxHomes) {
            return SetResult.TOO_MANY;
        }
        homes.put(key, location.clone());
        save();
        return SetResult.OK;
    }

    public Location get(UUID playerId, String name) {
        Map<String, Location> homes = homesByPlayer.get(playerId);
        return homes == null ? null : homes.get(name.toLowerCase());
    }

    public boolean delete(UUID playerId, String name) {
        Map<String, Location> homes = homesByPlayer.get(playerId);
        if (homes == null) return false;
        boolean removed = homes.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public Map<String, Location> homesOf(UUID playerId) {
        return homesByPlayer.getOrDefault(playerId, Map.of());
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Location>> playerEntry : homesByPlayer.entrySet()) {
            String playerPath = "homes." + playerEntry.getKey();
            for (Map.Entry<String, Location> homeEntry : playerEntry.getValue().entrySet()) {
                String path = playerPath + "." + homeEntry.getKey();
                Location loc = homeEntry.getValue();
                data.set(path + ".world", loc.getWorld().getName());
                data.set(path + ".x", loc.getX());
                data.set(path + ".y", loc.getY());
                data.set(path + ".z", loc.getZ());
                data.set(path + ".yaw", (double) loc.getYaw());
                data.set(path + ".pitch", (double) loc.getPitch());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save homes.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = data.getConfigurationSection("homes");
        if (playersSection == null) return;

        for (String playerKey : playersSection.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(playerKey);
                ConfigurationSection homesSection = playersSection.getConfigurationSection(playerKey);
                if (homesSection == null) continue;

                Map<String, Location> homes = new LinkedHashMap<>();
                for (String homeName : homesSection.getKeys(false)) {
                    String path = homeName;
                    World world = Bukkit.getWorld(homesSection.getString(path + ".world", ""));
                    if (world == null) continue; // world not loaded (yet) -- skip rather than crash; won't be re-saved unless re-set
                    double x = homesSection.getDouble(path + ".x");
                    double y = homesSection.getDouble(path + ".y");
                    double z = homesSection.getDouble(path + ".z");
                    float yaw = (float) homesSection.getDouble(path + ".yaw");
                    float pitch = (float) homesSection.getDouble(path + ".pitch");
                    homes.put(homeName, new Location(world, x, y, z, yaw, pitch));
                }
                homesByPlayer.put(playerId, homes);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped corrupt home data for \"" + playerKey + "\" in homes.yml", e);
            }
        }
    }
}
