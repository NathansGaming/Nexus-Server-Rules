package com.nexusuniverse.survival.spawn;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * A single, fixed WorldSpawn location -- applied through the real World spawn setting rather
 * than a private per-player teleport hack, specifically so anything that reads world spawn under
 * the hood (vanilla's own death-respawn-with-no-bed logic, the F3 debug screen's spawn marker,
 * another plugin's own "/spawn" command) resolves to the same fixed point automatically, without
 * needing to know NexusSurvival even exists. Applied three ways for real reliability:
 *
 *  1. World#setSpawnLocation on this plugin's enable -- the actual fix, covers everything above.
 *  2. Explicitly re-applied on every non-bed/non-anchor PlayerRespawnEvent (see SpawnListener) --
 *     a safety net in case something else on the server resets the world's own spawn point after
 *     this plugin set it (load order, another admin tool, a world reset, etc).
 *  3. Explicitly applied to every brand-new player on their very first join (see SpawnListener)
 *     -- vanilla should already put them at the world spawn once it's set correctly via (1), but
 *     this makes it certain instead of just relying on that.
 */
public class SpawnManager {

    private final JavaPlugin plugin;
    private final NexusSurvivalConfig config;

    public SpawnManager(JavaPlugin plugin, NexusSurvivalConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Called once on enable. */
    public void applyWorldSpawn() {
        if (!config.getBoolean("spawn.enabled", true)) return;
        Location location = spawnLocation();
        if (location == null) return;
        location.getWorld().setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** The configured fixed spawn point, or null (logged once) if the configured world isn't actually loaded. */
    public Location spawnLocation() {
        String worldName = config.getString("spawn.world", "");
        World world = worldName != null && !worldName.isEmpty() ? Bukkit.getWorld(worldName) : firstWorld();
        if (world == null) {
            plugin.getLogger().log(Level.WARNING, "NexusSurvival: spawn.world '" + worldName
                    + "' isn't a loaded world -- can't set the fixed WorldSpawn point. Check the config.");
            return null;
        }

        double x = config.getDouble("spawn.x", 0);
        double y = config.getDouble("spawn.y", 64);
        double z = config.getDouble("spawn.z", 0);
        float yaw = (float) config.getDouble("spawn.yaw", 0);
        float pitch = (float) config.getDouble("spawn.pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private World firstWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}
