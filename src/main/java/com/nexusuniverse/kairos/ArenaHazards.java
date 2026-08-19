package com.nexusuniverse.kairos;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

/**
 * Two simple, real hazards -- not just a "hazards enabled" flag with
 * nothing behind it. Floor collapse pulls the ground out from under a
 * random nearby player briefly; fire burst ignites the ground around
 * one. Both telegraph with particles/sound before they actually hurt,
 * so they read as dodgeable rather than cheap.
 */
public class ArenaHazards {

    private final NexusKairosPlugin plugin;
    private final Random random = new Random();

    private static final int COLLAPSE_RESTORE_DELAY_TICKS = 60; // 3s
    private static final int COLLAPSE_RADIUS = 1; // 3x3 patch

    public ArenaHazards(NexusKairosPlugin plugin) {
        this.plugin = plugin;
    }

    public void trigger(Location arenaCenter, List<Player> nearbyPlayers) {
        if (nearbyPlayers.isEmpty()) return;

        Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
        if (random.nextBoolean()) {
            floorCollapse(target);
        } else {
            fireBurst(target);
        }
    }

    private void floorCollapse(Player target) {
        Location base = target.getLocation().clone().subtract(0, 1, 0);
        base.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 30, 1, 0.2, 1);
        base.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_STONE_BREAK, 1f, 0.6f);

        for (int dx = -COLLAPSE_RADIUS; dx <= COLLAPSE_RADIUS; dx++) {
            for (int dz = -COLLAPSE_RADIUS; dz <= COLLAPSE_RADIUS; dz++) {
                Block block = base.clone().add(dx, 0, dz).getBlock();
                Material original = block.getType();
                if (original == Material.AIR || original == Material.VOID_AIR) continue;

                block.setType(Material.AIR);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> block.setType(original), COLLAPSE_RESTORE_DELAY_TICKS);
            }
        }
    }

    private void fireBurst(Player target) {
        Location center = target.getLocation();
        center.getWorld().spawnParticle(Particle.FLAME, center, 40, 1.5, 0.3, 1.5, 0.02);
        center.getWorld().playSound(center, org.bukkit.Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);

        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distance(center) <= 2.5) {
                player.setFireTicks(60); // 3s burn, dodgeable if you see it coming
            }
        }
    }
}
