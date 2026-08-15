package com.nexusuniverse.survival.thirst;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

public class ThirstItems {

    /** Sips a full Canteen holds before it needs refilling. */
    public static final int CANTEEN_MAX_CHARGES = 6;

    private final NamespacedKey waterBottleKey;
    private final NamespacedKey rawWaterKey;
    private final NamespacedKey canteenKey;
    private final NamespacedKey canteenChargesKey;

    public ThirstItems(NexusSurvivalPlugin plugin) {
        this.waterBottleKey = new NamespacedKey(plugin, "water_bottle");
        this.rawWaterKey = new NamespacedKey(plugin, "raw_water");
        this.canteenKey = new NamespacedKey(plugin, "water_canteen");
        this.canteenChargesKey = new NamespacedKey(plugin, "canteen_charges");
    }

    public ItemStack createWaterBottle() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        applyWaterBottleLook(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRawWater() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        applyRawWaterLook(meta);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A full Canteen -- CANTEEN_MAX_CHARGES sips before it runs dry.
     * Unlike a Water Bottle, drinking one doesn't consume it outright:
     * each sip just knocks a charge off (see ThirstListener#onConsume),
     * and it can be topped back up at any water source instead of being
     * re-bottled from scratch (see ThirstListener#onCanteenRefill).
     */
    public ItemStack createCanteen() {
        return buildCanteen(CANTEEN_MAX_CHARGES);
    }

    /**
     * Rebuilds a Canteen at a specific charge count. Used to hand back a
     * fresh ItemStack after each sip (one charge lower) and after a
     * refill (back to CANTEEN_MAX_CHARGES) -- PotionMeta/lore/PDC don't
     * update themselves in place, so the item is just recreated each time.
     */
    public ItemStack buildCanteen(int charges) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        applyCanteenLook(meta, charges);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Upgrades a plain, freshly-filled water bottle (from a lake, river,
     * cauldron, or waterlogged block) into Raw Water -- drinkable in a
     * pinch, but untreated: partial thirst restore and a real chance of
     * illness. Boiling it in a furnace (see WaterPurificationRecipe)
     * turns it into a proper Water Bottle. Returns false (and leaves the
     * item untouched) if it isn't actually a plain water potion -- a
     * real brewed potion sitting in the same hand never gets touched.
     */
    public boolean tagAsRawWater(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.getBasePotionType() != PotionType.WATER) return false;
        if (isWaterBottle(item) || isRawWater(item)) return false; // already tagged, nothing to do

        applyRawWaterLook(meta);
        item.setItemMeta(meta);
        return true;
    }

    private void applyWaterBottleLook(PotionMeta meta) {
        meta.setDisplayName("§bWater Bottle");
        meta.setLore(java.util.List.of("§7Right-click to drink and restore thirst."));
        meta.getPersistentDataContainer().set(waterBottleKey, PersistentDataType.BOOLEAN, true);
    }

    private void applyRawWaterLook(PotionMeta meta) {
        meta.setDisplayName("§7Raw Water");
        meta.setLore(java.util.List.of(
                "§7Untreated -- restores less thirst and may",
                "§7make you sick. Boil in a furnace to purify."
        ));
        meta.getPersistentDataContainer().set(rawWaterKey, PersistentDataType.BOOLEAN, true);
    }

    private void applyCanteenLook(PotionMeta meta, int charges) {
        charges = Math.max(0, Math.min(CANTEEN_MAX_CHARGES, charges));
        if (charges <= 0) {
            meta.setDisplayName("§7Empty Canteen");
            meta.setLore(java.util.List.of(
                    "§70/" + CANTEEN_MAX_CHARGES + " sips left.",
                    "§7Right-click a water source to refill it."
            ));
        } else {
            meta.setDisplayName("§bCanteen §7(" + charges + "/" + CANTEEN_MAX_CHARGES + ")");
            meta.setLore(java.util.List.of(
                    "§7Right-click to drink -- " + charges + "/" + CANTEEN_MAX_CHARGES + " sips left.",
                    "§7Refills at any water source, no glass bottle needed."
            ));
        }
        meta.getPersistentDataContainer().set(canteenKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(canteenChargesKey, PersistentDataType.INTEGER, charges);
    }

    /**
     * A plain, unmodified water potion -- not our tagged Water Bottle, Raw
     * Water, or Canteen. Mainly relevant for a player (usually an admin)
     * grabbing stock water bottles straight from the creative inventory to
     * hand out -- those still restore thirst at full strength, same as a
     * proper Water Bottle, so you're not forced to go through
     * /nexussurvival give.
     */
    public boolean isPlainWaterPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (isWaterBottle(item) || isRawWater(item) || isCanteen(item)) return false; // already handled as one of ours
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return meta.getBasePotionType() == PotionType.WATER;
    }

    public boolean isWaterBottle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Boolean tag = item.getItemMeta().getPersistentDataContainer().get(waterBottleKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    public boolean isRawWater(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Boolean tag = item.getItemMeta().getPersistentDataContainer().get(rawWaterKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    public boolean isCanteen(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Boolean tag = item.getItemMeta().getPersistentDataContainer().get(canteenKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    /** Sips remaining in this Canteen. 0 for an item that isn't a Canteen at all. */
    public int getCanteenCharges(ItemStack item) {
        if (!isCanteen(item)) return 0;
        Integer charges = item.getItemMeta().getPersistentDataContainer().get(canteenChargesKey, PersistentDataType.INTEGER);
        return charges == null ? 0 : charges;
    }
}
