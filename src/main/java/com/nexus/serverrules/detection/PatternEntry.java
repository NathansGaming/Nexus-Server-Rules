package com.nexus.serverrules.detection;

public record PatternEntry(String rootTerm, String normalizedRoot, ViolationCategory category) {
    public static PatternEntry of(String rootTerm, ViolationCategory category) {
        return new PatternEntry(rootTerm, TextNormalizer.normalize(rootTerm), category);
    }
}
