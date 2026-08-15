package com.nexusuniverse.survival.spawn;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class SpawnListener implements Listener {

    private final SpawnManager spawnManager;

    public SpawnListener(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    /** Brand-new players only -- a returning player keeps wherever they last logged out, same as vanilla. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) return;
        Location location = spawnManager.spawnLocation();
        if (location != null) event.getPlayer().teleport(location);
    }

    /** Never overrides a real personal spawn point (bed or respawn anchor) -- only the "no personal spawn set" vanilla-world-spawn case. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;
        Location location = spawnManager.spawnLocation();
        if (location != null) event.setRespawnLocation(location);
    }
}
