package com.nexus.serverrules.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.nexus.serverrules.storage.PlayerNameCache;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /ban <player> [reason] - operators only (nexusrules.ban, default op).
 *
 * Paper/vanilla already ships a working /ban command with no plugin
 * required at all - this class OVERRIDES that vanilla command (by
 * declaring "ban" in plugin.yml) so bans go through the same audit
 * trail as everything else in NexusServerRules: a persisted
 * bans-log.txt independent of the vanilla ban list, plus a live alert
 * to other online staff (nexusrules.notify) - the vanilla command gives
 * you neither.
 *
 * Resolves the target name via PlayerNameCache rather than
 * Bukkit.getOfflinePlayer(String) - the latter can silently block the
 * main thread on a Mojang web request for any name the server hasn't
 * seen before, which is not acceptable inside a command handler. The
 * trade-off: this can't pre-ban a name that has never once joined this
 * server (nothing local to resolve it against). If you need that,
 * vanilla /minecraft:ban still works as a fallback for that one case.
 */
public final class BanCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PlayerNameCache playerNameCache;
    private final File banLog;

    public BanCommand(JavaPlugin plugin, PlayerNameCache playerNameCache) {
        this.plugin = plugin;
        this.playerNameCache = playerNameCache;
        this.banLog = new File(plugin.getDataFolder(), "bans-log.txt");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexusrules.ban")) {
            sender.sendMessage("§cYou don't have permission to ban players.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /ban <player> [reason]");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Banned by staff";

        Player online = Bukkit.getPlayerExact(targetName);
        UUID targetId = online != null ? online.getUniqueId() : playerNameCache.lookup(targetName);
        if (targetId == null) {
            sender.sendMessage("§c" + targetName + " has never joined this server, so there's no local record "
                    + "to ban against. Double-check the spelling, or use vanilla /minecraft:ban for that case.");
            return true;
        }

        Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, reason, (Date) null, sender.getName());

        if (online != null) {
            online.kick(Component.text("§cYou have been banned.\n§7Reason: " + reason));
        }

        sender.sendMessage("§a[NexusServerRules] Banned " + targetName + " - " + reason);

        String alert = "§c[NexusServerRules] " + sender.getName() + " banned " + targetName + " - " + reason;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusrules.notify") && !staff.getName().equalsIgnoreCase(sender.getName())) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().warning(alert);
        appendToDisk(sender.getName(), targetName, reason);
        return true;
    }

    private void appendToDisk(String staffName, String targetName, String reason) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            try (FileWriter writer = new FileWriter(banLog, true)) {
                writer.write(String.format("[%s] %s banned %s - %s%n", Instant.now(), staffName, targetName, reason));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Failed to write bans-log.txt", e);
        }
    }
}
