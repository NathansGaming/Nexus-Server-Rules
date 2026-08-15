package com.nexusuniverse.realms.worldedit;

import com.nexusuniverse.realms.RealmsConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WandListener implements Listener {

    private final SelectionManager selections;
    private final RealmsConfig config;
    private final WandItemFactory wandItems;

    public WandListener(SelectionManager selections, RealmsConfig config, WandItemFactory wandItems) {
        this.selections = selections;
        this.config = config;
        this.wandItems = wandItems;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!wandItems.isWand(held)) return;
        if (event.getClickedBlock() == null) return;
        if (!event.getPlayer().hasPermission("nexusrealms.worldedit.use") && !event.getPlayer().hasPermission("nexusrealms.worldedit.admin")) return;

        Player player = event.getPlayer();
        Selection selection = selections.get(player.getUniqueId());
        Location clicked = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            // some Bedrock clients (via Geyser) can send one physical left-click as two
            // interact events -- only message on an actual change so that doesn't show as two
            // "Position 1 set." lines back to back; re-setting the same spot is harmless either way
            boolean changed = !clicked.equals(selection.pos1());
            selection.setPos1(clicked);
            if (changed) player.sendMessage(ChatColor.AQUA + "Position 1 set.");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            boolean changed = !clicked.equals(selection.pos2());
            selection.setPos2(clicked);
            if (changed) player.sendMessage(ChatColor.AQUA + "Position 2 set.");
        }
    }
}
