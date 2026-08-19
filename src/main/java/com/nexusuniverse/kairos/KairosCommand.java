package com.nexusuniverse.kairos;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KairosCommand implements CommandExecutor {

    private final NexusKairosPlugin plugin;

    public KairosCommand(NexusKairosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "summon" -> {
                String failReason = plugin.getFightManager().attemptSummon(player);
                if (failReason != null) {
                    player.sendMessage("§c" + failReason);
                } else {
                    player.sendMessage("§5Kairos has been summoned.");
                }
            }
            case "status" -> {
                player.sendMessage("§7Fight count: §e" + plugin.getFightState().getFightCount());
                player.sendMessage("§7Current band: §e" + FightBand.forFightCount(plugin.getFightState().getFightCount() + 1).displayName);
                player.sendMessage("§7Active fight: §e" + plugin.getFightManager().isFightActive());
                player.sendMessage("§7True ending armed: §e" + plugin.getFightState().isTrueEndingArmed());
            }
            case "forcetier" -> {
                if (!player.hasPermission("nexuskairos.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /kairos forcetier <fight count>");
                    return true;
                }
                try {
                    int value = Integer.parseInt(args[1]);
                    plugin.getFightState().forceFightCount(value);
                    player.sendMessage("§aFight count set to " + value + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cThat's not a number.");
                }
            }
            case "armtrueending" -> {
                if (!player.hasPermission("nexuskairos.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                plugin.getFightState().setTrueEndingArmed(true);
                player.sendMessage("§aTrue ending armed -- the next summon will be a real kill.");
            }
            case "givecrystal" -> {
                if (!player.hasPermission("nexuskairos.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                org.bukkit.entity.Player target = player;
                int amount = 1;
                if (args.length >= 2) {
                    org.bukkit.entity.Player found = org.bukkit.Bukkit.getPlayer(args[1]);
                    if (found == null) {
                        player.sendMessage("§cPlayer \"" + args[1] + "\" not found or offline.");
                        return true;
                    }
                    target = found;
                }
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cThat's not a number.");
                        return true;
                    }
                }
                target.getInventory().addItem(plugin.getCrystalItems().create(amount));
                player.sendMessage("§aGave " + amount + " Fractured Crystal(s) to " + target.getName() + ".");
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage("§7/kairos summon");
        player.sendMessage("§7/kairos status");
        player.sendMessage("§7/kairos forcetier <count> §8(admin, testing only)");
        player.sendMessage("§7/kairos armtrueending §8(admin, testing only -- normally automatic)");
        player.sendMessage("§7/kairos givecrystal [player] [amount] §8(admin)");
    }
}
