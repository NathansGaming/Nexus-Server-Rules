package com.nexusuniverse.chroma.listener;

import com.nexusuniverse.chroma.color.ColorTable;
import com.nexusuniverse.chroma.item.ChromaItemFactory;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/**
 * Converts a block placed into an empty item frame into a flat
 * solid-color square (see ChromaItemFactory / SolidColorMapRenderer),
 * and reverses it back into the real block when the square is taken
 * back out.
 *
 * Built against PlayerItemFrameChangeEvent (Paper API) and its
 * documented Cancellable contract: cancelling it is supposed to fully
 * prevent/undo the frame change Paper already resolved for that tick.
 * I don't have a live server to confirm the exact pre-change vs.
 * post-change-then-revert ordering against, so the logic below is
 * written to be correct under either interpretation -- after
 * cancelling, it looks at whichever hand still holds a matching item
 * and consumes from that one, rather than assuming a specific timing.
 * Worth confirming on first real test.
 */
public class FrameListener implements Listener {

    private final ColorTable colorTable;
    private final ChromaItemFactory itemFactory;

    public FrameListener(ColorTable colorTable, ChromaItemFactory itemFactory) {
        this.colorTable = colorTable;
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        if (event.isCancelled()) {
            return; // respect an earlier plugin (e.g. a claim/protection plugin) denying this
        }
        switch (event.getAction()) {
            case PLACE -> handlePlace(event);
            case REMOVE -> handleRemove(event);
            default -> {
                // ROTATE: a flat solid-color square looks identical at every
                // rotation, so there's nothing for this plugin to do here --
                // leave vanilla's rotate-in-place behavior alone.
            }
        }
    }

    private void handlePlace(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("nexuschroma.use")) {
            return;
        }

        Material material = event.getItemStack().getType();
        Color color = colorTable.get(material);
        if (color == null) {
            return; // not a block we have a color for -- let vanilla placement happen untouched
        }

        event.setCancelled(true);

        ItemFrame frame = event.getItemFrame();
        consumeOne(player, material);
        ItemStack chroma = itemFactory.create(material, color, frame.getWorld());
        frame.setItem(chroma, false);
    }

    private void handleRemove(PlayerItemFrameChangeEvent event) {
        Material source = itemFactory.readSource(event.getItemStack());
        if (source == null) {
            return; // an ordinary item, or not one of ours -- let vanilla removal happen
        }

        event.setCancelled(true);

        ItemFrame frame = event.getItemFrame();
        frame.setItem(null, false);
        giveOrDrop(event.getPlayer(), new ItemStack(source));
    }

    private void consumeOne(Player player, Material material) {
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main.getType() == material) {
            main.setAmount(main.getAmount() - 1);
            inv.setItemInMainHand(main);
            return;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == material) {
            off.setAmount(off.getAmount() - 1);
            inv.setItemInOffHand(off);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack remaining : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
    }
}
