package com.nexusuniverse.realms.land;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A personal claim's owner can individually trust specific players -- not necessarily members of
 * the same team/country at all -- to VISITOR (open doors/containers) or BUILDER (that, plus
 * building) access, scoped ONLY to this one claim. This is the third, innermost layer: a country
 * (Team) claims a large area; a trusted country member stakes a personal claim inside it; that
 * claim's owner can then trust individual visitors into their own specific plot, independent of
 * whether those visitors are on the country's team at all.
 */
public class PersonalClaim {

    private final UUID id;
    private final UUID owner;
    private final UUID teamId;
    private final String world;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final int radius;
    private String label;
    private final Map<UUID, ClaimPermission> trusted = new LinkedHashMap<>();

    public PersonalClaim(UUID id, UUID owner, UUID teamId, String world, double centerX, double centerY, double centerZ, int radius, String label) {
        this.id = id;
        this.owner = owner;
        this.teamId = teamId;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.label = label;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public UUID teamId() {
        return teamId;
    }

    public String world() {
        return world;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public double centerZ() {
        return centerZ;
    }

    public int radius() {
        return radius;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Map<UUID, ClaimPermission> trusted() {
        return trusted;
    }

    public void trust(UUID playerId, ClaimPermission permission) {
        trusted.put(playerId, permission);
    }

    public void untrust(UUID playerId) {
        trusted.remove(playerId);
    }

    /** True if this player is the owner, or trusted to at least the given permission level. Owner always passes regardless of level. */
    public boolean hasAccess(UUID playerId, ClaimPermission minimum) {
        if (owner.equals(playerId)) return true;
        ClaimPermission granted = trusted.get(playerId);
        return granted != null && granted.atLeast(minimum);
    }

    public boolean contains(String world, double x, double z) {
        if (!this.world.equals(world)) return false;
        double dx = x - centerX;
        double dz = z - centerZ;
        return (dx * dx + dz * dz) <= (double) radius * radius;
    }

    /** True if this claim's bounding circle overlaps another claim's -- used to keep personal claims from stacking on top of each other. */
    public boolean overlaps(PersonalClaim other) {
        if (!world.equals(other.world)) return false;
        double dx = centerX - other.centerX;
        double dz = centerZ - other.centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance < (radius + other.radius);
    }
}
