package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.RealmsConfig;
import com.nexusuniverse.realms.hints.HintManager;
import com.nexusuniverse.realms.team.Team;
import com.nexusuniverse.realms.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PersonalClaimCommand implements CommandExecutor {

    private final TeamManager teams;
    private final PersonalClaimManager personalClaims;
    private final RealmsConfig config;
    private final HintManager hints;

    public PersonalClaimCommand(TeamManager teams, PersonalClaimManager personalClaims, RealmsConfig config, HintManager hints) {
        this.teams = teams;
        this.personalClaims = personalClaims;
        this.config = config;
        this.hints = hints;
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
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player);
            case "list" -> handleList(player);
            case "trust" -> handleTrust(player, args);
            case "untrust" -> handleUntrust(player, args);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /pclaim <create [label]|remove|list|trust <player> <visitor|builder>|untrust <player>>");
    }

    private void handleCreate(Player player, String[] args) {
        Team team = teams.teamOf(player.getUniqueId());
        int radius = config.personalClaimRadius();
        int max = config.personalClaimMaxPerMember();

        PersonalClaimManager.Result result = personalClaims.validate(player.getLocation(), radius, player.getUniqueId(), team, max);
        switch (result) {
            case NOT_IN_A_TEAM -> {
                player.sendMessage(ChatColor.RED + "You need to be in a team to stake a personal claim -- join a country first.");
                return;
            }
            case TOO_MANY -> {
                player.sendMessage(ChatColor.RED + "You've already got " + max + " personal claim(s) -- that's your limit.");
                return;
            }
            case NOT_YOUR_TEAMS_LAND -> {
                player.sendMessage(ChatColor.RED + "You have to be standing inside land your own team has already "
                        + "claimed (a " + radius + "-block radius around you needs to be entirely your team's territory).");
                return;
            }
            case OVERLAPS -> {
                player.sendMessage(ChatColor.RED + "That's too close to another personal claim -- try somewhere else in your territory.");
                return;
            }
            case OK -> {
                // fall through
            }
        }

        String claimLabel = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        PersonalClaim claim = personalClaims.create(player.getLocation(), radius, player.getUniqueId(), team, claimLabel);
        player.sendMessage(ChatColor.AQUA + "Claimed a " + radius + "-block radius here"
                + (claimLabel.isBlank() ? "" : " (\"" + claimLabel + "\")") + ".");
        hints.sendContextual(player, "pclaim_create");
    }

    private void handleRemove(Player player) {
        PersonalClaim here = personalClaims.claimAt(player.getLocation());
        if (here == null) {
            player.sendMessage(ChatColor.RED + "You're not standing in a personal claim.");
            return;
        }
        if (!here.owner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "This personal claim isn't yours.");
            return;
        }
        personalClaims.remove(here.id(), player.getUniqueId());
        player.sendMessage(ChatColor.AQUA + "Removed your personal claim here.");
    }

    private void handleList(Player player) {
        List<PersonalClaim> mine = personalClaims.claimsOf(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any personal claims.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "--- Your personal claims (" + mine.size() + "/" + config.personalClaimMaxPerMember() + ") ---");
        for (PersonalClaim claim : mine) {
            String claimLabel = claim.label().isBlank() ? "(unlabeled)" : claim.label();
            player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + claimLabel + ChatColor.GRAY + " in " + claim.world()
                    + " at " + Math.round(claim.centerX()) + ", " + Math.round(claim.centerZ()) + " (radius " + claim.radius() + ")");
        }
    }

    /** Trust/untrust act on whichever personal claim the sender is currently standing in and owns -- not a claim they name, since a player could own several. */
    private void handleTrust(Player player, String[] args) {
        PersonalClaim here = requireOwnedClaimHere(player);
        if (here == null) return;
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /pclaim trust <player> <visitor|builder>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        ClaimPermission permission;
        try {
            permission = ClaimPermission.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Permission must be \"visitor\" or \"builder\".");
            return;
        }
        here.trust(target, permission);
        personalClaims.save();
        player.sendMessage(ChatColor.AQUA + args[1] + " is now trusted as a " + permission.name().toLowerCase() + " in this claim.");
    }

    private void handleUntrust(Player player, String[] args) {
        PersonalClaim here = requireOwnedClaimHere(player);
        if (here == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pclaim untrust <player>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        here.untrust(target);
        personalClaims.save();
        player.sendMessage(ChatColor.AQUA + args[1] + " is no longer trusted in this claim.");
    }

    private PersonalClaim requireOwnedClaimHere(Player player) {
        PersonalClaim here = personalClaims.claimAt(player.getLocation());
        if (here == null) {
            player.sendMessage(ChatColor.RED + "You're not standing in a personal claim.");
            return null;
        }
        if (!here.owner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "This personal claim isn't yours.");
            return null;
        }
        return here;
    }
}
