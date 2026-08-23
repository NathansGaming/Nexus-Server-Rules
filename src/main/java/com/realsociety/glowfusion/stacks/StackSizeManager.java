package com.realsociety.glowfusion.stacks;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Bumps every normally-stackable item's max stack size up to a configured
 * cap (default: vanilla's own hard ceiling of 99), instead of the usual 64
 * (or less - snowballs/eggs/ender pearls default to 16).
 *
 * <p>HONEST LIMITATION on the number itself: this isn't a plugin-side rule
 * we get to pick freely. Since Minecraft 1.20.5, stack size is one of the
 * game's own built-in "data components" (`minecraft:max_stack_size`) - the
 * exact same mechanism the game uses for a custom name or enchantments -
 * and the vanilla client/server both hard-clamp that component to the
 * range 1-99 no matter what a plugin tries to set it to. There is no way,
 * from a Paper plugin or otherwise, to push a normal item past 99 - that
 * ceiling is baked into the game itself, not something GlowFusion imposes.
 * Vanilla also refuses to combine a stack size above 1 with a "max_damage"
 * component (i.e. anything with durability - tools, weapons, armor), which
 * is why those are correctly left untouched below: they already report a
 * max stack size of 1 from the game itself, and forcing anything higher on
 * them isn't something the game allows regardless.</p>
 *
 * <p>Applying the new cap is "stamp it onto every item stack" rather than
 * "flip one global switch" - stack size lives on the individual item, the
 * same way a custom name would, so every different place a fresh ItemStack
 * can appear (crafted, looted, dropped, picked up, moved by a hopper, given
 * by another plugin or command) needs to get the same stamp, or two stacks
 * of the same material could end up unable to merge with each other. See
 * {@link StackSizeListener} for the events that do that stamping.</p>
 */
public final class StackSizeManager {

    private static final int VANILLA_HARD_CAP = 99;

    private final GlowFusionPlugin plugin;
    private int maxStackSize;

    public StackSizeManager(GlowFusionPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        int configured = plugin.getConfig().getInt("bigger-stacks.max-stack-size", VANILLA_HARD_CAP);
        if (configured > VANILLA_HARD_CAP) {
            plugin.getLogger().warning("bigger-stacks.max-stack-size (" + configured + ") is above " + VANILLA_HARD_CAP
                    + " - that's vanilla Minecraft's own hard ceiling for the max_stack_size item component, "
                    + "not a GlowFusion limit, so it can't be pushed any higher. Using " + VANILLA_HARD_CAP + " instead.");
            configured = VANILLA_HARD_CAP;
        }
        if (configured < 2) {
            plugin.getLogger().warning("bigger-stacks.max-stack-size (" + configured + ") is too low to do anything "
                    + "useful - falling back to " + VANILLA_HARD_CAP + ".");
            configured = VANILLA_HARD_CAP;
        }
        this.maxStackSize = configured;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    /**
     * Stamps the configured max stack size onto this one item, in place, if
     * it's eligible and doesn't already carry the right value.
     *
     * @return true if the item was actually changed (the caller is
     * responsible for writing it back into whatever inventory/entity it
     * came from - see {@link #normalizeInventory(Inventory)} for the
     * inventory case).
     */
    public boolean apply(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType().getMaxStackSize() <= 1) {
            return false; // not stackable to begin with - tools, armor, unique items, shulker boxes, etc.
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        // Compare against whatever's actually in effect right now - an explicit stamp if
        // there is one, otherwise the item's own vanilla default - so a sweep doesn't keep
        // rewriting an item whose default already happens to match the configured cap.
        int effectiveCurrent = meta.hasMaxStackSize() ? meta.getMaxStackSize() : item.getType().getMaxStackSize();
        if (effectiveCurrent == maxStackSize) {
            return false; // already correct - avoids needless work re-stamping every already-fixed item
        }
        meta.setMaxStackSize(maxStackSize);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Sweeps every slot of an inventory (a player's own inventory, an ender
     * chest, or any container - chest, shulker box, hopper, furnace, etc.)
     * and stamps every eligible item. Writes changes back through {@code
     * setItem} explicitly rather than relying on in-place mutation of
     * whatever array {@code getContents()} handed back, since that's not
     * guaranteed to be a live view depending on the inventory type.
     *
     * @return true if anything in the inventory was changed.
     */
    public boolean normalizeInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        boolean changed = false;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && apply(item)) {
                inventory.setItem(i, item);
                changed = true;
            }
        }
        return changed;
    }
}
