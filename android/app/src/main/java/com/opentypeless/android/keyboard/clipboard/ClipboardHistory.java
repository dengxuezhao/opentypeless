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
        Entry previous = null;
        for (Entry entry : entries) {
            if (entry.text().equals(text)) {
                previous = entry;
                break;
            }
        }
        Entry newest = Entry.from(text, previous != null && previous.pinned());
        return withNewestInSection(newest);
    }

    public ClipboardHistory setPinned(String text, boolean pinned) {
        Objects.requireNonNull(text, "text");
        for (Entry entry : entries) {
            if (entry.text().equals(text)) {
                if (entry.pinned() == pinned) return this;
                return withNewestInSection(entry.withPinned(pinned));
            }
        }
        return this;
    }

    public ClipboardHistory delete(String text) {
        Objects.requireNonNull(text, "text");
        ArrayList<Entry> remaining = new ArrayList<>(entries.size());
        int total = 0;
        for (Entry entry : entries) {
            if (entry.text().equals(text)) continue;
            remaining.add(entry);
            total += entry.codePoints();
        }
        if (remaining.size() == entries.size()) return this;
        return new ClipboardHistory(remaining, total);
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
        ArrayList<StoredEntry> entries = new ArrayList<>(texts.size());
        for (String text : texts) entries.add(new StoredEntry(text, false));
        return fromStoredEntries(entries);
    }

    static ClipboardHistory fromStoredEntries(List<StoredEntry> storedEntries) {
        Objects.requireNonNull(storedEntries, "storedEntries");
        if (storedEntries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many clipboard entries");
        }
        Set<String> unique = new HashSet<>();
        ArrayList<Entry> validated = new ArrayList<>(storedEntries.size());
        int total = 0;
        boolean reachedUnpinned = false;
        for (StoredEntry stored : storedEntries) {
            Objects.requireNonNull(stored, "stored clipboard entry");
            if (stored.pinned() && reachedUnpinned) {
                throw new IllegalArgumentException("pinned clipboard entry is out of order");
            }
            reachedUnpinned |= !stored.pinned();
            Entry entry = Entry.from(stored.text(), stored.pinned());
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

    private ClipboardHistory withNewestInSection(Entry newest) {
        ArrayList<Entry> desired = new ArrayList<>(Math.min(MAX_ENTRIES + 1, entries.size() + 1));
        if (newest.pinned()) desired.add(newest);
        for (Entry entry : entries) {
            if (!entry.text().equals(newest.text()) && entry.pinned()) desired.add(entry);
        }
        if (!newest.pinned()) desired.add(newest);
        for (Entry entry : entries) {
            if (!entry.text().equals(newest.text()) && !entry.pinned()) desired.add(entry);
        }

        int total = desired.stream().mapToInt(Entry::codePoints).sum();
        while (desired.size() > MAX_ENTRIES || total > MAX_TOTAL_CODE_POINTS) {
            int removal = oldestEvictionCandidate(desired, newest);
            Entry removed = desired.remove(removal);
            total -= removed.codePoints();
        }
        return new ClipboardHistory(desired, total);
    }

    private static int oldestEvictionCandidate(List<Entry> desired, Entry newest) {
        for (int index = desired.size() - 1; index >= 0; index--) {
            Entry entry = desired.get(index);
            if (entry != newest && !entry.pinned()) return index;
        }
        for (int index = desired.size() - 1; index >= 0; index--) {
            if (desired.get(index) != newest) return index;
        }
        return desired.size() - 1;
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

    static record StoredEntry(String text, boolean pinned) {
        StoredEntry {
            text = Objects.requireNonNull(text, "text");
        }
    }

    public record Entry(String text, Category category, int codePoints, boolean pinned) {
        public Entry {
            text = Objects.requireNonNull(text, "text");
            category = Objects.requireNonNull(category, "category");
            if (text.isEmpty() || category == Category.ALL || codePoints <= 0) {
                throw new IllegalArgumentException("invalid clipboard entry");
            }
        }

        static Entry from(String text, boolean pinned) {
            ClipboardPanelSnapshot snapshot = ClipboardPanelSnapshot.fromPrimaryText(text);
            if (!snapshot.hasText()) {
                throw new IllegalArgumentException("invalid clipboard history text");
            }
            String checked = snapshot.text();
            int codePoints = checked.codePointCount(0, checked.length());
            return new Entry(checked, classify(checked), codePoints, pinned);
        }

        Entry withPinned(boolean nextPinned) {
            return new Entry(text, category, codePoints, nextPinned);
        }

        public String preview(int maximumCodePoints) {
            return ClipboardPanelSnapshot.fromPrimaryText(text).preview(maximumCodePoints);
        }

        @Override
        public String toString() {
            return "Entry{category=" + category + ", codePoints=" + codePoints
                    + ", pinned=" + pinned + '}';
        }
    }
}
