package com.nexusuniverse.chroma.item;

import com.nexusuniverse.chroma.map.SolidColorMapRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds (and reads back) the filled-map item that actually sits in
 * the item frame -- a map whose only renderer paints one flat solid
 * color across the whole canvas (see SolidColorMapRenderer).
 *
 * The map item carries a PersistentDataContainer tag naming the real
 * block it stands in for, so FrameListener / FrameBreakListener can
 * hand the original block back later without needing any separate
 * bookkeeping of "which frame has which block."
 */
public class ChromaItemFactory {

    private final JavaPlugin plugin;
    private final NamespacedKey sourceBlockKey;

    public ChromaItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sourceBlockKey = new NamespacedKey(plugin, "source_block");
    }

    public ItemStack create(Material sourceBlock, Color color, World world) {
        MapView view = plugin.getServer().createMap(world);
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        for (MapRenderer old : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(old);
        }
        view.addRenderer(new SolidColorMapRenderer(color));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("Chroma: " + prettyName(sourceBlock), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Represents " + prettyName(sourceBlock), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(sourceBlockKey, PersistentDataType.STRING, sourceBlock.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Reads the source-block tag off a chroma map item. Returns null for any item that isn't one of ours. */
    public Material readSource(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(sourceBlockKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return Material.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String prettyName(Material material) {
        String[] words = material.name().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return builder.toString();
    }
}
