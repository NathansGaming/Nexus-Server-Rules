package com.nexusuniverse.survival.disease;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * Cure items are plain water-base potions (no vanilla effects of their
 * own) tagged with which Disease they cure. Drinking one that doesn't
 * match your current infection just does nothing special -- it's a wasted
 * potion, not an error, matching how a real "wrong medicine" would feel.
 */
public class DiseaseItems {

    private final NamespacedKey cureKey;

    public DiseaseItems(NexusSurvivalPlugin plugin) {
        this.cureKey = new NamespacedKey(plugin, "cure_for");
    }

    public ItemStack createCure(Disease disease) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        meta.setDisplayName("§aCure: " + disease.getDisplayName());
        meta.setLore(java.util.List.of("§7Drink while infected with", "§7" + disease.getDisplayName() + " to cure it."));
        meta.getPersistentDataContainer().set(cureKey, PersistentDataType.STRING, disease.name());
        item.setItemMeta(meta);
        return item;
    }

    public Disease readCureType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(cureKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Disease.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
