package com.opentypeless.android.keyboard.clipboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, bounded and content-redacted clipboard history model. */
public final class ClipboardHistory {
    public enum Category { ALL, TEXT, NUMBER, LINK }

    public static final int MAX_ENTRIES = 100;
    public static final int MAX_TOTAL_CODE_POINTS = 120_000;
    public static final int MAX_SEARCH_CODE_POINTS = 64;

    private static final Pattern NUMBER = Pattern.compile(
            "[+\\-]?[0-9０-９][0-9０-９\\s.,，。:_/\\-]*");
    private final List<Entry> entries;
    private final int totalCodePoints;

    private ClipboardHistory(List<Entry> entries, int totalCodePoints) {
        this.entries = List.copyOf(entries);
        this.totalCodePoints = totalCodePoints;
    }

    public static ClipboardHistory empty() {
        return new ClipboardHistory(List.of(), 0);
    }

    public ClipboardHistory record(ClipboardPanelSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.hasText()) return this;
        return record(snapshot.text());
    }

    public ClipboardHistory record(String text) {
        Entry newest = Entry.from(text);
        ArrayList<Entry> updated = new ArrayList<>(Math.min(MAX_ENTRIES, entries.size() + 1));
        updated.add(newest);
        int total = newest.codePoints();
        for (Entry entry : entries) {
            if (entry.text().equals(newest.text())) continue;
            if (updated.size() >= MAX_ENTRIES) break;
            if (total + entry.codePoints() > MAX_TOTAL_CODE_POINTS) continue;
            updated.add(entry);
            total += entry.codePoints();
        }
        return new ClipboardHistory(updated, total);
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public int totalCodePoints() {
        return totalCodePoints;
    }

    public List<Entry> filtered(String query, Category category) {
        Category selected = Objects.requireNonNull(category, "category");
        String needle = normalizedQuery(query);
        ArrayList<Entry> filtered = new ArrayList<>();
        for (Entry entry : entries) {
            if (selected != Category.ALL && entry.category() != selected) continue;
            if (!needle.isEmpty()
                    && !entry.text().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            filtered.add(entry);
        }
        return List.copyOf(filtered);
    }

    static ClipboardHistory fromNewestFirst(List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (texts.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many clipboard entries");
        }
        Set<String> unique = new HashSet<>();
        ArrayList<Entry> validated = new ArrayList<>(texts.size());
        int total = 0;
        for (String text : texts) {
            Entry entry = Entry.from(text);
            if (!unique.add(entry.text())) {
                throw new IllegalArgumentException("duplicate clipboard entry");
            }
            total += entry.codePoints();
            if (total > MAX_TOTAL_CODE_POINTS) {
                throw new IllegalArgumentException("clipboard history is too large");
            }
            validated.add(entry);
        }
        return new ClipboardHistory(validated, total);
    }

    private static String normalizedQuery(String query) {
        String value = query == null ? "" : query;
        ClipboardPanelSnapshot checked = ClipboardPanelSnapshot.fromPrimaryText(value);
        if (value.isEmpty()) return "";
        if (!checked.hasText()
                || value.codePointCount(0, value.length()) > MAX_SEARCH_CODE_POINTS) {
            throw new IllegalArgumentException("invalid clipboard search query");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static Category classify(String text) {
        String value = text.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")
                || lower.startsWith("http://")
                || lower.startsWith("www.")) {
            return Category.LINK;
        }
        if (NUMBER.matcher(value).matches()) return Category.NUMBER;
        return Category.TEXT;
    }

    @Override
    public String toString() {
        return "ClipboardHistory{entries=" + entries.size()
                + ", totalCodePoints=" + totalCodePoints + '}';
    }

    public record Entry(String text, Category category, int codePoints) {
        public Entry {
            text = Objects.requireNonNull(text, "text");
            category = Objects.requireNonNull(category, "category");
            if (text.isEmpty() || category == Category.ALL || codePoints <= 0) {
                throw new IllegalArgumentException("invalid clipboard entry");
            }
        }

        static Entry from(String text) {
            ClipboardPanelSnapshot snapshot = ClipboardPanelSnapshot.fromPrimaryText(text);
            if (!snapshot.hasText()) {
                throw new IllegalArgumentException("invalid clipboard history text");
            }
            String checked = snapshot.text();
            int codePoints = checked.codePointCount(0, checked.length());
            return new Entry(checked, classify(checked), codePoints);
        }

        public String preview(int maximumCodePoints) {
            return ClipboardPanelSnapshot.fromPrimaryText(text).preview(maximumCodePoints);
        }

        @Override
        public String toString() {
            return "Entry{category=" + category + ", codePoints=" + codePoints + '}';
        }
    }
}
