package com.nexus.voidrescue;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Periodic sweep over every online player, run on a timer rather than relying
 * solely on movement events - a genuinely stuck/frozen player (the exact
 * "technically dead, can't teleport, can't kill" case this plugin exists for)
 * may not generate any PlayerMoveEvent at all while stuck.
 */
public final class VoidWatchdogTask extends BukkitRunnable {

    private final RescueService rescueService;
    private final int scanIntervalTicks;
    private final int stuckDeadTicks;
    private final Map<UUID, Integer> deadTickCounters = new HashMap<>();

    public VoidWatchdogTask(RescueService rescueService, int scanIntervalTicks, int stuckDeadTicks) {
        this.rescueService = rescueService;
        this.scanIntervalTicks = Math.max(1, scanIntervalTicks);
        this.stuckDeadTicks = Math.max(1, stuckDeadTicks);
    }

    @Override
    public void run() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (player.isDead()) {
                int ticks = deadTickCounters.merge(id, scanIntervalTicks, Integer::sum);
                if (ticks >= stuckDeadTicks) {
                    deadTickCounters.remove(id);
                    rescueService.rescue(player, "stuck in a dead/unrespawned state", true);
                }
                continue;
            }
            deadTickCounters.remove(id);

            if (rescueService.isBelowVoidThreshold(player)) {
                rescueService.rescue(player, "fell below the world", false);
            }
        }
    }
}
