package com.nexusuniverse.realms.team;

/**
 * LEADER: exactly one per team, the founder (or whoever it's transferred to) -- can do
 * everything OFFICER can, plus disband the team and promote/demote OFFICERs.
 * OFFICER: can invite/kick MEMBERs, claim/unclaim land for the team, and change the team's
 * protection settings -- everything short of disbanding or touching another OFFICER's rank.
 * MEMBER: base access -- counted as "trusted to this country" for staking a personal claim
 * inside the team's territory, but can't do any of the above.
 */
public enum TeamRole {
    LEADER(3),
    OFFICER(2),
    MEMBER(1);

    private final int rank;

    TeamRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(TeamRole other) {
        return this.rank >= other.rank;
    }
}
