package com.nexus.serverrules.detection;

/**
 * EXACT   - normalized text contains the root term with zero edit distance.
 * FUZZY   - matched within the allowed edit-distance tolerance (catches
 *           missing letters, doubled letters, single-char substitutions).
 *
 * Per the user's decision, both tiers currently auto-punish (config
 * "strict-mode: true" default) rather than only flagging FUZZY hits
 * for review - but the distinction is preserved everywhere (logs, GUI,
 * punishment reason) so staff can see which tier triggered and staff
 * can flip strict-mode off later without any code changes.
 */
public enum MatchConfidence {
    EXACT,
    FUZZY
}
