package com.nexusuniverse.morality.encounter;

import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracked state for one spawned survivor NPC. Deliberately mutable and
 * plain (not a record) - state, the current helper, and the proximity
 * hold counter all change over the encounter's lifetime, tracked in
 * EncounterManager's active map keyed by entityId.
 */
public final class SurvivorEncounter {

    public final UUID entityId;
    public final Location spawnLocation;
    public final Instant spawnedAt;

    public EncounterState state = EncounterState.PENDING;

    /** Set once a player offers an item and enters the HELPING state. Cleared if they wander off. */
    public UUID helperPlayerId;

    /** Consecutive seconds the current helper has stayed within help-radius-blocks. */
    public int holdSeconds;

    /** So the "you wandered too far" message only sends once per departure, not once per tick. */
    public boolean warnedAboutDistance;

    public SurvivorEncounter(UUID entityId, Location spawnLocation, Instant spawnedAt) {
        this.entityId = entityId;
        this.spawnLocation = spawnLocation;
        this.spawnedAt = spawnedAt;
    }

    public boolean isResolved() {
        return state == EncounterState.HELPED || state == EncounterState.LOOTED || state == EncounterState.EXPIRED;
    }
}
