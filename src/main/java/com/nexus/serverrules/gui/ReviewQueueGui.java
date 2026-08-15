package com.nexus.serverrules.gui;

import com.nexus.serverrules.punishment.Restriction;
import com.nexus.serverrules.storage.RestrictionStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory-based version of /nexusrules queue - one player head per
 * currently-restricted player, left-click to clear (nexusrules.clear),
 * right-click for full case detail in chat. Built directly from
 * RestrictionStore so it can never drift out of sync with the text
 * command or /nexusrules info.
 */
public final class ReviewQueueGui {

    public static final NamespacedKey PLAYER_KEY_TAG = new NamespacedKey("nexusserverrules", "restricted-player-uuid");

    private ReviewQueueGui() {}

    public static Inventory build(RestrictionStore store) {
        Map<UUID, Restriction> active = store.allActive();
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(active.size() / 9.0)));

        ReviewQueueHolder holder = new ReviewQueueHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Component.text("NexusServerRules - Review Queue"));
        holder.setInventory(inv);

        int slot = 0;
        for (Restriction r : active.values()) {
            if (slot >= inv.getSize()) break; // more than 54 flagged at once - use /nexusrules queue for the rest

            OfflinePlayer target = Bukkit.getOfflinePlayer(r.playerId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text("§c" + r.playerName));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Category: §f" + r.category));
            lore.add(Component.text("§7Reason: §f" + r.reason));
            lore.add(Component.text("§7Triggered: §f" + r.triggeredAt));
            lore.add(Component.text(""));
            lore.add(Component.text("§aLeft-click: clear restriction"));
            lore.add(Component.text("§eRight-click: full case detail in chat"));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(PLAYER_KEY_TAG, PersistentDataType.STRING, r.playerId.toString());
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }
        return inv;
    }
}
