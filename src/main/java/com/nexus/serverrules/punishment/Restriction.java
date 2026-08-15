package com.nexus.serverrules.punishment;

import com.nexus.serverrules.detection.ViolationCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * A currently-active (or historical) restriction on a player. Per the
 * user's decision, restrictions do NOT auto-expire on a timer - they
 * persist across logout/relogin and stay active until a staff member
 * with nexusrules.clear runs /nexusrules clear <player>.
 */
public final class Restriction {

    public final UUID playerId;
    public final String playerName;
    public final ViolationCategory category;
    public final String reason;
    public final String triggeringMessage;
    public final Instant triggeredAt;
    public boolean active;
    public String clearedBy;
    public Instant clearedAt;

    // Previous gamemode, captured at punish-time so it can be restored
    // exactly (rather than assuming SURVIVAL) once staff clear the flag.
    public String previousGameMode;

    public Restriction(UUID playerId, String playerName, ViolationCategory category,
                        String reason, String triggeringMessage, Instant triggeredAt,
                        String previousGameMode) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.category = category;
        this.reason = reason;
        this.triggeringMessage = triggeringMessage;
        this.triggeredAt = triggeredAt;
        this.previousGameMode = previousGameMode;
        this.active = true;
    }
}
