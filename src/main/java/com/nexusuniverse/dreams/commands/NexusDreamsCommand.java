package com.nexusuniverse.dreams.commands;

import com.nexusuniverse.dreams.config.NexusDreamsConfig;
import com.nexusuniverse.dreams.listeners.SleepListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class NexusDreamsCommand implements CommandExecutor {

    private final SleepListener sleepListener;
    private final NexusDreamsConfig config;

    public NexusDreamsCommand(SleepListener sleepListener, NexusDreamsConfig config) {
        this.sleepListener = sleepListener;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7Usage: /nexusdreams <trigger [player]|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "trigger" -> handleTrigger(sender, args);
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
            sender.sendMessage("§cConsole must specify a player: /nexusdreams trigger <player>");
            return;
        }

        // Forced for testing - doesn't require the target to actually be asleep, and bypasses
        // the cooldown, unlike a real night-skip-triggered dream.
        sleepListener.forceDream(target);
        sender.sendMessage("§aTriggered a dream for " + target.getName() + ".");
    }

    private void handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage("§aConfig reloaded - new values apply to the next dream.");
    }
}
