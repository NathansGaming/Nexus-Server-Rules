package com.nexus.serverrules.commands;

import com.nexus.serverrules.detection.PatternRepository;
import com.nexus.serverrules.detection.ViolationResult;
import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.punishment.Restriction;
import com.nexus.serverrules.storage.PlayerNameCache;
import com.nexus.serverrules.storage.RestrictionStore;
import com.nexus.serverrules.storage.ReviewQueue;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-facing review/control surface:
 *   /nexusrules queue            - list players currently under an active restriction, with why
 *   /nexusrules info <player>    - full detail on one flagged player's triggering message(s)
 *   /nexusrules clear <player>   - lift a restriction (nexusrules.clear)
 *   /nexusrules reload           - hot-reload patterns.yml (nexusrules.reload)
 *
 * This is deliberately a functional text-based queue rather than only
 * a GUI, so it works over console/Discord-bridge logging too - a GUI
 * inventory view can be layered on top of the same ReviewQueue/
 * RestrictionStore data later without changing this command.
 */
public final class NexusRulesCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PatternRepository patternRepository;
    private final PunishmentManager punishmentManager;
    private final RestrictionStore restrictionStore;
    private final ReviewQueue reviewQueue;
    private final PlayerNameCache playerNameCache;

    public NexusRulesCommand(JavaPlugin plugin, PatternRepository patternRepository,
                              PunishmentManager punishmentManager, RestrictionStore restrictionStore,
                              ReviewQueue reviewQueue, PlayerNameCache playerNameCache) {
        this.plugin = plugin;
        this.patternRepository = patternRepository;
        this.punishmentManager = punishmentManager;
        this.restrictionStore = restrictionStore;
        this.reviewQueue = reviewQueue;
        this.playerNameCache = playerNameCache;
    }

    /**
     * Resolves a name to a UUID with zero network calls: checks online
     * players first (instant), then the local PlayerNameCache (also
     * instant). Deliberately does NOT fall back to
     * Bukkit.getOfflinePlayer(String) - that call can block the main
     * thread on a Mojang web request for any name the server has never
     * seen, which is not a price a staff command should risk paying.
     * Returns null (and messages the sender) if the name is unresolvable.
     */
    private UUID resolvePlayer(CommandSender sender, String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        UUID cached = playerNameCache.lookup(name);
        if (cached != null) return cached;
        sender.sendMessage("§c" + name + " has never joined this server, so there's no local record to resolve.");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /nexusrules <queue|gui|info <player>|clear <player>|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "queue" -> handleQueue(sender);
            case "gui" -> handleGui(sender);
            case "info" -> handleInfo(sender, args);
            case "clear" -> handleClear(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage("§eUsage: /nexusrules <queue|gui|info <player>|clear <player>|reload>");
        }
        return true;
    }

    private void handleGui(CommandSender sender) {
        if (!sender.hasPermission("nexusrules.queue")) {
            sender.sendMessage("§cYou don't have permission to view the review queue.");
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cOnly players can open the GUI - use /nexusrules queue instead.");
            return;
        }
        player.openInventory(com.nexus.serverrules.gui.ReviewQueueGui.build(restrictionStore));
    }

    private void handleQueue(CommandSender sender) {
        if (!sender.hasPermission("nexusrules.queue")) {
            sender.sendMessage("§cYou don't have permission to view the review queue.");
            return;
        }
        Map<UUID, Restriction> active = restrictionStore.allActive();
        if (active.isEmpty()) {
            sender.sendMessage("§a[NexusServerRules] No players are currently restricted.");
            return;
        }
        sender.sendMessage("§e[NexusServerRules] " + active.size() + " player(s) awaiting review:");
        for (Restriction r : active.values()) {
            sender.sendMessage("§7 - §f" + r.playerName + " §7: §c" + r.reason
                    + " §7(triggered " + r.triggeredAt + ") - /nexusrules info " + r.playerName);
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusrules.queue")) {
            sender.sendMessage("§cYou don't have permission to view case details.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /nexusrules info <player>");
            return;
        }
        UUID targetId = resolvePlayer(sender, args[1]);
        if (targetId == null) return;
        Restriction r = restrictionStore.get(targetId);
        if (r == null) {
            sender.sendMessage("§c" + args[1] + " is not currently restricted.");
            return;
        }
        sender.sendMessage("§e--- Case: " + r.playerName + " ---");
        sender.sendMessage("§7Category: §f" + r.category);
        sender.sendMessage("§7Reason: §f" + r.reason);
        sender.sendMessage("§7Triggering message: §f\"" + r.triggeringMessage + "\"");
        sender.sendMessage("§7Triggered at: §f" + r.triggeredAt);
        sender.sendMessage("§7Previous gamemode (restored on clear): §f" + r.previousGameMode);

        List<ViolationResult> history = new ArrayList<>();
        for (ViolationResult v : reviewQueue.all()) {
            if (v.playerId().equals(targetId)) history.add(v);
        }
        if (!history.isEmpty()) {
            sender.sendMessage("§7Full flagged history this queue-cycle (" + history.size() + "):");
            for (ViolationResult v : history) {
                sender.sendMessage("§7   [" + v.timestamp() + "] " + v.shortReason() + " - \"" + v.rawMessage() + "\"");
            }
        }
        sender.sendMessage("§7Clear with: §f/nexusrules clear " + r.playerName);
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusrules.clear")) {
            sender.sendMessage("§cYou don't have permission to clear restrictions.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /nexusrules clear <player>");
            return;
        }
        UUID targetId = resolvePlayer(sender, args[1]);
        if (targetId == null) return;
        boolean cleared = punishmentManager.clear(targetId, sender.getName());
        if (cleared) {
            reviewQueue.clearForPlayer(targetId);
            sender.sendMessage("§a[NexusServerRules] Cleared restriction for " + args[1] + ".");
        } else {
            sender.sendMessage("§c" + args[1] + " is not currently restricted.");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexusrules.reload")) {
            sender.sendMessage("§cYou don't have permission to reload patterns.");
            return;
        }
        patternRepository.reload();
        sender.sendMessage("§a[NexusServerRules] patterns.yml reloaded (" + patternRepository.entries().size() + " entries).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("queue", "gui", "info", "clear", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("info"))) {
            List<String> names = new ArrayList<>();
            for (var r : restrictionStore.allActive().values()) names.add(r.playerName);
            return names;
        }
        return List.of();
    }
}
