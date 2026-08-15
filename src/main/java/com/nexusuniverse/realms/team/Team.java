package com.nexusuniverse.realms.team;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A team is the "country" -- land claims (see land.LandClaim) belong to a team, not to an
 * individual player, and any current member is treated as "trusted to this country" for the
 * purpose of staking a personal sub-claim inside the team's territory (see
 * land.PersonalClaimManager).
 *
 * Members now carry a TeamRole (LEADER/OFFICER/MEMBER) rather than being a flat set -- rank
 * determines what a member can do at the COUNTRY level (invite, kick, claim/unclaim land, change
 * the settings below). It has no bearing on personal claims, which each have their own
 * independent, individually-granted trust list (see land.PersonalClaim) -- a country's hierarchy
 * and a personal claim's trust list are deliberately two separate layers, not one inherited from
 * the other.
 *
 * The three settings below are this country's own configurable protection rules -- what a
 * non-member is allowed to do passively inside the team's claimed territory, outside of any
 * personal claim. Off (false) by default: a stranger can't do any of these unless an OFFICER+
 * turns it on for this specific team.
 */
public class Team {

    private final UUID id;
    private String name;
    private final Map<UUID, TeamRole> members = new LinkedHashMap<>();

    private boolean allowOutsiderDoors = false;
    private boolean allowOutsiderContainers = false;
    private boolean allowOutsiderPvp = false;

    /**
     * How many chunks this specific team is currently allowed to have claimed at once. Starts at
     * whatever RealmsConfig#landStartingChunks() was when the team was created (set by the
     * caller right after construction, not hardcoded here -- Team itself doesn't know about
     * config) and only ever goes up, via a purchase through LandCommand's /realms upgrade.
     */
    private int chunkCap;

    public Team(UUID id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.members.put(leader, TeamRole.LEADER);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<UUID, TeamRole> members() {
        return members;
    }

    public Set<UUID> memberIds() {
        return members.keySet();
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public TeamRole roleOf(UUID playerId) {
        return members.get(playerId);
    }

    public void setRole(UUID playerId, TeamRole role) {
        members.put(playerId, role);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /** The team's single LEADER, or null if (unusually) none currently holds that rank. */
    public UUID owner() {
        for (Map.Entry<UUID, TeamRole> entry : members.entrySet()) {
            if (entry.getValue() == TeamRole.LEADER) return entry.getKey();
        }
        return null;
    }

    public boolean isOwner(UUID playerId) {
        return playerId.equals(owner());
    }

    /** True if this player is a member AND holds at least the given rank -- the standard permission check for team-level actions. */
    public boolean hasRole(UUID playerId, TeamRole minimum) {
        TeamRole role = roleOf(playerId);
        return role != null && role.atLeast(minimum);
    }

    public boolean allowOutsiderDoors() {
        return allowOutsiderDoors;
    }

    public void setAllowOutsiderDoors(boolean value) {
        this.allowOutsiderDoors = value;
    }

    public boolean allowOutsiderContainers() {
        return allowOutsiderContainers;
    }

    public void setAllowOutsiderContainers(boolean value) {
        this.allowOutsiderContainers = value;
    }

    public boolean allowOutsiderPvp() {
        return allowOutsiderPvp;
    }

    public void setAllowOutsiderPvp(boolean value) {
        this.allowOutsiderPvp = value;
    }

    public int chunkCap() {
        return chunkCap;
    }

    public void setChunkCap(int chunkCap) {
        this.chunkCap = chunkCap;
    }
}
