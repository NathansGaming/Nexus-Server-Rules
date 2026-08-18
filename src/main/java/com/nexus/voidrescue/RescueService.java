package com.nexus.voidrescue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Core rescue logic, shared by the watchdog task, the move-event fast path,
 * and the manual /voidrescue command.
 * <p>
 * Background (see README / config.yml): players - so far confirmed only on
 * Bedrock clients connecting through Geyser - can end up "technically dead"
 * server-side (isDead() == true, near-zero health) without ever completing a
 * respawn, in a state where a plain /tp or /kill doesn't fix them. This
 * mirrors known, still-open upstream reports with no official fix, so this
 * plugin works around it: force-complete the respawn via the Spigot API,
 * then - after a short delay, since some of those upstream reports trace the
 * desync to attribute/health changes applied in the same tick as the
 * death/respawn transition - reset fall state, heal, and teleport to a safe
 * spawn.
 */
public final class RescueService {

    private final Plugin plugin;
    private final Logger logger;
    private final ConfigurationSection config;
    private final Map<UUID, Long> lastRescueMillis = new HashMap<>();

    public RescueService(Plugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
    }

    /** True once a player's Y drops far enough below their world's actual min build height. */
    public boolean isBelowVoidThreshold(Player player) {
        World world = player.getWorld();
        int margin = config.getInt("voidMarginBelowMinHeight", 4);
        return player.getLocation().getY() < (world.getMinHeight() - margin);
    }

    public boolean onCooldown(Player player) {
        Long last = lastRescueMillis.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long cooldownMillis = Math.max(0, config.getInt("rescueCooldownSeconds", 5)) * 1000L;
        return (System.currentTimeMillis() - last) < cooldownMillis;
    }

    /** Call on quit so the cooldown map doesn't leak memory across a long-running server. */
    public void forget(Player player) {
        lastRescueMillis.remove(player.getUniqueId());
    }

    /**
     * Rescues a player. When {@code forced} is true (staff override, or the
     * stuck-dead watchdog path) this bypasses the cooldown entirely and
     * always runs. Safe to call on a player who isn't actually stuck -
     * worst case they get teleported to spawn and topped up on health.
     */
    public void rescue(Player player, String reason, boolean forced) {
        if (!forced && onCooldown(player)) {
            return;
        }
        lastRescueMillis.put(player.getUniqueId(), System.currentTimeMillis());

        boolean wasDead = player.isDead();
        if (wasDead) {
            // Force-completes a stuck client-side death/respawn screen. This is the
            // documented Spigot/Paper API for exactly this desync; see README.
            try {
                player.spigot().respawn();
            } catch (Exception e) {
                logger.warning("[VoidRescue] player.spigot().respawn() threw for "
                        + player.getName() + ": " + e.getMessage() + " - continuing rescue anyway.");
            }
        }

        long delayTicks = Math.max(0, config.getInt("respawnDelayTicks", 4));
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishRescue(player, reason), delayTicks);
    }

    private void finishRescue(Player player, String reason) {
        if (!player.isOnline()) {
            return;
        }

        Location destination = resolveDestination(player.getWorld());

        // Teleport twice, one tick apart, rather than once. A single teleport has
        // been reported to sometimes not register client-side on affected Bedrock/
        // Geyser clients that are already in a desynced state; a second teleport
        // shortly after forces the client to re-acknowledge the new position.
        player.teleport(destination);
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        healToConfiguredAmount(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(destination);
            }
        }, 1L);

        player.sendMessage(Component.text("You were pulled back from the void.", NamedTextColor.AQUA));
        logger.info("[VoidRescue] Rescued " + player.getName() + " (" + reason + ") -> "
                + describeLocation(destination));
        notifyStaff(player, reason);
    }

    private void healToConfiguredAmount(Player player) {
        double configured = config.getDouble("rescueHealth", 20.0);
        double max = 20.0;
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            max = maxHealthAttr.getValue();
        }
        double healTo = Math.max(1.0, Math.min(configured, max));
        player.setHealth(healTo);
    }

    private Location resolveDestination(World fallbackWorld) {
        ConfigurationSection overrides = config.getConfigurationSection("worldSpawns");
        if (overrides != null) {
            ConfigurationSection worldOverride = overrides.getConfigurationSection(fallbackWorld.getName());
            if (worldOverride != null) {
                double x = worldOverride.getDouble("x");
                double y = worldOverride.getDouble("y");
                double z = worldOverride.getDouble("z");
                float yaw = (float) worldOverride.getDouble("yaw", 0);
                float pitch = (float) worldOverride.getDouble("pitch", 0);
                return new Location(fallbackWorld, x, y, z, yaw, pitch);
            }
        }
        return fallbackWorld.getSpawnLocation();
    }

    private void notifyStaff(Player rescued, String reason) {
        if (!config.getBoolean("notifyStaff", true)) {
            return;
        }
        Component msg = Component.text("[VoidRescue] Auto-rescued " + rescued.getName()
                + " (" + reason + ").", NamedTextColor.YELLOW);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("voidrescue.notify")) {
                staff.sendMessage(msg);
            }
        }
    }

    private static String describeLocation(Location loc) {
        return loc.getWorld().getName() + " " + Math.round(loc.getX()) + "," + Math.round(loc.getY())
                + "," + Math.round(loc.getZ());
    }
}
