package com.opentypeless.android.keyboard.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class EmojiCatalogTest {
    @Test
    public void everyBrowseCategoryIsNonEmptyGloballyUniqueAndGeneratedAtPinnedCount() {
        Set<String> observed = new HashSet<>();

        for (EmojiCatalog.Category category : EmojiCatalog.browseCategories()) {
            assertFalse(EmojiCatalog.emoji(category).isEmpty());
            assertEquals(
                    EmojiCatalog.entries(category).size(),
                    EmojiCatalog.emoji(category).size());
            for (String emoji : EmojiCatalog.emoji(category)) {
                assertTrue(observed.add(emoji));
                assertTrue(EmojiCatalog.contains(emoji));
            }
        }

        assertEquals(9, EmojiCatalog.browseCategories().size());
        assertEquals(1_898, EmojiCatalog.size());
        assertEquals(EmojiCatalog.size(), observed.size());
        assertEquals(269, EmojiCatalog.emoji(EmojiCatalog.Category.FLAGS).size());
        assertEquals("15.1", EmojiCatalog.unicodeVersion());
        assertEquals("45", EmojiCatalog.cldrVersion());
    }

    @Test
    public void englishChineseAndPinyinCategorySearchAreDeterministicAndBounded() {
        List<EmojiCatalog.Entry> dog = EmojiCatalog.search("dog");
        List<EmojiCatalog.Entry> chineseDog = EmojiCatalog.search("狗");
        List<EmojiCatalog.Entry> pinyinCategory = EmojiCatalog.search("dongwu");
        List<EmojiCatalog.Entry> broad = EmojiCatalog.search("a");

        assertTrue(dog.stream().anyMatch(entry -> "🐕".equals(entry.emoji())));
        assertTrue(chineseDog.stream().anyMatch(entry -> "🐕".equals(entry.emoji())));
        assertEquals(EmojiCatalog.entries(EmojiCatalog.Category.ANIMALS), pinyinCategory);
        assertEquals(EmojiCatalog.MAX_SEARCH_RESULTS, broad.size());
        assertEquals(dog, EmojiCatalog.search("  DOG  "));
        assertTrue(dog.get(0).toString().contains("<redacted>"));
        assertFalse(dog.get(0).toString().contains(dog.get(0).emoji()));
    }

    @Test
    public void searchQueryRejectsControlsAndOverlongInput() {
        assertTrue(EmojiCatalog.search("").isEmpty());
        assertTrue(EmojiCatalog.search("dog\n").isEmpty());
        assertTrue(EmojiCatalog.search("x".repeat(33)).isEmpty());
        assertTrue(EmojiCatalog.validSearchAppend("do", "g"));
        assertFalse(EmojiCatalog.validSearchAppend("x".repeat(32), "y"));
        assertFalse(EmojiCatalog.validSearchAppend("dog", "\u202E"));
    }

    @Test
    public void recentIsRuntimeOnlyAndUnknownValuesAreRejected() {
        assertTrue(EmojiCatalog.emoji(EmojiCatalog.Category.RECENT).isEmpty());
        assertTrue(EmojiCatalog.entries(EmojiCatalog.Category.RECENT).isEmpty());
        assertFalse(EmojiCatalog.contains("not emoji"));
        assertFalse(EmojiCatalog.contains(null));
        assertThrows(NullPointerException.class, () -> EmojiCatalog.emoji(null));
        assertThrows(NullPointerException.class, () -> EmojiCatalog.entries(null));
    }
}
