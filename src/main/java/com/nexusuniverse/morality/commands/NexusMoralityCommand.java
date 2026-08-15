package com.nexusuniverse.morality.commands;

import com.nexusuniverse.morality.config.NexusMoralityConfig;
import com.nexusuniverse.morality.encounter.EncounterManager;
import com.nexusuniverse.morality.encounter.SurvivorEncounter;
import com.nexusuniverse.morality.karma.KarmaStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;

public final class NexusMoralityCommand implements CommandExecutor {

    private final EncounterManager encounterManager;
    private final KarmaStore karmaStore;
    private final NexusMoralityConfig config;

    public NexusMoralityCommand(EncounterManager encounterManager, KarmaStore karmaStore, NexusMoralityConfig config) {
        this.encounterManager = encounterManager;
        this.karmaStore = karmaStore;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7Usage: /nexusmorality <trigger [player]|karma <player>|list|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "trigger" -> handleTrigger(sender, args);
            case "karma" -> handleKarma(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void handleTrigger(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found or not online: " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cConsole must specify a player: /nexusmorality trigger <player>");
            return;
        }

        boolean spawned = encounterManager.forceSpawnNear(target);
        if (spawned) {
            sender.sendMessage("§aSpawned a survivor encounter near " + target.getName() + ".");
        } else {
            sender.sendMessage("§cCouldn't spawn one - either the concurrent-encounter cap is full, "
                    + "or no safe location was found nearby after 10 attempts. Try again or move the target.");
        }
    }

    private void handleKarma(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusmorality karma <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int karma = karmaStore.get(target.getUniqueId());
        sender.sendMessage("§7" + (target.getName() != null ? target.getName() : args[1]) + "'s karma: §f" + karma);
    }

    private void handleList(CommandSender sender) {
        var active = encounterManager.activeSnapshot();
        if (active.isEmpty()) {
            sender.sendMessage("§7No active survivor encounters right now.");
            return;
        }
        sender.sendMessage("§7--- Active Encounters (" + active.size() + ") ---");
        for (SurvivorEncounter encounter : active) {
            long ageSeconds = Duration.between(encounter.spawnedAt, Instant.now()).getSeconds();
            Location loc = encounter.spawnLocation;
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            sender.sendMessage("§f" + encounter.state + " §7- " + world + " " + loc.getBlockX() + ","
                    + loc.getBlockY() + "," + loc.getBlockZ() + " §7(spawned " + ageSeconds + "s ago)");
        }
    }

    private void handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage("§aConfig reloaded - new values apply to encounters spawned from now on.");
    }
}
