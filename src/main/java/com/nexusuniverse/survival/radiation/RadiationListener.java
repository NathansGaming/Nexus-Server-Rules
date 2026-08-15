package com.nexusuniverse.survival.radiation;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class RadiationListener implements Listener {

    private final NexusSurvivalPlugin plugin;

    public RadiationListener(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!plugin.getRadiationItems().isWand(item)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getRadiationManager().setFirstPoint(player, clicked.getLocation());
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getRadiationManager().setSecondPoint(player, clicked.getLocation());
            event.setCancelled(true);
        }
    }
}
