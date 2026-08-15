package com.nexusuniverse.survival;

import com.nexusuniverse.survival.disease.Disease;
import com.nexusuniverse.survival.thirst.ThirstItems;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class NexusSurvivalCommand implements CommandExecutor {

    private final NexusSurvivalPlugin plugin;

    public NexusSurvivalCommand(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7Usage: /nexussurvival <give|radiation|status|resetme|removeall|plaguedeaths|cureplayer|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(player, args);
            case "radiation" -> handleRadiation(player, args);
            case "status" -> handleStatus(player);
            case "resetme" -> handleResetMe(player);
            case "removeall" -> handleRemoveAll(player);
            case "plaguedeaths" -> handlePlagueDeaths(player);
            case "cureplayer" -> handleCurePlayer(player, args);
            case "reload" -> handleReload(player);
            default -> player.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    /** Emergency admin tool: force-cures whatever a player currently has, no matching cure item needed. */
    private void handleCurePlayer(Player sender, String[] args) {
        if (!sender.hasPermission("nexussurvival.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexussurvival cureplayer <player>");
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }
        var data = plugin.getPlayerDataManager().get(target);
        if (data.infection == null) {
            sender.sendMessage("§7" + target.getName() + " isn't infected with anything.");
            return;
        }
        Disease disease = data.infection;
        plugin.getDiseaseManager().cure(target, disease);
        sender.sendMessage("§aForce-cured " + target.getName() + " of " + disease.getDisplayName() + ".");
    }

    private void handleReload(Player sender) {
        if (!sender.hasPermission("nexussurvival.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        plugin.getNexusSurvivalConfig().reload();
        sender.sendMessage("§aConfig reloaded -- spawn/infection chances and severity numbers apply immediately.");
    }

    private void handlePlagueDeaths(Player player) {
        var deaths = plugin.getDiseaseManager().getRecentPlagueDeaths();
        if (deaths.isEmpty()) {
            player.sendMessage("§7No recorded plague deaths yet.");
            return;
        }
        player.sendMessage("§7--- Recent Plague Deaths ---");
        for (String entry : deaths) {
            player.sendMessage("§4\u2620 §f" + entry);
        }
    }

    private void handleGive(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nexussurvival give <waterbottle|canteen|rawwater|gasmask|wand|feralsummon|tntsummon|plaguemobsummon|cure> [player]");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "waterbottle" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getThirstItems().createWaterBottle());
                announceGive(player, target, "a Water Bottle");
            }
            case "canteen" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getThirstItems().createCanteen());
                announceGive(player, target, "a Canteen (" + ThirstItems.CANTEEN_MAX_CHARGES + " sips, refillable at any water source)");
            }
            case "rawwater" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getThirstItems().createRawWater());
                announceGive(player, target, "Raw Water -- boil it in a furnace to purify it");
            }
            case "gasmask" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getRadiationItems().createGasMask());
                announceGive(player, target, "a Hazmat Mask");
            }
            case "wand" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getRadiationItems().createWand());
                announceGive(player, target, "a Radiation Zone Wand");
            }
            case "feralsummon" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getFeralZombieManager().createSummonItem());
                announceGive(player, target, "a Feral Zombie Summon");
            }
            case "tntsummon" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getTntZombieManager().createSummonItem());
                announceGive(player, target, "a TNT Zombie Summon");
            }
            case "plaguemobsummon" -> {
                Player target = resolveTarget(player, args, 2);
                if (target == null) return;
                target.getInventory().addItem(plugin.getContagiousMobManager().createSummonItem());
                announceGive(player, target, "a Plague Carrier Summon");
            }
            case "cure" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /nexussurvival give cure <disease> [player]. Options: "
                            + diseaseNames());
                    return;
                }
                Disease disease = parseDisease(args[2]);
                if (disease == null) {
                    player.sendMessage("§cUnknown disease. Options: " + diseaseNames());
                    return;
                }
                Player target = resolveTarget(player, args, 3);
                if (target == null) return;
                target.getInventory().addItem(plugin.getDiseaseItems().createCure(disease));
                announceGive(player, target, "a cure for " + disease.getDisplayName());
            }
            default -> player.sendMessage("§cUnknown item. Options: waterbottle, canteen, rawwater, gasmask, wand, feralsummon, tntsummon, plaguemobsummon, cure");
        }
    }

    /** Resolves the target player from args[index] if present, defaulting to the sender. Sends an error and returns null if a name was given but isn't online. */
    private Player resolveTarget(Player sender, String[] args, int index) {
        if (args.length <= index) return sender;
        Player target = org.bukkit.Bukkit.getPlayerExact(args[index]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[index]);
        }
        return target;
    }

    private void announceGive(Player sender, Player target, String itemDescription) {
        if (target.equals(sender)) {
            sender.sendMessage("§aGiven " + itemDescription + ".");
        } else {
            sender.sendMessage("§aGiven " + itemDescription + " to " + target.getName() + ".");
            target.sendMessage("§aYou received " + itemDescription + " from " + sender.getName() + ".");
        }
    }

    private void handleRadiation(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nexussurvival radiation <wand|create <name>|remove <name>|list>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "wand" -> {
                player.getInventory().addItem(plugin.getRadiationItems().createWand());
                player.sendMessage("§aGiven a Radiation Zone Wand.");
            }
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /nexussurvival radiation create <name>");
                    return;
                }
                plugin.getRadiationManager().createZone(player, args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /nexussurvival radiation remove <name>");
                    return;
                }
                boolean removed = plugin.getRadiationManager().removeZone(args[2]);
                player.sendMessage(removed ? "§aZone removed." : "§cNo zone with that name.");
            }
            case "list" -> {
                var names = plugin.getRadiationManager().listZoneNames();
                player.sendMessage(names.isEmpty() ? "§7No radiation zones defined." : "§7Zones: §f" + names);
            }
            default -> player.sendMessage("§cUnknown radiation subcommand.");
        }
    }

    private static final String[] SEVERITY_LABELS = {"Mild", "Moderate", "Severe", "CRITICAL"};

    private void handleStatus(Player player) {
        var data = plugin.getPlayerDataManager().get(player);
        player.sendMessage("§7--- Survival Status ---");
        player.sendMessage("§bThirst: §f" + (int) data.thirst + "/180");
        player.sendMessage("§aRad-O2: §f" + (int) data.radOxygen + "/20");
        player.sendMessage("§eDirtiness: §f" + (int) data.dirtiness + "/100");
        player.sendMessage(data.infection == null
                ? "§aHealthy -- no infection."
                : "§4Infected: §c" + data.infection.getDisplayName()
                        + " §7(" + SEVERITY_LABELS[data.infectionSeverity] + ")");

        long immuneSeconds = plugin.getDiseaseManager().immunitySecondsRemaining(player);
        if (immuneSeconds > 0) {
            player.sendMessage("§7Immune to new infections for another " + immuneSeconds + "s.");
        }
    }

    private void handleResetMe(Player player) {
        var data = plugin.getPlayerDataManager().get(player);
        data.thirst = 180;
        data.radOxygen = 20;
        data.dirtiness = 0;
        data.infection = null;
        data.infectionSeverity = 0;
        data.severityTickCounter = 0;
        data.immuneUntilTick = 0;
        data.infectionBar.setVisible(false);
        player.sendMessage("§aYour survival stats have been reset.");
    }

    private void handleRemoveAll(Player player) {
        plugin.getPlayerDataManager().clearAll();
        player.sendMessage("§aCleared all tracked survival state. It will regenerate as players are touched again.");
    }

    private Disease parseDisease(String raw) {
        try {
            return Disease.valueOf(raw.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String diseaseNames() {
        return Arrays.stream(Disease.values()).map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
