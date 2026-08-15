package com.nexusuniverse.realms.land;

/**
 * One rung on the chunk-cap upgrade ladder -- reaching this many chunks costs exactly this much,
 * paid as a flat one-time price (not a delta from wherever the team's cap currently sits). A
 * team can jump straight to any tier above its current cap; it doesn't have to buy every rung on
 * the way up.
 */
public record ChunkUpgradeTier(int chunks, double price) {
}
