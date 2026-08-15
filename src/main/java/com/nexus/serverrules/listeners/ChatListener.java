package com.nexus.serverrules.listeners;

import com.nexus.serverrules.detection.MatchConfidence;
import com.nexus.serverrules.detection.ViolationDetector;
import com.nexus.serverrules.detection.ViolationResult;
import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.storage.ReviewQueue;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ChatListener implements Listener {

    private final JavaPlugin plugin;
    private final ViolationDetector detector;
    private final PunishmentManager punishmentManager;
    private final ReviewQueue reviewQueue;
    private final boolean strictMode;

    /**
     * @param strictMode from config.yml chat-detection.strict-mode. When
     *                   true (the default, matching what was asked for),
     *                   any match - EXACT or FUZZY - auto-restricts. When
     *                   false, only EXACT matches auto-restrict; FUZZY-only
     *                   hits are blocked and queued for staff review but the
     *                   player is NOT restricted, so staff can gauge the
     *                   fuzzy matcher's real false-positive rate before
     *                   trusting it to auto-punish.
     */
    public ChatListener(JavaPlugin plugin, ViolationDetector detector,
                         PunishmentManager punishmentManager, ReviewQueue reviewQueue, boolean strictMode) {
        this.plugin = plugin;
        this.detector = detector;
        this.punishmentManager = punishmentManager;
        this.reviewQueue = reviewQueue;
        this.strictMode = strictMode;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Already-restricted players stay muted outright - no need to
        // re-scan, and this guarantees mute holds even if a message
        // somehow slips past detection (defense in depth).
        if (punishmentManager.isRestricted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[NexusServerRules] You are currently restricted and cannot chat. "
                    + "Use /appeal <message> to reach staff, or wait for a staff member to review your case.");
            return;
        }

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        List<ViolationResult> violations = detector.scan(player, rawMessage);
        if (violations.isEmpty()) return;

        // Multiple categories can trip on one message; punish once,
        // but log/queue every category that matched.
        event.setCancelled(true);

        boolean anyExact = violations.stream().anyMatch(v -> v.confidence() == MatchConfidence.EXACT);
        boolean shouldPunish = anyExact || strictMode;

        // Punishment must happen on the main thread - chat events fire async.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            for (ViolationResult v : violations) {
                reviewQueue.add(v);
            }
            if (shouldPunish) {
                punishmentManager.punish(player, violations.get(0));
                player.sendMessage("§c[NexusServerRules] Your message was blocked and your account has been "
                        + "automatically restricted pending staff review. A staff member will look into this - "
                        + "you can explain your side with /appeal <message>.");
            } else {
                player.sendMessage("§e[NexusServerRules] Your message was blocked and flagged for staff review. "
                        + "You have not been restricted, but a repeat may result in one.");
            }
            notifyStaff(player, violations, shouldPunish);
        });
    }

    private void notifyStaff(Player offender, List<ViolationResult> violations, boolean wasPunished) {
        String summary = violations.get(0).shortReason();
        String verb = wasPunished ? "auto-restricted" : "flagged (not restricted, strict-mode off)";
        String alert = "§c[NexusServerRules] " + offender.getName() + " " + verb + " - " + summary
                + " - review with /nexusrules queue";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusrules.notify")) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().warning(alert);
    }
}
