package com.nexusuniverse.chroma.listener;

import com.nexusuniverse.chroma.item.ChromaItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * When a frame holding one of our chroma squares is destroyed --
 * punched by a player, exploded, knocked off by a physics update --
 * swap its drop back to the real block instead of letting the
 * underlying filled map hit the ground.
 *
 * Deliberately registered against BOTH HangingBreakEvent and its
 * subclass HangingBreakByEntityEvent: Bukkit does not automatically
 * deliver a subclass event to a listener registered only for the
 * parent type, so an entity-caused break (e.g. a player punching the
 * frame) would be silently missed if only HangingBreakEvent were
 * handled. This dual-registration pattern is the standard fix.
 */
public class FrameBreakListener implements Listener {

    private final ChromaItemFactory itemFactory;

    public FrameBreakListener(ChromaItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onBreak(HangingBreakEvent event) {
        swapDrop(event);
    }

    @EventHandler
    public void onBreakByEntity(HangingBreakByEntityEvent event) {
        swapDrop(event);
    }

    private void swapDrop(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        Material source = itemFactory.readSource(frame.getItem());
        if (source == null) {
            return;
        }
        // Clear the frame's item ourselves first so vanilla's own
        // drop-the-contents logic has nothing left to drop, then drop
        // the real block in its place.
        frame.setItem(null, false);
        frame.getWorld().dropItemNaturally(frame.getLocation(), new ItemStack(source));
    }
}
