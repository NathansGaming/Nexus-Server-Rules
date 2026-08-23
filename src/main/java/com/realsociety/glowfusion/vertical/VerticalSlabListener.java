package com.realsociety.glowfusion.vertical;

import com.realsociety.glowfusion.GlowFusionPlugin;
import com.realsociety.glowfusion.slabs.DualSlabListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lets a player crouch + right-click an empty-handed slab to stand it up
 * against a wall (cycling north/east/south/west), and crouch + right-click
 * again (or attack it) to cycle further / break it back down.
 *
 * <p>Decorative only, by design: vanilla has no "vertical slab" blockstate,
 * so there's no way to give the standing panel a matching half-depth
 * collision box without a resource pack. Instead, while standing, the real
 * block becomes AIR (no collision) and a rotated {@link BlockDisplay}
 * renders the slab's own texture as a thin vertical panel. A co-located,
 * invisible {@link Interaction} entity is what actually catches the
 * follow-up clicks, since right-clicking AIR doesn't fire a block-interact
 * event the way right-clicking a real block does.</p>
 */
public final class VerticalSlabListener implements Listener {

    public static final NamespacedKey VERTICAL_KEY = new NamespacedKey("glowfusion", "vertical_slab");

    private final GlowFusionPlugin plugin;
    private final VerticalSlabStore store;

    public VerticalSlabListener(GlowFusionPlugin plugin, VerticalSlabStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    // --- First press: a real, flat slab -> standing (NORTH) ---

    @EventHandler(ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (!plugin.featureEnabled("vertical-slabs.enabled")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getItem() != null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getBlockData() instanceof Slab)) {
            return;
        }
        if (plugin.requiresPermission("vertical-slabs.require-permission")
                && !player.hasPermission("glowfusion.vertical")) {
            return;
        }
        if (DualSlabListener.findFusedDisplay(clicked.getLocation()) != null) {
            player.sendActionBar(Component.text("Can't stand up a fused slab - break it first."));
            return;
        }
        if (store.has(clicked.getLocation())) {
            // A real, solid Slab block can only exist here if the earlier
            // "standing" entry is stale - standUp() always sets the real
            // block to AIR, so if we're looking at an actual Slab, whatever
            // display/interaction that entry once pointed to is gone (most
            // likely: the server stopped or crashed before the world
            // autosaved them, while our own vertical-slabs.yml - written
            // immediately on every change - still remembers this spot as
            // occupied). Clear the stale bookkeeping instead of silently
            // refusing to stand this slab up.
            store.remove(clicked.getLocation());
        }

        event.setCancelled(true);
        standUp(clicked, clicked.getBlockData().getAsString(), VerticalOrientation.NORTH);
    }

    // --- Follow-up presses: cycle facing, or return to FLAT ---

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (!plugin.featureEnabled("vertical-slabs.enabled") || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction) || !isOurs(clicked)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (plugin.requiresPermission("vertical-slabs.require-permission")
                && !player.hasPermission("glowfusion.vertical")) {
            return;
        }

        event.setCancelled(true);

        Location blockLoc = clicked.getLocation().toBlockLocation();
        VerticalSlabStore.Entry entry = store.get(blockLoc);
        if (entry == null) {
            return;
        }

