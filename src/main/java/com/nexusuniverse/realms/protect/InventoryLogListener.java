package com.nexusuniverse.realms.protect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Container transactions are logged by snapshotting contents on open and again on close, then
 * diffing the two -- not by trying to interpret every individual InventoryClickEvent (a normal
 * click, a shift-click, a double-click that collects a whole stack type, a drag across multiple
 * slots, a hopper feeding in from below while the player has it open). Diffing start vs. end
 * state gets the NET result of all of that correctly with a fraction of the complexity, at the
 * cost of not knowing the exact sequence of clicks that produced it -- a tradeoff worth making
 * here, since "what changed" is what actually matters for both logging and rollback.
 */
public class InventoryLogListener implements Listener {

    private final ProtectionDatabase db;
    private final WatchlistManager watchlist;
    private final Map<UUID, Map<Material, Integer>> openSnapshots = new HashMap<>();

    public InventoryLogListener(ProtectionDatabase db, WatchlistManager watchlist) {
        this.db = db;
        this.watchlist = watchlist;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!isContainer(inventory)) return;

        openSnapshots.put(player.getUniqueId(), countContents(inventory));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!isContainer(inventory)) return;

        Map<Material, Integer> before = openSnapshots.remove(player.getUniqueId());
        if (before == null) return;
        Map<Material, Integer> after = countContents(inventory);

        Location location = containerLocation(inventory);
        long now = System.currentTimeMillis();

        Map<Material, Integer> combined = new HashMap<>(before);
        combined.keySet().addAll(after.keySet());

        for (Map.Entry<Material, Integer> entry : combined.entrySet()) {
            Material material = entry.getKey();
            int beforeCount = before.getOrDefault(material, 0);
            int afterCount = after.getOrDefault(material, 0);
            int delta = afterCount - beforeCount;
            if (delta == 0) continue;

            String action = delta < 0 ? "CONTAINER_WITHDRAW" : "CONTAINER_DEPOSIT";
            int amount = Math.abs(delta);

            db.logInventoryChange(now, player.getUniqueId().toString(), player.getName(),
                    location != null ? location.getWorld().getName() : null,
                    location != null ? location.getBlockX() : null,
                    location != null ? location.getBlockY() : null,
                    location != null ? location.getBlockZ() : null,
                    action, material.name(), amount, null);

            if (delta < 0) {
                watchlist.notifyObtained(player, material, amount, "took from a container");
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        long now = System.currentTimeMillis();
        Location loc = player.getLocation();

        for (ItemStack item : event.getDrops()) {
            if (item == null || item.getType() == Material.AIR) continue;
            db.logInventoryChange(now, player.getUniqueId().toString(), player.getName(),
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    "DEATH_DROP", item.getType().name(), item.getAmount(), null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        watchlist.notifyObtained(player, result.getType(), result.getAmount(), "crafted it");
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        watchlist.notifyObtained(event.getPlayer(), item.getType(), item.getAmount(), "picked it up");
    }

    private boolean isContainer(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Container;
    }

    private Location containerLocation(Inventory inventory) {
        if (inventory.getHolder() instanceof Container container) {
            return container.getBlock().getLocation();
        }
        return null;
    }

    private Map<Material, Integer> countContents(Inventory inventory) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return counts;
    }
}
