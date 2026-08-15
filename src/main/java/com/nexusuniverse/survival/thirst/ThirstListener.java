package com.nexusuniverse.survival.thirst;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class ThirstListener implements Listener {

    private final NexusSurvivalPlugin plugin;
    private final Random random = new Random();

    public ThirstListener(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (plugin.getThirstItems().isCanteen(item)) {
            handleCanteenSip(event, item, player);
            return;
        }

        if (plugin.getThirstItems().isWaterBottle(item)) {
            plugin.getThirstManager().drink(player);
            return;
        }

        if (plugin.getThirstItems().isPlainWaterPotion(item)) {
            plugin.getThirstManager().drink(player);
            return;
        }

        if (plugin.getThirstItems().isRawWater(item)) {
            plugin.getThirstManager().drinkRaw(player);
            double chance = plugin.getNexusSurvivalConfig().getDouble("disease.raw-water-contamination-chance", 0.15)
                    * plugin.getNexusSurvivalConfig().diseaseChanceMultiplier();
            if (random.nextDouble() < chance) {
                plugin.getDiseaseManager().infectRandom(player);
            }
        }
    }

    /**
     * One sip from a Canteen: restores thirst and burns a charge instead
     * of consuming the whole item like a Water Bottle. Uses Paper's
     * PlayerItemConsumeEvent#setReplacement (not the base Bukkit
     * getItem()/setItem()) to swap in a Canteen with one fewer charge as
     * what's left in the player's hand afterward, overriding vanilla's
     * default "leave a glass bottle behind" replacement for a drained
     * potion. At 0 charges the Canteen itself is what's left behind (see
     * ThirstItems#buildCanteen) -- it doesn't degrade into a plain glass
     * bottle, so onCanteenRefill below still recognizes it as a Canteen.
     */
    private void handleCanteenSip(PlayerItemConsumeEvent event, ItemStack item, Player player) {
        var thirstItems = plugin.getThirstItems();
        int charges = thirstItems.getCanteenCharges(item);

        if (charges <= 0) {
            // Shouldn't normally happen -- onCanteenRefill intercepts right-clicks on a water
            // source before this event fires, and an empty Canteen aimed at anything else still
            // has nothing to give. Refuse the drink rather than restoring thirst for free.
            event.setCancelled(true);
            player.sendMessage("§7Your canteen is empty. Right-click a water source to refill it.");
            return;
        }

        plugin.getThirstManager().drinkCanteenSip(player);

        int remaining = charges - 1;
        event.setReplacement(thirstItems.buildCanteen(remaining));

        if (remaining <= 0) {
            player.sendMessage("§7Your canteen is now empty. Right-click a water source to refill it.");
        }
    }

    /**
     * Right-clicking a water source (lake, river, cauldron, or waterlogged
     * block) while holding a Canteen that isn't already full tops it back
     * up to CANTEEN_MAX_CHARGES sips -- no glass bottle round-trip needed,
     * that's the entire point of carrying one instead of a stack of Water
     * Bottles. Handled here rather than corrected a tick later like
     * onBottleFill below: a Canteen is a POTION item, so if this were left
     * to run normally the player would just drink it instead of filling
     * it, and there'd be nothing useful to "fix up" afterward. DENY on
     * useItemInHand (in addition to cancelling) stops that drink attempt
     * from ever starting.
     */
    @EventHandler
    public void onCanteenRefill(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isWaterSource(clicked)) return;

        ItemStack itemInHand = event.getItem();
        var thirstItems = plugin.getThirstItems();
        if (!thirstItems.isCanteen(itemInHand)) return;

        int charges = thirstItems.getCanteenCharges(itemInHand);
        if (charges >= ThirstItems.CANTEEN_MAX_CHARGES) return; // already full -- let it drink normally instead

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        ItemStack refilled = thirstItems.buildCanteen(ThirstItems.CANTEEN_MAX_CHARGES);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(refilled);
        } else {
            player.getInventory().setItemInMainHand(refilled);
        }
        player.sendMessage("§bYou refill your canteen.");
    }

    /**
     * Catches the moment a player fills a glass bottle from a real water
     * source (lake, river, cauldron, waterlogged block) and upgrades the
     * result into Raw Water -- untreated, drinkable in a pinch, but not
     * as good as a properly boiled Water Bottle. Checked one tick later:
     * the actual glass-bottle-to-potion swap happens as part of
     * vanilla's own handling of this same right-click, not something we
     * get to control the timing of, so we let that finish first and then
     * look at what ended up in the player's hand.
     */
    @EventHandler
    public void onBottleFill(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isWaterSource(clicked)) return;

        ItemStack itemInHand = event.getItem();
        if (itemInHand == null || itemInHand.getType() != Material.GLASS_BOTTLE) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack current = hand == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            plugin.getThirstItems().tagAsRawWater(current);
        });
    }

    private boolean isWaterSource(Block block) {
        if (block.getType() == Material.WATER || block.getType() == Material.WATER_CAULDRON) return true;
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }
}
