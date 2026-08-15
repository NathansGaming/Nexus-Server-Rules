package com.nexus.serverrules.detection;

import java.time.Instant;
import java.util.UUID;

public record ViolationResult(
        UUID playerId,
        String playerName,
        String rawMessage,
        String normalizedMessage,
        String matchedRoot,
        ViolationCategory category,
        MatchConfidence confidence,
        Instant timestamp
) {
    public String shortReason() {
        return category.name() + " (" + confidence.name() + ", root=\"" + matchedRoot + "\")";
    }
}
