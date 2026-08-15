package com.nexusuniverse.realms.guidebook;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class GuidebookListener implements Listener {

    private final GuidebookManager manager;
    private final GuidebookItem item;

    public GuidebookListener(GuidebookManager manager, GuidebookItem item) {
        this.manager = manager;
        this.item = item;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.hasReceived(player.getUniqueId())) return;

        ItemStack book = item.create();
        var leftover = player.getInventory().addItem(book);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        manager.markReceived(player.getUniqueId());
        player.sendMessage("§7You've been given a copy of the Nexus Realms Handbook.");
    }
}
