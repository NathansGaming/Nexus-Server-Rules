package com.nexusuniverse.survival.radiation;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

public class RadiationItems {

    private final NamespacedKey immuneKey;
    private final NamespacedKey wandKey;

    public RadiationItems(NexusSurvivalPlugin plugin) {
        this.immuneKey = new NamespacedKey(plugin, "radiation_immune");
        this.wandKey = new NamespacedKey(plugin, "radiation_wand");
    }

    public ItemStack createGasMask() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setDisplayName("§eHazmat Mask");
        meta.setLore(java.util.List.of("§7Fully blocks radiation drain while worn."));
        meta.setColor(Color.fromRGB(255, 221, 51));
        meta.getPersistentDataContainer().set(immuneKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWearingGasMask(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || !helmet.hasItemMeta()) return false;
        Boolean tag = helmet.getItemMeta().getPersistentDataContainer().get(immuneKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        var meta = item.getItemMeta();
        meta.setDisplayName("§dRadiation Zone Wand");
        meta.setLore(java.util.List.of(
                "§7Left-click a block: set corner 1",
                "§7Right-click a block: set corner 2",
                "§7Then: /nexussurvival radiation create <name>"
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Boolean tag = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }
}
