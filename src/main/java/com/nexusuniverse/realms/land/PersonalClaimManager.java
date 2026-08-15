package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.team.Team;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The "small piece of land inside the country" layer. A personal claim is owned by an individual
 * player, not a team, and only makes sense nested inside territory their own team already
 * claimed (LandClaimManager) -- staking one checks that every chunk the claim's circle touches
 * belongs to the same team the player is a member of, not just the center point, so a personal
 * claim can't spill out into unclaimed or someone else's land.
 */
public class PersonalClaimManager {

    private final JavaPlugin plugin;
    private final LandClaimManager landClaims;
    private final File file;
    private final List<PersonalClaim> claims = new ArrayList<>();

    public PersonalClaimManager(JavaPlugin plugin, LandClaimManager landClaims) {
        this.plugin = plugin;
        this.landClaims = landClaims;
        this.file = new File(plugin.getDataFolder(), "personal-claims.yml");
        load();
    }

    public enum Result { OK, NOT_YOUR_TEAMS_LAND, TOO_MANY, OVERLAPS, NOT_IN_A_TEAM }

    public Result validate(Location center, int radius, UUID playerId, Team team, int maxPerMember) {
        if (team == null) return Result.NOT_IN_A_TEAM;
        if (countFor(playerId) >= maxPerMember) return Result.TOO_MANY;
        if (!fullyWithinTeamTerritory(center, radius, team)) return Result.NOT_YOUR_TEAMS_LAND;

        PersonalClaim candidate = new PersonalClaim(UUID.randomUUID(), playerId, team.id(),
                center.getWorld().getName(), center.getX(), center.getY(), center.getZ(), radius, "");
        for (PersonalClaim existing : claims) {
            if (candidate.overlaps(existing)) return Result.OVERLAPS;
        }
        return Result.OK;
    }

    public PersonalClaim create(Location center, int radius, UUID playerId, Team team, String label) {
        PersonalClaim claim = new PersonalClaim(UUID.randomUUID(), playerId, team.id(),
                center.getWorld().getName(), center.getX(), center.getY(), center.getZ(), radius, label);
        claims.add(claim);
        save();
        return claim;
    }

    public boolean remove(UUID claimId, UUID requesterId) {
        PersonalClaim claim = byId(claimId);
        if (claim == null || !claim.owner().equals(requesterId)) return false;
        claims.remove(claim);
        save();
        return true;
    }

    public PersonalClaim byId(UUID claimId) {
        for (PersonalClaim claim : claims) {
            if (claim.id().equals(claimId)) return claim;
        }
        return null;
    }

    public PersonalClaim claimAt(Location location) {
        for (PersonalClaim claim : claims) {
            if (claim.contains(location.getWorld().getName(), location.getX(), location.getZ())) return claim;
        }
        return null;
    }

    public List<PersonalClaim> claimsOf(UUID playerId) {
        List<PersonalClaim> result = new ArrayList<>();
        for (PersonalClaim claim : claims) {
            if (claim.owner().equals(playerId)) result.add(claim);
        }
        return result;
    }

    public int countFor(UUID playerId) {
        return claimsOf(playerId).size();
    }

    /** Releases every personal claim a team's own disbanding should also clear -- a personal claim doesn't make sense once the country around it no longer exists. */
    public void releaseAllForTeam(UUID teamId) {
        claims.removeIf(claim -> claim.teamId().equals(teamId));
        save();
    }

    private boolean fullyWithinTeamTerritory(Location center, int radius, Team team) {
        int minChunkX = (center.getBlockX() - radius) >> 4;
        int maxChunkX = (center.getBlockX() + radius) >> 4;
        int minChunkZ = (center.getBlockZ() - radius) >> 4;
        int maxChunkZ = (center.getBlockZ() + radius) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var chunk = center.getWorld().getChunkAt(cx, cz);
                UUID owner = landClaims.ownerOf(chunk);
                if (owner == null || !owner.equals(team.id())) return false;
            }
        }
        return true;
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (int i = 0; i < claims.size(); i++) {
            PersonalClaim claim = claims.get(i);
            String path = "claims." + i;
            data.set(path + ".id", claim.id().toString());
            data.set(path + ".owner", claim.owner().toString());
            data.set(path + ".team", claim.teamId().toString());
            data.set(path + ".world", claim.world());
            data.set(path + ".x", claim.centerX());
            data.set(path + ".y", claim.centerY());
            data.set(path + ".z", claim.centerZ());
            data.set(path + ".radius", claim.radius());
            data.set(path + ".label", claim.label());
            java.util.List<String> trustLines = new ArrayList<>();
            for (var entry : claim.trusted().entrySet()) {
                trustLines.add(entry.getKey() + ":" + entry.getValue().name());
            }
            data.set(path + ".trusted", trustLines);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save personal-claims.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("claims");
        if (section == null) return;

        for (String indexKey : section.getKeys(false)) {
            try {
                String path = "claims." + indexKey;
                UUID id = UUID.fromString(data.getString(path + ".id"));
                UUID owner = UUID.fromString(data.getString(path + ".owner"));
                UUID teamId = UUID.fromString(data.getString(path + ".team"));
                String world = data.getString(path + ".world");
                double x = data.getDouble(path + ".x");
                double y = data.getDouble(path + ".y");
                double z = data.getDouble(path + ".z");
                int radius = data.getInt(path + ".radius");
                String label = data.getString(path + ".label", "");
                PersonalClaim claim = new PersonalClaim(id, owner, teamId, world, x, y, z, radius, label);
                for (String trustLine : data.getStringList(path + ".trusted")) {
                    String[] parts = trustLine.split(":");
                    claim.trust(UUID.fromString(parts[0]), ClaimPermission.valueOf(parts[1]));
                }
                claims.add(claim);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped a corrupt personal claim entry (" + indexKey + ")", e);
            }
        }
    }
}
