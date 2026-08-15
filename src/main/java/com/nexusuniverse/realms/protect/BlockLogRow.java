package com.nexusuniverse.realms.protect;

public record BlockLogRow(
        long id,
        long timestamp,
        String playerName,
        String world,
        int x, int y, int z,
        String action,
        String oldBlockData,
        String newBlockData
) {
}
