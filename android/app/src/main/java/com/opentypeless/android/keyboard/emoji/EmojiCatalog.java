package com.opentypeless.android.keyboard.emoji;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable Unicode Emoji 15.1 catalog with bounded, offline CLDR keyword search. */
public final class EmojiCatalog {
    public enum Category {
        RECENT,
        SMILEYS,
        PEOPLE,
        ANIMALS,
        FOOD,
        ACTIVITIES,
        TRAVEL,
        OBJECTS,
        SYMBOLS,
        FLAGS
    }

    public static final int MAX_SEARCH_CODE_POINTS = 32;
    public static final int MAX_SEARCH_RESULTS = 240;

    /** One generated catalog row. Names are presentation/search metadata, never diagnostics. */
    public static final class Entry {
        private final String emoji;
        private final String englishName;
        private final String chineseName;
        private final String searchText;

        Entry(String emoji, String englishName, String chineseName, String keywords) {
            this.emoji = requireText(emoji, "emoji");
            this.englishName = requireText(englishName, "englishName");
            this.chineseName = requireText(chineseName, "chineseName");
            String boundedKeywords = requireText(keywords, "keywords");
            if (emoji.codePointCount(0, emoji.length()) > 16
                    || englishName.codePointCount(0, englishName.length()) > 96
                    || chineseName.codePointCount(0, chineseName.length()) > 96
                    || boundedKeywords.codePointCount(0, boundedKeywords.length()) > 768) {
                throw new IllegalStateException("generated Emoji catalog row exceeds bounds");
            }
            searchText = (englishName + " " + chineseName + " " + boundedKeywords)
                    .toLowerCase(Locale.ROOT);
        }

        public String emoji() {
            return emoji;
        }

        public String englishName() {
            return englishName;
        }

        public String chineseName() {
            return chineseName;
        }

        public String localizedName(boolean simplifiedChinese) {
            return simplifiedChinese ? chineseName : englishName;
        }

        boolean matches(String normalizedQuery) {
            return searchText.contains(normalizedQuery);
        }

        @Override
        public String toString() {
            return "EmojiCatalog.Entry{<redacted>}";
        }
    }

    private static final List<Category> BROWSE_CATEGORIES = List.of(
            Category.SMILEYS,
            Category.PEOPLE,
            Category.ANIMALS,
            Category.FOOD,
            Category.ACTIVITIES,
            Category.TRAVEL,
            Category.OBJECTS,
            Category.SYMBOLS,
            Category.FLAGS);
    private static final Map<Category, String> CATEGORY_SEARCH_ALIASES = Map.ofEntries(
            Map.entry(Category.SMILEYS,
                    "smile face emotion happy xiao lian xiaolian biaoqing qingxu 笑脸 表情 情绪"),
            Map.entry(Category.PEOPLE,
                    "people body hand person renwu shoushi shenti 人物 身体 手势"),
            Map.entry(Category.ANIMALS,
                    "animal nature dongwu ziran 动物 自然"),
            Map.entry(Category.FOOD,
                    "food drink shiwu yinliao 食物 饮料"),
            Map.entry(Category.ACTIVITIES,
                    "activity sport huodong yundong 活动 运动"),
            Map.entry(Category.TRAVEL,
                    "travel place transport lvxing didian jiaotong 旅行 地点 交通"),
            Map.entry(Category.OBJECTS,
                    "object tool wupin gongju 物品 工具"),
            Map.entry(Category.SYMBOLS,
                    "symbol heart fuhao xin 符号 爱心"),
            Map.entry(Category.FLAGS,
                    "flag country qizhi guoqi 旗帜 国旗"));
    private static final Map<Category, List<Entry>> INVENTORY = validateInventory(
            EmojiCatalogData.inventory());
    private static final Map<Category, List<String>> GLYPHS = buildGlyphs();
    private static final Map<String, Entry> BY_GLYPH = buildLookup();

    private EmojiCatalog() {}

    public static List<Category> browseCategories() {
        return BROWSE_CATEGORIES;
    }

    public static List<Entry> entries(Category category) {
        Objects.requireNonNull(category, "category");
        if (category == Category.RECENT) return List.of();
        List<Entry> values = INVENTORY.get(category);
        if (values == null) throw new IllegalArgumentException("unknown category");
        return values;
    }

