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
    public void v1RoundTripPreservesExactMruUnicodeOrder() {
        ClipboardHistory source = ClipboardHistory.empty()
                .record("first\nline")
                .record("中文🙂")
                .record("https://example.com");

        String encoded = ClipboardHistoryCodec.encode(source);
        ClipboardHistory decoded = ClipboardHistoryCodec.decode(encoded);

        assertEquals(source.entries(), decoded.entries());
        assertEquals(encoded, ClipboardHistoryCodec.encode(decoded));
        assertTrue(encoded.startsWith(
                ClipboardHistoryCodec.MAGIC + "\n" + ClipboardHistoryCodec.FORMAT_VERSION));
    }

    @Test
    public void unknownMalformedDuplicateAndNonCanonicalPayloadsFailClosed() {
        String one = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "one".getBytes(StandardCharsets.UTF_8));
        String header = ClipboardHistoryCodec.MAGIC + "\n";

        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "2\n" + one));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "1\n" + one + "\n" + one));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "1\n" + one + "="));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "1\n!!!"));
        assertThrows(IllegalArgumentException.class,
                () -> ClipboardHistoryCodec.decode(header + "1\n" + Base64.getUrlEncoder()
                        .withoutPadding().encodeToString(new byte[] {(byte) 0xC3, 0x28})));
    }

    @Test
    public void codecRejectsOversizedCountAndPayloadBeforeAllocationGrowth() {
        String one = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "one".getBytes(StandardCharsets.UTF_8));
        StringBuilder tooMany = new StringBuilder(
                ClipboardHistoryCodec.MAGIC + "\n1");
        for (int index = 0; index <= ClipboardHistory.MAX_ENTRIES; index++) {
            tooMany.append('\n').append(one).append(index);
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
