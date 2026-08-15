package com.nexusuniverse.survival.hygiene;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Sneak + right-click a water cauldron while wearing armor to wash it.
 * Deliberately does not consume the water level in the cauldron for v0.1 --
 * a real implementation would drain a level per wash, flagged as a rough edge.
 */
public class HygieneListener implements Listener {

    private final NexusSurvivalPlugin plugin;

    public HygieneListener(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.WATER_CAULDRON) return;

        plugin.getHygieneManager().wash(player);
        event.setCancelled(true);
    }
}
