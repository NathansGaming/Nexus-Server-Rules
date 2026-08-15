package com.nexusuniverse.realms.land;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin bypass is ON automatically for anyone holding nexusrealms.bypass -- no toggle needed to
 * start using it, matching "operators automatically bypass land claims." This class exists only
 * to give an individual op a way to switch it back OFF for themselves temporarily (e.g. while
 * actually building on their own claimed land and not wanting a stray click to touch someone
 * else's plot by mistake) without changing anything permission-wide or affecting other staff.
 *
 * In-memory only, on purpose: this is a momentary "which mode am I in right now" toggle, not a
 * persistent setting, so everyone with the permission is back to bypass-on (the default) after
 * every server restart.
 */
public class AdminBypassManager {

    private final Set<UUID> disabled = new HashSet<>();

    public boolean isBypassing(Player player) {
        if (!player.hasPermission("nexusrealms.bypass")) return false;
        return !disabled.contains(player.getUniqueId());
    }

    /** @return the new state -- true means bypass is now ON for this player. */
    public boolean toggle(UUID playerId) {
        if (disabled.contains(playerId)) {
            disabled.remove(playerId);
            return true;
        }
        disabled.add(playerId);
        return false;
    }
}
