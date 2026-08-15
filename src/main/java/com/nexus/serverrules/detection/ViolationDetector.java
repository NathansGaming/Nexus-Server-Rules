package com.nexus.serverrules.detection;

import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a chat message through normalization, then checks it against
 * every loaded pattern using a bounded edit-distance sliding window.
 * This is what catches "missing the letters on the end", doubled
 * letters, and single substitutions ("...er" -> "...a") without
 * needing every variant hand-enumerated in config.
 */
public final class ViolationDetector {

    private final PatternRepository repository;

    // Per user's decision: ambiguous/borderline hits are treated
    // strictly. This is the edit-distance tolerance for "fuzzy" matches -
    // kept small (1 for short-ish roots, 2 for longer ones) so we don't
    // start flagging unrelated words. Tune via config if false-positive
    // rate is too high in practice.
    private static final int SHORT_ROOT_THRESHOLD = 6;

    // Roots at or below this length get NO fuzzy tolerance (exact
    // substring match only, post-normalization). Short common words
    // ("damn", 4 chars) are exactly where fuzzy matching backfires -
    // there are too many innocent words one edit away (e.g. "dawn").
    // A short word typed obfuscated ("d4mn") is still caught by the
    // exact check after normalization maps the leetspeak, so this
    // costs little real detection while removing most collateral
    // false positives.
    private static final int MIN_LENGTH_FOR_FUZZY = 5;

    public ViolationDetector(PatternRepository repository) {
        this.repository = repository;
    }

    public List<ViolationResult> scan(Player player, String rawMessage) {
        List<ViolationResult> results = new ArrayList<>();
        String normalized = TextNormalizer.normalize(rawMessage);
        if (normalized.isEmpty()) return results;
        List<String> words = TextNormalizer.normalizeWords(rawMessage);

        for (PatternEntry entry : repository.entries()) {
            MatchConfidence confidence = match(normalized, words, entry.normalizedRoot());
            if (confidence != null) {
                results.add(new ViolationResult(
                        player.getUniqueId(),
                        player.getName(),
                        rawMessage,
                        normalized,
                        entry.rootTerm(),
                        entry.category(),
                        confidence,
                        Instant.now()
                ));
                // One hit per root is enough; keep scanning other roots
                // in case a message trips multiple categories at once.
            }
        }
        return results;
    }

    /**
     * EXACT is checked against the fully flattened message (spaces and
     * punctuation stripped) - see TextNormalizer#normalize. FUZZY is
     * checked one real word at a time (see TextNormalizer#normalizeWords)
     * so the edit-distance window can never straddle a genuine space and
     * coincidentally match two unrelated adjacent words mashed together
     * (see the "come back" / "wetback" false positive this was fixed
     * for - "eback", the tail of the flattened "comeback", was 2 edits
     * from "wetback" purely by chance).
     */
    private MatchConfidence match(String denseText, List<String> words, String root) {
        int rootLen = root.length();
        if (rootLen == 0) return null;

        if (denseText.contains(root)) {
            return MatchConfidence.EXACT;
        }

        if (rootLen <= MIN_LENGTH_FOR_FUZZY) return null; // exact-only for short roots, see MIN_LENGTH_FOR_FUZZY

        int tolerance = rootLen <= SHORT_ROOT_THRESHOLD ? 1 : 2;

        for (String word : words) {
            if (word.length() < rootLen - tolerance) continue; // can't possibly be within tolerance, skip the scan
            if (fuzzyMatchesWithinWord(word, root, tolerance)) {
                return MatchConfidence.FUZZY;
            }
        }
        return null;
    }

    /** Slides a window across a single real word and checks edit distance against the root. */
    private boolean fuzzyMatchesWithinWord(String word, String root, int tolerance) {
        int rootLen = root.length();
        for (int windowLen = Math.max(1, rootLen - tolerance); windowLen <= rootLen + tolerance; windowLen++) {
            if (windowLen > word.length()) continue;
            for (int start = 0; start + windowLen <= word.length(); start++) {
                String window = word.substring(start, start + windowLen);
                if (levenshtein(window, root) <= tolerance) {
                    return true;
                }
            }
        }
        return false;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
