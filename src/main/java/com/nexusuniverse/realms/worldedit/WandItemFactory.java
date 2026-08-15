package com.nexusuniverse.realms.worldedit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * The /redit selection wand needs to be identifiable as THE wand specifically -- not just "any
 * item of the configured material." The default material is a golden axe, a normal, usable
 * vanilla tool (chops wood, strips logs on right-click, fights). Matching on material alone
 * would hijack every golden axe swing on the server -- for every player, since
 * nexusrealms.worldedit.use is on by default -- into WorldEdit position-setting instead of its
 * normal vanilla behavior, the moment they picked one up for an ordinary reason. Tagging the
 * specific item this plugin actually gave out with a PersistentDataContainer marker (the same
 * PDC-tagging pattern the rest of the Nexus plugin family already uses for its own custom items)
 * means only that exact item works as a wand; an unrelated golden axe a player mined or bought
 * behaves like a normal golden axe.
 *
 * The visible name and lore matter for the same reason on top of that: a plain, unnamed golden
 * axe sitting in a hotbar gives no clue what it does, which is a real usability problem for
 * anyone who can't easily inspect item tooltips at a glance -- worth it for every player, but
 * particularly worth it for controller users.
 */
public class WandItemFactory {

    private final NamespacedKey key;

    public WandItemFactory(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "worldedit_wand");
    }

    public ItemStack create(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "WorldEdit Wand");
        meta.setLore(List.of(
                ChatColor.GRAY + "Left-click a block: set position 1",
                ChatColor.GRAY + "Right-click a block: set position 2"
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
