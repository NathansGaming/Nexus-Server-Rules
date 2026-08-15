package com.nexus.serverrules.gui;

import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.punishment.Restriction;
import com.nexus.serverrules.storage.RestrictionStore;
import com.nexus.serverrules.storage.ReviewQueue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class GuiListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final RestrictionStore restrictionStore;
    private final ReviewQueue reviewQueue;

    public GuiListener(PunishmentManager punishmentManager, RestrictionStore restrictionStore, ReviewQueue reviewQueue) {
        this.punishmentManager = punishmentManager;
        this.restrictionStore = restrictionStore;
        this.reviewQueue = reviewQueue;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReviewQueueHolder)) return;
        event.setCancelled(true); // this GUI is view/act-only, never a place to store items

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String uuidStr = meta.getPersistentDataContainer().get(ReviewQueueGui.PLAYER_KEY_TAG, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID playerId = UUID.fromString(uuidStr);

        if (!(event.getWhoClicked() instanceof Player sender)) return;

        if (event.isRightClick()) {
            Restriction r = restrictionStore.get(playerId);
            if (r == null) {
                sender.sendMessage("§cThat player is no longer restricted.");
                return;
            }
            sender.sendMessage("§e--- Case: " + r.playerName + " ---");
            sender.sendMessage("§7Category: §f" + r.category);
            sender.sendMessage("§7Reason: §f" + r.reason);
            sender.sendMessage("§7Triggering message: §f\"" + r.triggeringMessage + "\"");
            sender.sendMessage("§7Triggered at: §f" + r.triggeredAt);
            return;
        }

        // Left-click: clear.
        if (!sender.hasPermission("nexusrules.clear")) {
            sender.sendMessage("§cYou don't have permission to clear restrictions.");
            return;
        }
        boolean cleared = punishmentManager.clear(playerId, sender.getName());
        if (cleared) {
            reviewQueue.clearForPlayer(playerId);
            sender.sendMessage("§a[NexusServerRules] Cleared restriction.");
            sender.closeInventory();
        } else {
            sender.sendMessage("§cThat player is no longer restricted.");
        }
    }
}
