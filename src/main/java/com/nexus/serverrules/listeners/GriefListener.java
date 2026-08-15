package com.nexus.serverrules.listeners;

import com.nexus.serverrules.detection.MatchConfidence;
import com.nexus.serverrules.detection.ViolationCategory;
import com.nexus.serverrules.detection.ViolationResult;
import com.nexus.serverrules.grief.PlacedBlockRegistry;
import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.storage.ReviewQueue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Heuristic griefing detection: watches for a player rapidly breaking
 * blocks that someone ELSE placed (not natural terrain, not their own
 * build). A burst of that within a short window reads as griefing
 * rather than normal survival mining, and routes through the exact
 * same restriction pipeline as chat violations - same restrictions.yml
 * entry, same staff queue/GUI, same manual-clear-only rule, same
 * appeal channel.
 *
 * This has no concept of land claims or permissions - it only knows
 * "someone else placed this recently and you just broke a lot of
 * those, fast." It will have false positives (e.g. two players mining
 * through each other's temporary scaffolding). Tune the constants
 * below - or wire them to config.yml - based on what you actually see.
 */
public final class GriefListener implements Listener {

    private final JavaPlugin plugin;
    private final PunishmentManager punishmentManager;
    private final ReviewQueue reviewQueue;
    private final PlacedBlockRegistry registry;
    private final int windowSeconds;
    private final int blockThreshold;
    private final Map<UUID, Deque<Instant>> recentSuspectBreaks = new HashMap<>();

    public GriefListener(JavaPlugin plugin, PunishmentManager punishmentManager, ReviewQueue reviewQueue,
                          int windowSeconds, int blockThreshold, int registryCapacity) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
        this.reviewQueue = reviewQueue;
        this.registry = new PlacedBlockRegistry(registryCapacity);
        this.windowSeconds = windowSeconds;
        this.blockThreshold = blockThreshold;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        registry.recordPlaced(PlacedBlockRegistry.BlockKey.of(event.getBlock().getLocation()), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Already-restricted players are forced into adventure mode,
        // which already blocks breaking most blocks - this is just
        // defense in depth for CanDestroy NBT edge cases.
        if (punishmentManager.isRestricted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        PlacedBlockRegistry.BlockKey key = PlacedBlockRegistry.BlockKey.of(event.getBlock().getLocation());
        UUID owner = registry.ownerOf(key);
        registry.forget(key);
        if (owner == null || owner.equals(player.getUniqueId())) {
            return; // natural terrain, or their own build - not suspicious
        }

        Deque<Instant> breaks = recentSuspectBreaks.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        Instant now = Instant.now();
        breaks.addLast(now);
        while (!breaks.isEmpty() && now.getEpochSecond() - breaks.peekFirst().getEpochSecond() > windowSeconds) {
            breaks.pollFirst();
        }

        if (breaks.size() < blockThreshold) return;

        int burstSize = breaks.size();
        breaks.clear();

        // Threshold hit on this exact block - stop this one too, then punish.
        event.setCancelled(true);

        ViolationResult violation = new ViolationResult(
                player.getUniqueId(),
                player.getName(),
                burstSize + " other players' blocks broken within " + windowSeconds + "s",
                "",
                "block-break-burst",
                ViolationCategory.GRIEFING,
                MatchConfidence.EXACT,
                now
        );
        punishmentManager.punish(player, violation);
        reviewQueue.add(violation);

        player.sendMessage("§c[NexusServerRules] You've been automatically restricted for rapidly destroying "
                + "other players' builds. A staff member will review this - use /appeal <message> to explain.");

        String alert = "§c[NexusServerRules] " + player.getName() + " auto-restricted - griefing burst ("
                + burstSize + " blocks in " + windowSeconds + "s) - review with /nexusrules queue";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusrules.notify")) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().warning(alert);
    }
}
