package com.nexusuniverse.realms.protect;

import org.bukkit.Location;

/**
 * Every field except sinceMillis is optional (null = don't filter on it) -- "rollback everything
 * anyone did in the last hour, anywhere" is a valid (if drastic) filter, same as "rollback just
 * this one player's actions within 10 blocks of here in the last 20 minutes."
 */
public record RollbackFilter(
        String playerName,        // null = any player
        Location center,          // null = no location filter
        double radius,            // only used if center != null
        long sinceMillis          // required -- only changes at or after this timestamp are eligible
) {
    public static RollbackFilter of(String playerName, Location center, double radius, long sinceMillis) {
        return new RollbackFilter(playerName, center, radius, sinceMillis);
    }
}
