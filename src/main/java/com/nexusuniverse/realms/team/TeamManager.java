package com.nexusuniverse.realms.team;

import com.nexusuniverse.realms.RealmsConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TeamManager {

    private final JavaPlugin plugin;
    private final RealmsConfig config;
    private final File file;
    private final Map<UUID, Team> teamsById = new HashMap<>();

    public TeamManager(JavaPlugin plugin, RealmsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    public Team create(String name, UUID owner) {
        Team team = new Team(UUID.randomUUID(), name, owner);
        team.setChunkCap(config.landStartingChunks());
        teamsById.put(team.id(), team);
        save();
        return team;
    }

    public Team byId(UUID id) {
        return teamsById.get(id);
    }

    public Team byName(String name) {
        for (Team team : teamsById.values()) {
            if (team.name().equalsIgnoreCase(name)) return team;
        }
        return null;
    }

    /** The FIRST team this player belongs to -- this version doesn't support multi-team membership; joining a new team leaves whatever team you were already on. */
    public Team teamOf(UUID playerId) {
        for (Team team : teamsById.values()) {
            if (team.isMember(playerId)) return team;
        }
        return null;
    }

    public boolean disband(UUID teamId) {
        boolean removed = teamsById.remove(teamId) != null;
        if (removed) save();
        return removed;
    }

    /**
     * Promotes/demotes a member one rank at a time, refusing to leave a team without a LEADER
     * (demoting the sole LEADER, or promoting someone TO leader, needs the explicit
     * transferLeadership() below instead of this).
     */
    public enum RankChangeResult { OK, NOT_A_MEMBER, ALREADY_AT_LIMIT, USE_TRANSFER_INSTEAD }

    public RankChangeResult promote(Team team, UUID playerId) {
        TeamRole current = team.roleOf(playerId);
        if (current == null) return RankChangeResult.NOT_A_MEMBER;
        if (current == TeamRole.OFFICER) return RankChangeResult.USE_TRANSFER_INSTEAD; // promoting an OFFICER means making them LEADER, a bigger action
        if (current == TeamRole.LEADER) return RankChangeResult.ALREADY_AT_LIMIT;
        team.setRole(playerId, TeamRole.OFFICER);
        save();
        return RankChangeResult.OK;
    }

    public RankChangeResult demote(Team team, UUID playerId) {
        TeamRole current = team.roleOf(playerId);
        if (current == null) return RankChangeResult.NOT_A_MEMBER;
        if (current == TeamRole.LEADER) return RankChangeResult.USE_TRANSFER_INSTEAD; // demoting the sole leader would leave the team without one
        if (current == TeamRole.MEMBER) return RankChangeResult.ALREADY_AT_LIMIT;
        team.setRole(playerId, TeamRole.MEMBER);
        save();
        return RankChangeResult.OK;
    }

    /** Hands leadership to another current member, demoting the previous leader to OFFICER (not kicked -- they stay on the team, just no longer in charge). */
    public boolean transferLeadership(Team team, UUID currentLeaderId, UUID newLeaderId) {
        if (!team.isOwner(currentLeaderId)) return false;
        if (!team.isMember(newLeaderId)) return false;
        team.setRole(currentLeaderId, TeamRole.OFFICER);
        team.setRole(newLeaderId, TeamRole.LEADER);
        save();
        return true;
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Team team : teamsById.values()) {
            String path = "teams." + team.id();
            data.set(path + ".name", team.name());
            java.util.List<String> memberLines = new java.util.ArrayList<>();
            for (Map.Entry<UUID, TeamRole> entry : team.members().entrySet()) {
                memberLines.add(entry.getKey() + ":" + entry.getValue().name());
            }
            data.set(path + ".members", memberLines);
            data.set(path + ".settings.allow-outsider-doors", team.allowOutsiderDoors());
            data.set(path + ".settings.allow-outsider-containers", team.allowOutsiderContainers());
            data.set(path + ".settings.allow-outsider-pvp", team.allowOutsiderPvp());
            data.set(path + ".chunk-cap", team.chunkCap());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save teams.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("teams");
        if (section == null) return;

        for (String idKey : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idKey);
                String name = section.getString(idKey + ".name", "Unnamed");
                java.util.List<String> memberLines = section.getStringList(idKey + ".members");
                if (memberLines.isEmpty()) continue;

                String[] first = memberLines.get(0).split(":");
                Team team = new Team(id, name, UUID.fromString(first[0]));
                team.members().clear();
                for (String line : memberLines) {
                    String[] parts = line.split(":");
                    TeamRole role = parts.length > 1 ? TeamRole.valueOf(parts[1]) : TeamRole.MEMBER;
                    team.setRole(UUID.fromString(parts[0]), role);
                }
                team.setAllowOutsiderDoors(section.getBoolean(idKey + ".settings.allow-outsider-doors", false));
                team.setAllowOutsiderContainers(section.getBoolean(idKey + ".settings.allow-outsider-containers", false));
                team.setAllowOutsiderPvp(section.getBoolean(idKey + ".settings.allow-outsider-pvp", false));
                team.setChunkCap(section.getInt(idKey + ".chunk-cap", config.landStartingChunks()));

                teamsById.put(id, team);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped a corrupt team entry (" + idKey + ") in teams.yml", e);
            }
        }
    }
}
