package com.nexusuniverse.kairos;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The real summon-ritual crystal, distinct from a player just mining
 * vanilla amethyst -- tagged via PersistentDataContainer so counting/
 * consuming logic in FightManager checks for THIS item specifically,
 * not "any amethyst shard in your inventory." Distributed via
 * /kairos givecrystal rather than requiring a crafting recipe.
 */
public class CrystalItems {

    private final NamespacedKey crystalKey;

    public CrystalItems(NexusKairosPlugin plugin) {
        this.crystalKey = new NamespacedKey(plugin, "kairos_crystal");
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dFractured Crystal");
        meta.setLore(java.util.List.of(
                "§7A shard of something that shouldn't exist.",
                "§7Needed to summon Kairos."
        ));
        meta.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCrystal(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer().get(crystalKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
