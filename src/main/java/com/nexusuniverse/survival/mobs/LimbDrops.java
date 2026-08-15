package com.nexusuniverse.survival.mobs;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class LimbDrops {

    private LimbDrops() {}

    public static ItemStack create(EntityType mobType, LimbPart part) {
        boolean skeletal = mobType == EntityType.SKELETON || mobType == EntityType.STRAY || mobType == EntityType.WITHER_SKELETON;
        Material material = skeletal ? Material.BONE : Material.ROTTEN_FLESH;

        String mobLabel = switch (mobType) {
            case HUSK -> "Husk";
            case DROWNED -> "Drowned";
            case SKELETON -> "Skeleton";
            case STRAY -> "Stray";
            case WITHER_SKELETON -> "Wither Skeleton";
            default -> "Zombie";
        };
        String partLabel = switch (part) {
            case HEAD -> "Skull";
            case ARM -> "Arm";
            case LEG -> "Leg";
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c" + mobLabel + " " + partLabel);
        meta.setLore(List.of("§7A grisly trophy."));
        item.setItemMeta(meta);
        return item;
    }
}
