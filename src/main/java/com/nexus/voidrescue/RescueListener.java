package com.nexus.voidrescue;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/**
 * Fast path on top of {@link VoidWatchdogTask}: catches a player falling
 * below the void threshold immediately (on their next move event) rather
 * than waiting up to one scan interval. Does not replace the watchdog - a
 * player who is fully stuck and not moving at all still needs the periodic
 * sweep, which is why both exist.
 */
public final class RescueListener implements Listener {

    private final RescueService rescueService;

    public RescueListener(RescueService rescueService) {
        this.rescueService = rescueService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (rescueService.isBelowVoidThreshold(player)) {
            rescueService.rescue(player, "fell below the world", false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rescueService.forget(event.getPlayer());
    }
}
