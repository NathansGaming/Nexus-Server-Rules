package com.nexusuniverse.realms.guidebook;

import java.util.ArrayList;
import java.util.List;

/**
 * A Minecraft book page's real hard limit is 798 characters -- but that's not the number that
 * actually matters for "does this get cut off." A page only visually displays about 14 lines of
 * text at default size before the rest of the page's content, while still technically stored,
 * simply isn't shown at all. Cramming a long paragraph into one page string under the 798-char
 * cap can still look cut off in-game, because it never scrolls -- whatever doesn't fit in those
 * 14 lines is just gone from view.
 *
 * This wraps text to a conservative characters-per-line estimate and hard-stops each page at
 * maxLinesPerPage (kept a line under the real visual limit as a safety margin), starting a new
 * page for whatever doesn't fit -- so every page's content is guaranteed to actually be visible,
 * not just technically present in the data.
 */
public final class BookPaginator {

    private static final int DEFAULT_MAX_CHARS_PER_LINE = 18;
    private static final int DEFAULT_MAX_LINES_PER_PAGE = 13;

    private BookPaginator() {
    }

    /** paragraphs -- one entry per logical section/paragraph; a blank line is inserted between them on the page. */
    public static List<String> paginate(List<String> paragraphs) {
        return paginate(paragraphs, DEFAULT_MAX_CHARS_PER_LINE, DEFAULT_MAX_LINES_PER_PAGE);
    }

    public static List<String> paginate(List<String> paragraphs, int maxCharsPerLine, int maxLinesPerPage) {
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();
        int linesOnPage = 0;

        for (String paragraph : paragraphs) {
            for (String line : wordWrap(paragraph, maxCharsPerLine)) {
                if (linesOnPage >= maxLinesPerPage) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                    linesOnPage = 0;
                }
                if (linesOnPage > 0) currentPage.append('\n');
                currentPage.append(line);
                linesOnPage++;
            }

            // a blank separator line between paragraphs, itself subject to the same page limit
            if (linesOnPage >= maxLinesPerPage) {
                pages.add(currentPage.toString());
                currentPage = new StringBuilder();
                linesOnPage = 0;
            } else {
                currentPage.append('\n');
                linesOnPage++;
            }
        }

        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
        return pages;
    }

    private static List<String> wordWrap(String text, int maxCharsPerLine) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxCharsPerLine) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
