package com.nexus.serverrules.detection;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns raw player chat into a normalized form so obfuscated attempts
 * (missing letters, doubled letters, leetspeak, spacing, punctuation
 * insertion, unicode lookalikes) collapse down to something the
 * pattern matcher can actually catch.
 *
 * This is deliberately aggressive/lossy - the normalized string is only
 * ever used for detection, never shown to players or logged as "what
 * they said" (the raw original is what gets logged/shown to staff).
 */
public final class TextNormalizer {

    // Leetspeak / common substitution map. Longest keys checked first
    // isn't required here since we do char-by-char + a few multi-char passes.
    private static final Map<Character, Character> CHAR_SUBSTITUTIONS = Map.ofEntries(
            Map.entry('0', 'o'),
            Map.entry('1', 'i'),
            Map.entry('!', 'i'),
            Map.entry('|', 'i'),
            Map.entry('3', 'e'),
            Map.entry('4', 'a'),
            Map.entry('@', 'a'),
            Map.entry('5', 's'),
            Map.entry('$', 's'),
            Map.entry('7', 't'),
            Map.entry('+', 't'),
            Map.entry('8', 'b'),
            Map.entry('9', 'g'),
            Map.entry('6', 'g')
    );

    // Strips combining diacritical marks after NFD decomposition.
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    // Any character that isn't a-z or 0-9 gets treated as "noise" and
    // dropped entirely - this is what defeats "n.i.g.g.e.r" / "n i g" /
    // zero-width-space insertion tricks.
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    // Collapses 3+ repeated characters down to 1 (handles "niiiiigger"
    // stretching attempts) while leaving normal doubled letters
    // (e.g. "hello") alone via the matcher's fuzzy stage instead.
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{2,}");

    private TextNormalizer() {}

    /**
     * Full normalization pipeline. Returns a compact string with only
     * lowercase a-z0-9 characters, diacritics stripped, leetspeak
     * mapped to letters, and excessive character stretching collapsed.
     */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";

        String s = input.toLowerCase();

        // Strip unicode diacritics (café -> cafe, catches accented lookalikes)
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");

        // Apply leetspeak/character substitutions
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(CHAR_SUBSTITUTIONS.getOrDefault(c, c));
        }
        s = sb.toString();

        // Drop every non-alphanumeric character (spaces, punctuation,
        // symbols used as separators) - this is the key step that
        // defeats spacing-based obfuscation.
        s = NON_ALNUM.matcher(s).replaceAll("");

        // Collapse stretched-out repeated characters
        s = REPEATED_CHARS.matcher(s).replaceAll("$1");

        return s;
    }
}
