package com.nexusuniverse.survival.thirst;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.Plugin;

/**
 * Registers a real furnace recipe: Raw Water in, Water Bottle out, same
 * cook time as standard vanilla smelting. Matched via
 * RecipeChoice.ExactChoice against Raw Water's exact look (name, lore,
 * PDC tag) rather than a plain Material match -- Material alone would
 * also match vanilla's own plain water bottles (same Material.POTION +
 * PotionType.WATER), which would let people "purify" water that was
 * never flagged as raw in the first place. Only water this plugin has
 * already tagged Raw is purifiable this way.
 *
 * This is the first custom crafting/smelting recipe in this plugin --
 * everything before this was give-command items only. Standard,
 * well-established Bukkit API (FurnaceRecipe + RecipeChoice), but worth
 * flagging as new ground for this specific codebase.
 */
public final class WaterPurificationRecipe {

    private WaterPurificationRecipe() {}

    public static void register(Plugin plugin, ThirstItems thirstItems) {
        NamespacedKey key = new NamespacedKey(plugin, "purify_raw_water");
        ItemStack rawWaterExample = thirstItems.createRawWater();
        ItemStack purifiedResult = thirstItems.createWaterBottle();

        FurnaceRecipe recipe = new FurnaceRecipe(
                key,
                purifiedResult,
                new RecipeChoice.ExactChoice(rawWaterExample),
                0.0f, // no XP for boiling water
                200   // same cook time as standard vanilla smelting
        );

        Bukkit.getServer().addRecipe(recipe);
    }
}
