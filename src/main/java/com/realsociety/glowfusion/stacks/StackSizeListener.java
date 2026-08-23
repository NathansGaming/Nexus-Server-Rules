package com.realsociety.glowfusion.stacks;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Every place a fresh {@link ItemStack} can enter play gets its own hook
 * here so it picks up the configured max stack size immediately, plus a
 * light recurring sweep of online players' own inventories as a safety net
 * for anything that slips in some other way (an admin's {@code /give}, an
 * economy plugin's shop, WorldEdit, etc.) - see {@link StackSizeManager}'s
 * class javadoc for why every entry point needs this instead of one global
 * switch.
 */
public final class StackSizeListener implements Listener {

    private final GlowFusionPlugin plugin;
    private final StackSizeManager manager;
    private BukkitTask sweepTask;

    public StackSizeListener(GlowFusionPlugin plugin, StackSizeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** A player's own inventory/ender chest is where every other source (join, craft, pickup...) eventually lands, so a periodic pass over just those catches anything else (plugin-given items, commands) without needing to scan the whole loaded world. */
    public void startSweepTask() {
        int intervalSeconds = Math.max(5, plugin.getConfig().getInt("bigger-stacks.resync-interval-seconds", 10));
        long intervalTicks = intervalSeconds * 20L;
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                manager.normalizeInventory(player.getInventory());
                manager.normalizeInventory(player.getEnderChest());
            }
        }, intervalTicks, intervalTicks);
    }

    public void stopSweepTask() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.normalizeInventory(player.getInventory());
        manager.normalizeInventory(player.getEnderChest());
    }

    /** Stamps the crafted result before it's even taken, so what the player sees in the output slot already reflects the real stack size. */
    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (result != null && manager.apply(result)) {
            inventory.setResult(result);
        }
    }

    /** Covers natural block drops, mob drops, dispenser ejects - anything that becomes a real ground item entity. */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (manager.apply(stack)) {
            entity.setItemStack(stack);
        }
    }

    /** Stamps a ground item right before it's picked up, so it merges correctly with whatever's already in the player's inventory. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (manager.apply(stack)) {
            entity.setItemStack(stack);
        }
    }

    /** Hoppers (and other automatic item movers) transferring an un-stamped stack between containers. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (manager.apply(item)) {
            event.setItem(item);
        }
    }

    /** Chest/fishing/entity loot tables generate items with no meta at all by default. */
    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        List<ItemStack> loot = event.getLoot();
        List<ItemStack> updated = new ArrayList<>(loot.size());
        boolean changed = false;
        for (ItemStack item : loot) {
            if (manager.apply(item)) {
                changed = true;
            }
            updated.add(item);
        }
        if (changed) {
            event.setLoot(updated);
        }
    }

    /**
     * Safety net for any container a player just finished looking at
     * (chest, shulker box, barrel, furnace, another player's trade window,
     * etc.) - catches anything that reached it by a path not covered
     * above, e.g. another plugin's shop or command putting items straight
     * into an open inventory.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        manager.normalizeInventory(event.getInventory());
    }
}
