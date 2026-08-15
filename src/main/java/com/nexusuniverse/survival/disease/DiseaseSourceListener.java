package com.nexusuniverse.survival.disease;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;

/**
 * Environmental disease sources: breaking a block a disease is tagged
 * against (see Disease.sourceBlocks), harvesting one that isn't "broken"
 * in the usual sense (berry bushes fire PlayerHarvestBlockEvent instead of
 * BlockBreakEvent), or taking a hit from a source mob (Disease.sourceMobs).
 * Biome-based exposure (Disease.sourceBiomes) isn't event-driven -- see
 * DiseaseManager.tick() instead, since "standing in a biome" has no event
 * to hook.
 */
public class DiseaseSourceListener implements Listener {

    private final NexusSurvivalPlugin plugin;

    public DiseaseSourceListener(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getDiseaseManager().tryInfectFromBlock(event.getPlayer(), event.getBlock().getType());
    }

    @EventHandler
    public void onHarvest(PlayerHarvestBlockEvent event) {
        plugin.getDiseaseManager().tryInfectFromBlock(event.getPlayer(), event.getHarvestedBlock().getType());
    }

    @EventHandler
    public void onMobHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity damager = event.getDamager();
        plugin.getDiseaseManager().tryInfectFromMob(player, damager.getType());
    }
}
