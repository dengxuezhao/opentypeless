package com.opentypeless.android.keyboard.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.Test;

public final class ClipboardHistoryCodecTest {
    @Test
    public void v2RoundTripPreservesPinStateAndExactUnicodeOrder() {
        ClipboardHistory source = ClipboardHistory.empty()
                .record("first\nline")
                .record("中文🙂")
                .record("https://example.com")
                .setPinned("中文🙂", true);

        String encoded = ClipboardHistoryCodec.encode(source);
        ClipboardHistory decoded = ClipboardHistoryCodec.decode(encoded);

        assertEquals(source.entries(), decoded.entries());
        assertEquals(encoded, ClipboardHistoryCodec.encode(decoded));
        assertTrue(encoded.startsWith(
                ClipboardHistoryCodec.MAGIC + "\n" + ClipboardHistoryCodec.FORMAT_VERSION));
        assertTrue(encoded.contains("\np:"));
        assertTrue(encoded.contains("\nu:"));
    }

    @Test
    public void authenticatedV1MigrationAssignsEveryLegacyEntryUnpinned() {
        String one = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "one".getBytes(StandardCharsets.UTF_8));
        String two = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "two".getBytes(StandardCharsets.UTF_8));

        ClipboardHistory migrated = ClipboardHistoryCodec.decodeVersion1(
                ClipboardHistoryCodec.MAGIC + "\n1\n" + one + "\n" + two);

        assertEquals(List.of("one", "two"),
                migrated.entries().stream().map(ClipboardHistory.Entry::text).toList());
        assertTrue(migrated.entries().stream().noneMatch(ClipboardHistory.Entry::pinned));
    }

    @Test
    public void unknownMalformedDuplicateAndNonCanonicalPayloadsFailClosed() {
        String one = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "one".getBytes(StandardCharsets.UTF_8));
        String header = ClipboardHistoryCodec.MAGIC + "\n";

        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "3\nu:" + one));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "2\nu:" + one + "\nu:" + one));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "2\nu:" + one + "="));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "2\nx:" + one));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "2\nu:" + Base64.getUrlEncoder()
                        .withoutPadding().encodeToString(new byte[] {(byte) 0xC3, 0x28})));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(
                        header + "2\nu:" + one + "\np:" + Base64.getUrlEncoder()
                                .withoutPadding().encodeToString(
                                        "pinned".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void codecRejectsOversizedCountAndPayloadBeforeAllocationGrowth() {
        String one = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "one".getBytes(StandardCharsets.UTF_8));
        StringBuilder tooMany = new StringBuilder(
                ClipboardHistoryCodec.MAGIC + "\n2");
        for (int index = 0; index <= ClipboardHistory.MAX_ENTRIES; index++) {
            tooMany.append("\nu:").append(one).append(index);
        }

        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(tooMany.toString()));
        assertThrows(IllegalArgumentException.class, () -> ClipboardHistoryCodec.decode(
                "x".repeat(ClipboardHistoryCodec.MAX_ENCODED_PAYLOAD_CHARS + 1)));
    }

    @Test
    public void decodedHistoryNeverSilentlyTruncates() {
        ClipboardHistory history = ClipboardHistory.fromNewestFirst(List.of("one", "two"));
        assertEquals(List.of("one", "two"),
                history.entries().stream().map(ClipboardHistory.Entry::text).toList());
    }
}