    public static List<String> emoji(Category category) {
        Objects.requireNonNull(category, "category");
        if (category == Category.RECENT) return List.of();
        List<String> values = GLYPHS.get(category);
        if (values == null) throw new IllegalArgumentException("unknown category");
        return values;
    }

    public static Entry find(String emoji) {
        return emoji == null ? null : BY_GLYPH.get(emoji);
    }

    public static boolean contains(String emoji) {
        return find(emoji) != null;
    }

    /** Returns at most 240 deterministic CLDR-order matches; an invalid/empty query returns none. */
    public static List<Entry> search(String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) return List.of();
        ArrayList<Entry> matches = new ArrayList<>();
        for (Category category : BROWSE_CATEGORIES) {
            boolean categoryMatch = CATEGORY_SEARCH_ALIASES.get(category).contains(normalized);
            for (Entry entry : INVENTORY.get(category)) {
                if (categoryMatch || entry.matches(normalized)) {
                    matches.add(entry);
                    if (matches.size() == MAX_SEARCH_RESULTS) return List.copyOf(matches);
                }
            }
        }
        return List.copyOf(matches);
    }

    public static boolean validSearchAppend(String current, String addition) {
        if (current == null || addition == null || addition.isEmpty()) return false;
        String candidate = current + addition;
        return candidate.codePointCount(0, candidate.length()) <= MAX_SEARCH_CODE_POINTS
                && safeSearchText(candidate);
    }

    public static int size() {
        return BY_GLYPH.size();
    }

    public static String unicodeVersion() {
        return EmojiCatalogData.UNICODE_VERSION;
    }

    public static String cldrVersion() {
        return EmojiCatalogData.CLDR_VERSION;
    }

    private static String normalizeQuery(String query) {
        if (query == null || !safeSearchText(query)) return "";
        String trimmed = query.strip();
        if (trimmed.isEmpty()
                || trimmed.codePointCount(0, trimmed.length()) > MAX_SEARCH_CODE_POINTS) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean previousSpace = false;
        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean whitespace = Character.isWhitespace(codePoint);
            if (whitespace) {
                if (!previousSpace) normalized.append(' ');
            } else {
                normalized.appendCodePoint(codePoint);
            }
            previousSpace = whitespace;
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean safeSearchText(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)
                    || codePoint == 0x202A
                    || codePoint == 0x202B
                    || codePoint == 0x202C
                    || codePoint == 0x202D
                    || codePoint == 0x202E
                    || codePoint == 0x2066
                    || codePoint == 0x2067
                    || codePoint == 0x2068
                    || codePoint == 0x2069) {
                return false;
            }
        }
        return true;
    }

    private static Map<Category, List<Entry>> validateInventory(
            Map<Category, List<Entry>> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.keySet().equals(Set.copyOf(BROWSE_CATEGORIES))) {
            throw new IllegalStateException("generated Emoji categories are incomplete");
        }
        HashSet<String> observed = new HashSet<>();
        int count = 0;
        EnumMap<Category, List<Entry>> immutable = new EnumMap<>(Category.class);
        for (Category category : BROWSE_CATEGORIES) {
            List<Entry> entries = List.copyOf(candidate.get(category));
            if (entries.isEmpty()) throw new IllegalStateException("empty Emoji category");
            for (Entry entry : entries) {
                if (!observed.add(entry.emoji())) {
                    throw new IllegalStateException("duplicate Emoji catalog row");
                }
                count++;
            }
            immutable.put(category, entries);
        }
        if (count != EmojiCatalogData.ENTRY_COUNT) {
            throw new IllegalStateException("generated Emoji count mismatch");
        }
        return Map.copyOf(immutable);
    }

    private static Map<Category, List<String>> buildGlyphs() {
        EnumMap<Category, List<String>> values = new EnumMap<>(Category.class);
        for (Category category : BROWSE_CATEGORIES) {
            List<Entry> entries = INVENTORY.get(category);
            ArrayList<String> glyphs = new ArrayList<>(entries.size());
            for (Entry entry : entries) glyphs.add(entry.emoji());
            values.put(category, List.copyOf(glyphs));
        }
        return Map.copyOf(values);
    }

    private static Map<String, Entry> buildLookup() {
        HashMap<String, Entry> values = new HashMap<>();
        for (Category category : BROWSE_CATEGORIES) {
            for (Entry entry : INVENTORY.get(category)) values.put(entry.emoji(), entry);
        }
        return Map.copyOf(values);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("generated Emoji " + field + " is empty");
        }
        return value;
    }
}
