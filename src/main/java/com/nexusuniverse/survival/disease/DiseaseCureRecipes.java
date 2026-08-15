package com.nexusuniverse.survival.disease;

import com.nexusuniverse.survival.thirst.ThirstItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/**
 * Every cure is craftable, not just admin-giveable -- a Water Bottle (our
 * tagged one specifically, not just any water potion, so the recipe stays
 * well-defined) plus two thematic ingredients tied to that disease's
 * flavor or source. This is what makes surviving an outbreak a real skill
 * loop instead of something that depends on an admin handing out cures.
 */
public final class DiseaseCureRecipes {

    private DiseaseCureRecipes() {}

    public static void registerAll(Plugin plugin, DiseaseItems diseaseItems, ThirstItems thirstItems) {
        register(plugin, diseaseItems, thirstItems, Disease.RATTLING_COUGH, Material.HONEY_BOTTLE, Material.SUGAR);
        register(plugin, diseaseItems, thirstItems, Disease.FEVER_ROT, Material.GOLDEN_CARROT, Material.ROTTEN_FLESH);
        register(plugin, diseaseItems, thirstItems, Disease.GLOWSICKNESS, Material.MILK_BUCKET, Material.GLOWSTONE_DUST);
        register(plugin, diseaseItems, thirstItems, Disease.BONE_CHILL, Material.MILK_BUCKET, Material.BLAZE_POWDER);
        register(plugin, diseaseItems, thirstItems, Disease.SWAMP_FEVER, Material.MILK_BUCKET, Material.LILY_PAD);
        register(plugin, diseaseItems, thirstItems, Disease.IRON_LUNG, Material.MILK_BUCKET, Material.CHARCOAL);
        register(plugin, diseaseItems, thirstItems, Disease.STATIC_SHOCK, Material.MILK_BUCKET, Material.REDSTONE);
        register(plugin, diseaseItems, thirstItems, Disease.WITHERING_PLAGUE, Material.GOLDEN_APPLE, Material.MILK_BUCKET);
        register(plugin, diseaseItems, thirstItems, Disease.BREATH_FEVER, Material.MILK_BUCKET, Material.SUGAR);
        register(plugin, diseaseItems, thirstItems, Disease.BUSH_HAZE, Material.MILK_BUCKET, Material.SWEET_BERRIES);
        register(plugin, diseaseItems, thirstItems, Disease.NECROTIC_BLIGHT, Material.GOLDEN_APPLE, Material.WITHER_ROSE);
        register(plugin, diseaseItems, thirstItems, Disease.DEEP_ROT, Material.MILK_BUCKET, Material.SCULK);
    }

    private static void register(Plugin plugin, DiseaseItems diseaseItems, ThirstItems thirstItems,
                                  Disease disease, Material ingredientA, Material ingredientB) {
        NamespacedKey key = new NamespacedKey(plugin, "cure_" + disease.name().toLowerCase());
        ItemStack result = diseaseItems.createCure(disease);

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        recipe.addIngredient(new RecipeChoice.ExactChoice(thirstItems.createWaterBottle()));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(ingredientA));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(ingredientB));

        Bukkit.addRecipe(recipe);
    }
}
