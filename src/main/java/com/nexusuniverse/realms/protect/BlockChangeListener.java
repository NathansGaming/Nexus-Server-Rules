package com.nexusuniverse.realms.protect;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * The block half of the log -- BlockBreakEvent and BlockPlaceEvent, captured with the EXACT
 * BlockData string (not just the Material) on both sides, e.g.
 * "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]" rather than just "OAK_STAIRS".
 * That's what makes a rollback actually restore stairs facing the right way, a chest still
 * being a chest and not losing its orientation, redstone wire retaining its exact connections,
 * and so on -- Material alone would be enough to prevent a hole staying a hole, but not enough
 * to make a rollback actually look right afterward.
 */
public class BlockChangeListener implements Listener {

    private final ProtectionDatabase db;

    public BlockChangeListener(ProtectionDatabase db) {
        this.db = db;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String oldData = block.getBlockData().getAsString();
        String newData = "minecraft:air";
        log(event.getPlayer(), block, "BREAK", oldData, newData);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        String oldData = event.getBlockReplacedState().getBlockData().getAsString();
        String newData = block.getBlockData().getAsString();
        log(event.getPlayer(), block, "PLACE", oldData, newData);
    }

    private void log(Player player, Block block, String action, String oldData, String newData) {
        db.logBlockChange(
                System.currentTimeMillis(),
                player.getUniqueId().toString(),
                player.getName(),
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                action, oldData, newData
        );
    }
}
