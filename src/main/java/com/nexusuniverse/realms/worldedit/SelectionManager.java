package com.nexusuniverse.realms.worldedit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory only, on purpose -- a selection is a working tool for the current session, not something that needs to survive a restart. */
public class SelectionManager {

    private final Map<UUID, Selection> selections = new HashMap<>();

    public Selection get(UUID playerId) {
        return selections.computeIfAbsent(playerId, id -> new Selection());
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }
}
