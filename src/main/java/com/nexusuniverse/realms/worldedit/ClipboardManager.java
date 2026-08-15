package com.nexusuniverse.realms.worldedit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory only, same reasoning as SelectionManager/EditHistoryManager -- a clipboard is a working tool for the current session, not something that needs to survive a restart. */
public class ClipboardManager {

    private final Map<UUID, Clipboard> clipboards = new HashMap<>();

    public void set(UUID playerId, Clipboard clipboard) {
        clipboards.put(playerId, clipboard);
    }

    public Clipboard get(UUID playerId) {
        return clipboards.get(playerId);
    }
}
