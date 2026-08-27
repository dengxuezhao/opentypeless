package com.opentypeless.android.keyboard.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class ClipboardHistoryTest {
    @Test
    public void recordIsBoundedMostRecentFirstAndMovesDuplicates() {
        ClipboardHistory history = ClipboardHistory.empty()
                .record("first")
                .record("second")
                .record("first");

        assertEquals(List.of("first", "second"), texts(history.entries()));

        for (int index = 0; index < ClipboardHistory.MAX_ENTRIES + 5; index++) {
            history = history.record("item-" + index);
        }
        assertEquals(ClipboardHistory.MAX_ENTRIES, history.size());
        assertEquals("item-104", history.entries().get(0).text());
        assertEquals("item-5", history.entries().get(99).text());
    }

    @Test
    public void totalCodePointLimitEvictsOldestWithoutTruncatingEntries() {
        String large = "汉".repeat(ClipboardPanelSnapshot.MAX_TEXT_CODE_POINTS);
        // The suffixed entry is 40,001 code points and therefore rejected by the editor bound.
        assertThrows(IllegalArgumentException.class, () -> ClipboardHistory.empty().record(large + "x"));

        ClipboardHistory history = ClipboardHistory.empty()
                .record(large)
                .record("乙".repeat(ClipboardPanelSnapshot.MAX_TEXT_CODE_POINTS))
                .record("丙".repeat(ClipboardPanelSnapshot.MAX_TEXT_CODE_POINTS));
        assertEquals(3, history.size());
        assertEquals(ClipboardHistory.MAX_TOTAL_CODE_POINTS, history.totalCodePoints());
        ClipboardHistory trimmed = history.record("new");
        assertEquals(3, trimmed.size());
        assertEquals("new", trimmed.entries().get(0).text());
        assertTrue(trimmed.totalCodePoints() <= ClipboardHistory.MAX_TOTAL_CODE_POINTS);
    }

    @Test
    public void searchAndCategoriesAreComputedWithoutStoredMetadata() {
        ClipboardHistory history = ClipboardHistory.empty()
                .record("Meeting Notes")
                .record("138 0013 8000")
                .record("https://example.com/path")
                .record("中文内容");

        assertEquals(
                List.of("Meeting Notes"),
                texts(history.filtered("meeting", ClipboardHistory.Category.ALL)));
        assertEquals(
                List.of("https://example.com/path"),
                texts(history.filtered("EXAMPLE", ClipboardHistory.Category.LINK)));
        assertEquals(
                List.of("138 0013 8000"),
                texts(history.filtered("", ClipboardHistory.Category.NUMBER)));
        assertEquals(
                List.of("中文内容"),
                texts(history.filtered("中文", ClipboardHistory.Category.TEXT)));
        assertThrows(IllegalArgumentException.class, () -> history.filtered(
                "x".repeat(ClipboardHistory.MAX_SEARCH_CODE_POINTS + 1),
                ClipboardHistory.Category.ALL));
    }

    @Test
    public void diagnosticsNeverContainEntryBodies() {
        String secret = "private clipboard body";
        ClipboardHistory history = ClipboardHistory.empty().record(secret);

        assertFalse(history.toString().contains(secret));
        assertFalse(history.entries().get(0).toString().contains(secret));
        assertTrue(history.toString().contains("entries=1"));
    }

    private static List<String> texts(List<ClipboardHistory.Entry> entries) {
        return entries.stream().map(ClipboardHistory.Entry::text).toList();
    }
}