        VerticalOrientation next = entry.orientation.next();
        if (next == VerticalOrientation.FLAT) {
            layDown(blockLoc, entry, true);
        } else {
            entry.orientation = next;
            store.put(blockLoc, entry);
            updateDisplayOrientation(blockLoc, next);
        }
    }

    // --- Attacking the standing panel breaks it entirely ---

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!plugin.featureEnabled("vertical-slabs.enabled")) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !isOurs(event.getEntity())) {
            return;
        }
        event.setCancelled(true);

        Location blockLoc = event.getEntity().getLocation().toBlockLocation();
        VerticalSlabStore.Entry entry = store.get(blockLoc);
        if (entry == null) {
            return;
        }
        removeEntitiesAt(blockLoc);
        store.remove(blockLoc);
        if (player.getGameMode() != GameMode.CREATIVE) {
            Material material = Bukkit.createBlockData(entry.originalBlockData).getMaterial();
            blockLoc.getWorld().dropItemNaturally(blockLoc, new ItemStack(material));
        }
        // Block stays AIR - it's been broken, same as mining a normal slab.
    }

    // --- Don't let something else get built into the "hollow" space ---

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.featureEnabled("vertical-slabs.enabled")) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (!store.has(loc)) {
            return;
        }
        if (!hasLiveEntities(loc)) {
            // The store thinks this space is occupied, but there's no
            // display/interaction here to back that up - almost always an
            // unclean shutdown that lost the entities while our own
            // vertical-slabs.yml (saved immediately, not on the world's
            // autosave schedule) still remembers the spot as standing.
            // Self-heal instead of permanently blocking this space.
            store.remove(loc);
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("A standing slab occupies that space - break it first."));
    }

    // --- Helpers ---

    private void standUp(Block block, String originalData, VerticalOrientation orientation) {
        Location loc = block.getLocation();
        block.setType(Material.AIR);

        Slab displayData = (Slab) Bukkit.createBlockData(originalData);
        displayData.setType(Slab.Type.BOTTOM); // normalize shape; our transform math assumes this

        String key = VerticalSlabStore.key(loc);
        Transformation transform = transformFor(orientation);

        loc.getWorld().spawn(loc, BlockDisplay.class, display -> {
            display.setBlock(displayData);
            display.setTransformation(transform);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.getPersistentDataContainer().set(VERTICAL_KEY, PersistentDataType.STRING, key);
        });

        Location center = loc.clone().add(0.5, 0.5, 0.5);
        loc.getWorld().spawn(center, Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(1.0f);
            interaction.setPersistent(true);
            interaction.setInvulnerable(true);
            interaction.getPersistentDataContainer().set(VERTICAL_KEY, PersistentDataType.STRING, key);
        });

        store.put(loc, new VerticalSlabStore.Entry(originalData, orientation));
    }

    private void layDown(Location blockLoc, VerticalSlabStore.Entry entry, boolean removeEntities) {
        if (removeEntities) {
            removeEntitiesAt(blockLoc);
        }
        BlockData data = Bukkit.createBlockData(entry.originalBlockData);
        blockLoc.getBlock().setBlockData(data, false);
        store.remove(blockLoc);
    }

    private void updateDisplayOrientation(Location blockLoc, VerticalOrientation orientation) {
        // Anchored on the block's corner (where the BlockDisplay is
        // actually spawned), not its center - see the comment in
        // DualSlabListener#findFusedDisplay for why the center would let a
        // same-level neighboring block's entities false-match here too.
        for (Entity entity : blockLoc.getWorld().getNearbyEntities(blockLoc, 0.6, 0.6, 0.6)) {
            if (entity instanceof BlockDisplay display && isOurs(display)) {
                display.setTransformation(transformFor(orientation));
                return;
            }
        }
    }

    private void removeEntitiesAt(Location blockLoc) {
        for (Entity entity : blockLoc.getWorld().getNearbyEntities(blockLoc, 0.6, 0.6, 0.6).stream().toList()) {
            if ((entity instanceof BlockDisplay || entity instanceof Interaction) && isOurs(entity)) {
                entity.remove();
            }
        }
    }

    private boolean hasLiveEntities(Location blockLoc) {
        for (Entity entity : blockLoc.getWorld().getNearbyEntities(blockLoc, 0.6, 0.6, 0.6)) {
            if ((entity instanceof BlockDisplay || entity instanceof Interaction) && isOurs(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forcibly clears whatever this plugin has tracked at this exact block
     * - any standing-slab display/interaction pair, and its store entry -
     * restoring the original block if we have a record of what it was.
     * Backs the "/glowfusion unstick" recovery command for a spot left
     * permanently blocked (can't break, can't place) after entities were
     * lost while the store still thought the space was occupied.
     *
     * @return true if anything was actually cleared.
     */
    public boolean forceClear(Location blockLoc) {
        boolean removedEntities = false;
        for (Entity entity : blockLoc.getWorld().getNearbyEntities(blockLoc, 0.6, 0.6, 0.6).stream().toList()) {
            if ((entity instanceof BlockDisplay || entity instanceof Interaction) && isOurs(entity)) {
                entity.remove();
                removedEntities = true;
            }
        }
        VerticalSlabStore.Entry entry = store.get(blockLoc);
        if (entry != null) {
            store.remove(blockLoc);
            if (blockLoc.getBlock().getType() == Material.AIR) {
                blockLoc.getBlock().setBlockData(Bukkit.createBlockData(entry.originalBlockData), false);
            }
            return true;
        }
        return removedEntities;
    }

    private static boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(VERTICAL_KEY, PersistentDataType.STRING);
    }

    /**
     * Builds the rotation + translation that turns a normalized BOTTOM slab
     * (occupying the bottom half of the unit cube, y in [0, 0.5]) into a
     * full-height, half-thickness panel flush against the given compass
     * face. Derived directly from the slab's raw model coordinates:
     * rotating 90 degrees about the X axis swaps the Y/Z roles (turning the
     * vertical half-cut into a north/south-facing one), and rotating about
     * the Z axis swaps X/Y instead (giving an east/west-facing one).
     */
    private static Transformation transformFor(VerticalOrientation facing) {
        Vector3f translation;
        Quaternionf rotation;
        switch (facing) {
            case NORTH -> {
                rotation = new Quaternionf().rotationX((float) Math.toRadians(90));
                translation = new Vector3f(0f, 1f, 0f);
            }
            case SOUTH -> {
                rotation = new Quaternionf().rotationX((float) Math.toRadians(90));
                translation = new Vector3f(0f, 1f, 0.5f);
            }
            case WEST -> {
                rotation = new Quaternionf().rotationZ((float) Math.toRadians(90));
                translation = new Vector3f(0.5f, 0f, 0f);
            }
            case EAST -> {
                rotation = new Quaternionf().rotationZ((float) Math.toRadians(90));
                translation = new Vector3f(1.0f, 0f, 0f);
            }
            default -> {
                rotation = new Quaternionf();
                translation = new Vector3f(0f, 0f, 0f);
            }
        }
        return new Transformation(translation, rotation, new Vector3f(1f, 1f, 1f), new Quaternionf());
    }
}
