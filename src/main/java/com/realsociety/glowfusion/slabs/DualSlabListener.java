package com.realsociety.glowfusion.slabs;

import com.realsociety.glowfusion.GlowFusionPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Lets a player right-click an existing bottom slab while holding a
 * *different* colored/material slab to visually fuse the two together in a
 * single block space.
 *
 * <p>Paper (and vanilla) only lets two slabs of the <em>same</em> material
 * combine into a real "double slab" blockstate. To get a genuinely
 * different-looking top half without shipping a resource pack, we keep the
 * real block as the bottom slab (for collision/physics/mining) and render
 * the second slab visually with a {@link BlockDisplay} entity positioned in
 * the top half of the same block space. No client-side resource pack is
 * required because the display simply renders an existing vanilla
 * blockstate.</p>
 */
public final class DualSlabListener implements Listener {

    public static final NamespacedKey FUSED_KEY =
            new NamespacedKey("glowfusion", "fused_slab_display");

    private final GlowFusionPlugin plugin;

    public DualSlabListener(GlowFusionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.featureEnabled("dual-slabs.enabled")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            // Avoid double-firing for main hand + off hand.
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() != BlockFace.UP) {
            return;
        }
        if (!(clicked.getBlockData() instanceof Slab baseSlab)) {
            return;
        }
        if (baseSlab.getType() != Slab.Type.BOTTOM) {
            // Only the natural "click the top of a bottom slab" placement is
            // supported, mirroring vanilla's own double-slab gesture.
            return;
        }

        Player player = event.getPlayer();
        if (plugin.requiresPermission("dual-slabs.require-permission")
                && !player.hasPermission("glowfusion.fuse")) {
            return;
        }

        ItemStack inHand = event.getItem();
        if (inHand == null || !isSlab(inHand.getType())) {
            return;
        }
        if (inHand.getType() == clicked.getType()) {
            // Same material: let vanilla's normal double-slab behavior happen.
            return;
        }
        if (findFusedDisplay(clicked.getLocation()) != null) {
            // Already fused - make players break it first before re-fusing.
            player.sendActionBar(Component.text(
                    "This slab is already fused - break it first to change the top half."));
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);

        Slab topData = (Slab) inHand.getType().createBlockData();
        topData.setType(Slab.Type.TOP);
        spawnFusedTop(clicked, topData);

        if (plugin.featureEnabled("dual-slabs.consume-item")
                && player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        if (plugin.featureEnabled("dual-slabs.play-sound")) {
            clicked.getWorld().playSound(clicked.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.1f);
        }
    }

    private void spawnFusedTop(Block baseBlock, BlockData topHalf) {
        Location loc = baseBlock.getLocation();
        BlockDisplay display = baseBlock.getWorld().spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(topHalf);
            // Explicit identity transform: don't rely on whatever a freshly
            // spawned display defaults to - this guarantees it renders
            // exactly where the block model says it should (no rotation,
            // no scaling), regardless of server/version quirks.
            entity.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(1f, 1f, 1f), new Quaternionf()));
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.getPersistentDataContainer().set(FUSED_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        display.setInvulnerable(true);
    }

    /** Finds the fused-top display entity (if any) sitting at this exact block. */
    public static BlockDisplay findFusedDisplay(Location blockLocation) {
        // Search around the block's own corner - the exact point we spawn
        // the display at - not its center. Searching around the center let
        // a same-Y-level *neighboring* block's display (whose corner sits
        // exactly 1 block away) fall inside the search box too, since both
        // are equidistant from the center; anchoring on the corner instead
        // keeps neighbors safely outside this radius.
        Location corner = blockLocation.toBlockLocation();
        List<Entity> nearby = blockLocation.getWorld().getNearbyEntities(corner, 0.6, 0.6, 0.6)
                .stream().toList();
        for (Entity entity : nearby) {
            if (entity instanceof BlockDisplay display
                    && display.getPersistentDataContainer().has(FUSED_KEY, PersistentDataType.BYTE)) {
                return display;
            }
        }
        return null;
    }

    /**
     * Forcibly removes a fused-top display anchored at this block, whether
     * or not the real block underneath still looks the way this feature
     * expects. Used by the "/glowfusion unstick" recovery command for a
     * "ghost" display left behind after the real bottom slab was removed by
     * something other than our own break/explode listeners (another
     * plugin, world edit, a chunk regeneration, or an unclean server
     * shutdown that lost the entity but not a since-reverted block).
     *
     * @return true if a display was actually found and removed.
     */
    public static boolean forceClearFusedDisplay(Location blockLocation) {
        BlockDisplay display = findFusedDisplay(blockLocation);
        if (display == null) {
            return false;
        }
        display.remove();
        return true;
    }

    private static boolean isSlab(Material material) {
        return material.name().endsWith("_SLAB");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        removeFusedDisplayAndDrop(event.getBlock(), true, event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeFusedDisplayAndDrop(block, false, null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeFusedDisplayAndDrop(block, false, null);
        }
    }

    private void removeFusedDisplayAndDrop(Block block, boolean dropItem, Location dropAt) {
        BlockDisplay display = findFusedDisplay(block.getLocation());
        if (display == null) {
            return;
        }
        Material topMaterial = display.getBlock().getMaterial();
        display.remove();
        if (dropItem && topMaterial != null) {
            block.getWorld().dropItemNaturally(
                    dropAt != null ? dropAt : block.getLocation(),
                    new ItemStack(topMaterial));
        }
    }

    /** Keeps players/mobs/projectiles from damaging or popping the illusion entity directly. */
    @EventHandler(ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof BlockDisplay display
                && display.getPersistentDataContainer().has(FUSED_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
