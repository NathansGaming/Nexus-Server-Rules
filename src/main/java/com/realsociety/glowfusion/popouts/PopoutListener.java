package com.realsociety.glowfusion.popouts;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * Flips a lever's bound popout between its "off" and "on" block snapshots.
 * No animation, no entities - the moment the lever's redstone state
 * changes, every block in the popout's region is overwritten with the
 * matching snapshot. That's what makes a pop-out, ramp, or awning appear
 * and disappear instantly without any redstone contraption behind it.
 */
public final class PopoutListener implements Listener {

    private final GlowFusionPlugin plugin;
    private final PopoutStore store;

    public PopoutListener(GlowFusionPlugin plugin, PopoutStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeverRedstone(BlockRedstoneEvent event) {
        if (!plugin.featureEnabled("popouts.enabled")) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.LEVER) {
            return;
        }
        boolean wasPowered = event.getOldCurrent() > 0;
        boolean isPowered = event.getNewCurrent() > 0;
        if (wasPowered == isPowered) {
            return;
        }

        String name = store.getBoundPopoutName(block.getLocation());
        if (name == null) {
            return;
        }
        PopoutDefinition def = store.getDefinition(name);
        if (def == null || !def.isComplete()) {
            return;
        }
        def.apply(isPowered ? PopoutDefinition.State.ON : PopoutDefinition.State.OFF);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeverBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LEVER) {
            // Only drop the binding - the saved popout design itself stays
            // around in case the player wants to rebind it to a new lever.
            store.unbind(event.getBlock().getLocation());
        }
    }
}
