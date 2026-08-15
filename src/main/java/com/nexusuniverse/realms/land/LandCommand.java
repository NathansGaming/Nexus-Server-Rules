package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.RealmsConfig;
import com.nexusuniverse.realms.economy.VaultHook;
import com.nexusuniverse.realms.hints.HintManager;
import com.nexusuniverse.realms.team.Team;
import com.nexusuniverse.realms.team.TeamManager;
import com.nexusuniverse.realms.team.TeamRole;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LandCommand implements CommandExecutor {

    private final TeamManager teams;
    private final LandClaimManager land;
    private final RealmsConfig config;
    private final HintManager hints;
    private final VaultHook economy;
    private final AdminBypassManager bypass;

    public LandCommand(TeamManager teams, LandClaimManager land, RealmsConfig config, HintManager hints, VaultHook economy, AdminBypassManager bypass) {
        this.teams = teams;
        this.land = land;
        this.config = config;
        this.hints = hints;
        this.economy = economy;
        this.bypass = bypass;
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
            case "claim" -> handleClaim(player, args);
            case "unclaim" -> handleUnclaim(player, args);
            case "info" -> handleInfo(player);
            case "tiers" -> handleTiers(player);
            case "upgrade" -> handleUpgrade(player, args);
            case "openterrain" -> handleOpenTerrain(player, args);
            case "bypass" -> handleBypassToggle(player);
            case "admin" -> handleAdmin(player, args);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /realms <claim [radius <radius>]|unclaim [all]|info|tiers|upgrade <chunks>"
                + "|openterrain <build|containers|doors|pvp|elytra> <on|off>|bypass|admin <claim|unclaim> <team> <radius>>");
    }

    private void handleClaim(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("radius")) {
            handleClaimRadius(player, args);
            return;
        }

        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You need to be in a team to claim land -- /team create <name> first.");
            return;
        }
        if (!team.hasRole(player.getUniqueId(), TeamRole.OFFICER)) {
            player.sendMessage(ChatColor.RED + "You need to be at least an officer to claim land for your team.");
            return;
        }
        if (land.chunkCountFor(team.id()) >= team.chunkCap()) {
            player.sendMessage(ChatColor.RED + team.name() + " has hit its claim limit (" + team.chunkCap()
                    + " chunks) -- an officer can buy more with /realms upgrade, or /realms tiers to see options.");
            return;
        }

        var chunk = player.getLocation().getChunk();
        if (!land.claim(chunk, team)) {
            player.sendMessage(ChatColor.RED + "This chunk is already claimed.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Claimed this chunk for " + team.name() + ".");
        hints.sendContextual(player, "land_claim");
    }

    /**
     * /realms claim radius <radius> -- the player-facing equivalent of /realms admin claim: bulk-claims
     * a square of chunks centered on the sender, one chunk at a time, for their OWN team. Same
     * OFFICER+ requirement as a normal single-chunk claim. Deliberately does NOT auto-raise the team's
     * chunk cap the way the admin version does -- stops at whatever cap the team currently has (same
     * as a normal claim would) so this can't be used to skip the paid /realms upgrade tiers, and is
     * additionally capped at config land.player-radius-claim-max-chunks per run regardless of cap
     * headroom, so one command can't claim an unreasonably large area even for a team with a huge cap.
     */
    private void handleClaimRadius(Player player, String[] args) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You need to be in a team to claim land -- /team create <name> first.");
            return;
        }
        if (!team.hasRole(player.getUniqueId(), TeamRole.OFFICER)) {
            player.sendMessage(ChatColor.RED + "You need to be at least an officer to claim land for your team.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /realms claim radius <radius>");
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Radius must be a whole number of chunks.");
            return;
        }
        int maxSearchRadius = config.landPlayerRadiusClaimMaxSearchRadius();
        if (radius < 0 || radius > maxSearchRadius) {
            player.sendMessage(ChatColor.RED + "Radius must be between 0 and " + maxSearchRadius + " chunks.");
            return;
        }

        int maxChunksPerRun = config.landPlayerRadiusClaimMaxChunks();
        var center = player.getLocation().getChunk();
        int claimed = 0;
        int skippedTaken = 0;
        boolean stoppedForCap = false;
        boolean stoppedForLimit = false;

        outer:
        for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
            for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                if (claimed >= maxChunksPerRun) {
                    stoppedForLimit = true;
                    break outer;
                }
                if (land.chunkCountFor(team.id()) >= team.chunkCap()) {
                    stoppedForCap = true;
                    break outer;
                }
                var chunk = player.getWorld().getChunkAt(cx, cz);
                if (land.claim(chunk, team)) {
                    claimed++;
                } else {
                    skippedTaken++;
                }
            }
        }

        StringBuilder msg = new StringBuilder(ChatColor.AQUA + "Claimed " + claimed + " chunk(s) for " + team.name() + ".");
        if (skippedTaken > 0) {
            msg.append(ChatColor.GRAY).append(" (").append(skippedTaken).append(" already claimed by someone, skipped)");
        }
        if (stoppedForCap) {
            msg.append(ChatColor.GRAY).append(" Stopped early -- hit your team's ").append(team.chunkCap())
                    .append("-chunk cap. An officer can buy more with /realms upgrade.");
        } else if (stoppedForLimit) {
            msg.append(ChatColor.GRAY).append(" Hit the ").append(maxChunksPerRun)
                    .append("-chunk limit for one radius claim -- run it again to keep going.");
        }
        player.sendMessage(msg.toString());
        if (claimed > 0) hints.sendContextual(player, "land_claim");
    }

    private void handleUnclaim(Player player, String[] args) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        if (!team.hasRole(player.getUniqueId(), TeamRole.OFFICER)) {
            player.sendMessage(ChatColor.RED + "You need to be at least an officer to unclaim land.");
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            int unclaimed = land.unclaimAll(team.id());
            if (unclaimed == 0) {
                player.sendMessage(ChatColor.RED + team.name() + " doesn't have any claimed land.");
                return;
            }
            player.sendMessage(ChatColor.AQUA + "Unclaimed all " + unclaimed + " chunk(s) belonging to " + team.name() + ".");
            return;
        }

        var chunk = player.getLocation().getChunk();
        if (!land.unclaim(chunk, team)) {
            player.sendMessage(ChatColor.RED + "This chunk isn't claimed by your team.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Unclaimed this chunk.");
    }

    private void handleInfo(Player player) {
        var chunk = player.getLocation().getChunk();
        java.util.UUID ownerId = land.ownerOf(chunk);
        if (ownerId == null) {
            player.sendMessage(ChatColor.GRAY + "This chunk is unclaimed.");
            return;
        }
        Team team = teams.byId(ownerId);
        player.sendMessage(ChatColor.GRAY + "This chunk belongs to " + ChatColor.WHITE
                + (team != null ? team.name() : "an unknown team") + ChatColor.GRAY + ".");
        if (team != null) {
            player.sendMessage(ChatColor.GRAY + "Claimed: " + ChatColor.WHITE + land.chunkCountFor(team.id())
                    + " / " + team.chunkCap() + ChatColor.GRAY + " chunks.");
        }
    }

    private void handleTiers(Player player) {
        Team team = teams.teamOf(player.getUniqueId());
        boolean opTier = player.hasPermission("nexusrealms.chunks.optier");
        List<ChunkUpgradeTier> tiers = opTier ? config.landUpgradeTiersOp() : config.landUpgradeTiersNormal();
        int ceiling = opTier ? config.maxChunksPerTeamOp() : config.maxChunksPerTeam();

        player.sendMessage(ChatColor.GRAY + "--- Chunk Cap Upgrades" + (opTier ? " (op pricing)" : "") + " ---");
        if (team != null) {
            player.sendMessage(ChatColor.GRAY + "Your team's current cap: " + ChatColor.WHITE + team.chunkCap() + " chunks");
        }
        for (ChunkUpgradeTier tier : tiers) {
            if (ceiling > 0 && tier.chunks() > ceiling) continue;
            boolean owned = team != null && team.chunkCap() >= tier.chunks();
            player.sendMessage((owned ? ChatColor.DARK_GRAY : ChatColor.YELLOW) + "" + tier.chunks() + " chunks"
                    + ChatColor.GRAY + " -- " + (owned ? "owned" : ChatColor.GOLD + economy.format(tier.price())));
        }
        player.sendMessage(ChatColor.GRAY + "Buy with " + ChatColor.WHITE + "/realms upgrade <chunks>" + ChatColor.GRAY + ".");
    }

    private void handleUpgrade(Player player, String[] args) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        if (!team.hasRole(player.getUniqueId(), TeamRole.OFFICER)) {
            player.sendMessage(ChatColor.RED + "You need to be at least an officer to upgrade your team's chunk cap.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /realms upgrade <chunks> -- see /realms tiers for options.");
            return;
        }

        int requestedChunks;
        try {
            requestedChunks = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Chunks must be a whole number -- see /realms tiers for valid values.");
            return;
        }

        boolean opTier = player.hasPermission("nexusrealms.chunks.optier");
        List<ChunkUpgradeTier> tiers = opTier ? config.landUpgradeTiersOp() : config.landUpgradeTiersNormal();
        int ceiling = opTier ? config.maxChunksPerTeamOp() : config.maxChunksPerTeam();

        if (requestedChunks <= team.chunkCap()) {
            player.sendMessage(ChatColor.RED + team.name() + " already has a cap of " + team.chunkCap() + " chunks or higher.");
            return;
        }
        if (ceiling > 0 && requestedChunks > ceiling) {
            player.sendMessage(ChatColor.RED + "The maximum chunk cap" + (opTier ? " (even with op pricing)" : "")
                    + " is " + ceiling + " chunks.");
            return;
        }

        ChunkUpgradeTier tier = findTier(tiers, requestedChunks);
        if (tier == null) {
            player.sendMessage(ChatColor.RED + "" + requestedChunks + " isn't a valid tier -- see /realms tiers for options.");
            return;
        }

        if (!economy.isReady()) {
            player.sendMessage(ChatColor.RED + "No economy plugin is available -- chunk upgrades can't be purchased right now.");
            return;
        }
        if (!economy.withdraw(player, tier.price())) {
            player.sendMessage(ChatColor.RED + "You don't have enough money. Cost: " + ChatColor.GOLD + economy.format(tier.price()));
            return;
        }

        team.setChunkCap(tier.chunks());
        teams.save();
        player.sendMessage(ChatColor.AQUA + team.name() + "'s chunk cap is now " + tier.chunks() + "! Paid "
                + economy.format(tier.price()) + ".");
    }

    private ChunkUpgradeTier findTier(List<ChunkUpgradeTier> tiers, int chunks) {
        for (ChunkUpgradeTier tier : tiers) {
            if (tier.chunks() == chunks) return tier;
        }
        return null;
    }

    private void handleOpenTerrain(Player player, String[] args) {
        if (!player.hasPermission("nexusrealms.openterrain.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to change open terrain settings.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /realms openterrain <build|containers|doors|pvp|elytra> <on|off>");
            return;
        }
        String setting = args[1].toLowerCase();
        if (!setting.equals("build") && !setting.equals("containers") && !setting.equals("doors")
                && !setting.equals("pvp") && !setting.equals("elytra")) {
            player.sendMessage(ChatColor.RED + "Usage: /realms openterrain <build|containers|doors|pvp|elytra> <on|off>");
            return;
        }
        boolean value = args[2].equalsIgnoreCase("on");
        config.setOpenTerrainSetting("allow-" + setting, value);
        player.sendMessage(ChatColor.AQUA + "Open terrain " + setting + " is now " + (value ? "allowed" : "blocked") + ".");
    }

    private void handleBypassToggle(Player player) {
        if (!player.hasPermission("nexusrealms.bypass")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        boolean nowBypassing = bypass.toggle(player.getUniqueId());
        player.sendMessage(nowBypassing
                ? ChatColor.AQUA + "Admin bypass is now ON -- you can build, use containers/doors, and fight anywhere, on anyone's land."
                : ChatColor.AQUA + "Admin bypass is now OFF -- you're subject to land protections like a normal player again.");
    }

    /** /realms admin <claim|unclaim> <team> <radius> -- bulk-claim or -unclaim a square of chunks centered on the sender, for a named team. */
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("nexusrealms.land.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /realms admin <claim|unclaim> <team> <radius>");
            return;
        }

        Team team = teams.byName(args[2]);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "No team named \"" + args[2] + "\" -- create it first with /team create.");
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Radius must be a whole number of chunks.");
            return;
        }
        int maxRadius = config.landAdminBulkClaimMaxRadius();
        if (radius < 0 || radius > maxRadius) {
            player.sendMessage(ChatColor.RED + "Radius must be between 0 and " + maxRadius + " chunks.");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "claim" -> handleAdminClaim(player, team, radius);
            case "unclaim" -> handleAdminUnclaim(player, team, radius);
            default -> player.sendMessage(ChatColor.RED + "Usage: /realms admin <claim|unclaim> <team> <radius>");
        }
    }

    /** Claims every not-already-claimed chunk in a (2*radius+1) square centered on the sender's current chunk. Auto-raises the team's chunk cap if the result exceeds it, since an admin pre-building a country's territory shouldn't be blocked by that team's default starting cap. */
    private void handleAdminClaim(Player player, Team team, int radius) {
        var center = player.getLocation().getChunk();
        int claimed = 0;
        int skipped = 0;
        for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
            for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                var chunk = player.getWorld().getChunkAt(cx, cz);
                if (land.claim(chunk, team)) {
                    claimed++;
                } else {
                    skipped++;
                }
            }
        }

        int total = land.chunkCountFor(team.id());
        if (total > team.chunkCap()) {
            team.setChunkCap(total);
            teams.save();
        }

        player.sendMessage(ChatColor.AQUA + "Claimed " + claimed + " chunk(s) for " + team.name()
                + (skipped > 0 ? " (" + skipped + " already claimed by someone, skipped)" : "") + ". "
                + team.name() + " now holds " + total + " / " + team.chunkCap() + " chunks.");
    }

    /** Unclaims every chunk in the square that actually belongs to this team -- chunks claimed by anyone else are left untouched. */
    private void handleAdminUnclaim(Player player, Team team, int radius) {
        var center = player.getLocation().getChunk();
        int unclaimed = 0;
        for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
            for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                var chunk = player.getWorld().getChunkAt(cx, cz);
                if (land.unclaim(chunk, team)) {
                    unclaimed++;
                }
            }
        }
        player.sendMessage(ChatColor.AQUA + "Unclaimed " + unclaimed + " chunk(s) that belonged to " + team.name() + ".");
    }
}
