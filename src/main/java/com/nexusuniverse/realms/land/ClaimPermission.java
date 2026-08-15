package com.nexusuniverse.realms.land;

/**
 * VISITOR: can open doors, chests, and other containers/interactables inside the claim, but
 * can't break or place blocks.
 * BUILDER: everything VISITOR can, plus building -- effectively co-owner access to this one
 * specific claim, without making them a team member or giving them access to any of the
 * claim owner's OTHER claims.
 */
public enum ClaimPermission {
    VISITOR(1),
    BUILDER(2);

    private final int rank;

    ClaimPermission(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(ClaimPermission other) {
        return this.rank >= other.rank;
    }
}
