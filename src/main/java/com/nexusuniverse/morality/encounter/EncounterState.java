package com.nexusuniverse.morality.encounter;

/**
 * Lifecycle of a single survivor encounter. PENDING and HELPING are the
 * only "still open" states - everything else means the encounter is
 * resolved and about to (or already did) get cleaned up.
 */
public enum EncounterState {
    /** Spawned, waiting for a player to do anything at all. */
    PENDING,
    /** A player has offered an item and is expected to stay nearby. */
    HELPING,
    /** Resolved: a player saw it through. Karma reward applied. */
    HELPED,
    /** Resolved: a player attacked the survivor instead. Karma penalty applied. */
    LOOTED,
    /** Resolved: nobody did anything before despawn-seconds ran out, or it died some other way. */
    EXPIRED
}
