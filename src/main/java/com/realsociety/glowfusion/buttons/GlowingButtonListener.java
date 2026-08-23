package com.realsociety.glowfusion.buttons;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.Switch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Makes buttons emit real block light while they're pressed/powered.
 *
 * <p>Vanilla buttons never carry a light-emitting blockstate, and a plugin
 * can't retroactively change a vanilla block's luminance. Instead, whenever a
 * button becomes powered we place an invisible {@code minecraft:light} block
 * (a vanilla, mapmaker-oriented block with an adjustable 0-15 light level and
 * no hitbox/texture) in the empty space right in front of the button - i.e.
 * where a player stands to press it - and remove it again the moment the
 * button stops being powered. That spot lighting up reads as "the button
 * itself is glowing" without needing a resource pack.</p>
 */
public final class GlowingButtonListener implements Listener {

    private final GlowFusionPlugin plugin;
    private final LightStore lightStore;
    private BukkitTask verificationTask;

    public GlowingButtonListener(GlowFusionPlugin plugin, LightStore lightStore) {
        this.plugin = plugin;
        this.lightStore = lightStore;
    }

    public void reload() {
        // Config is read live from plugin.getConfig() on every event, so
        // there's nothing to re-cache here today; kept for future settings
        // that might need eager recomputation, and restarts the task with
        // any new interval.
        stopVerificationTask();
        startVerificationTask();
    }

    @EventHandler(ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!plugin.featureEnabled("glowing-buttons.enabled")) {
            return;
        }
        Block block = event.getBlock();
        if (!isButton(block.getType())) {
            return;
        }
        boolean wasPowered = event.getOldCurrent() > 0;
        boolean isPowered = event.getNewCurrent() > 0;
        if (wasPowered == isPowered) {
            return;
        }
        if (isPowered) {
            turnOn(block);
        } else {
            turnOff(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onButtonBreak(BlockBreakEvent event) {
        if (isButton(event.getBlock().getType())) {
            turnOff(event.getBlock());
        }
    }

    private void turnOn(Block button) {
        Location buttonLoc = button.getLocation();
        if (lightStore.hasLight(buttonLoc)) {
            return;
        }
        if (!(button.getBlockData() instanceof Switch sw)) {
            return;
        }
        Block target = button.getRelative(sw.getFacing());
        if (target.getType() != Material.AIR) {
            // Don't clobber whatever's already occupying that space.
            return;
        }
        BlockData data = Material.LIGHT.createBlockData();
        if (data instanceof Light light) {
            int level = clampLevel(plugin.getConfig().getInt("glowing-buttons.light-level", 15));
            light.setLevel(level);
            target.setBlockData(light);
            lightStore.put(buttonLoc, target.getLocation());
        }
    }

    private void turnOff(Block button) {
        Location buttonLoc = button.getLocation();
        Location lightLoc = lightStore.getLight(buttonLoc);
        if (lightLoc == null) {
            return;
        }
        Block lightBlock = lightLoc.getBlock();
        if (lightBlock.getType() == Material.LIGHT) {
            lightBlock.setType(Material.AIR);
        }
        lightStore.remove(buttonLoc);
    }

    /**
     * Periodically reconciles tracked buttons in case a BlockRedstoneEvent
     * was missed (e.g. the button or its supporting block was removed by
     * world edit tooling, or another plugin toggled power directly).
     */
    public void startVerificationTask() {
        long interval = Math.max(20L, plugin.getConfig().getLong("glowing-buttons.verification-interval-ticks", 100L));
        verificationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::verifyAll, interval, interval);
    }

    public void stopVerificationTask() {
        if (verificationTask != null) {
            verificationTask.cancel();
            verificationTask = null;
        }
    }

    private void verifyAll() {
        for (Location buttonLoc : lightStore.allButtonLocations()) {
            World world = buttonLoc.getWorld();
            if (world == null) {
                lightStore.remove(buttonLoc);
                continue;
            }
            if (!world.isChunkLoaded(buttonLoc.getBlockX() >> 4, buttonLoc.getBlockZ() >> 4)) {
                continue; // don't force-load chunks just to verify
            }
            Block block = buttonLoc.getBlock();
            boolean stillGlowing = isButton(block.getType())
                    && block.getBlockData() instanceof Powerable powerable
                    && powerable.isPowered();
            if (!stillGlowing) {
                turnOff(block);
            }
        }
    }

    private static boolean isButton(Material material) {
        return material.name().endsWith("_BUTTON");
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(15, level));
    }
}
