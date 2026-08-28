package com.opentypeless.android.keyboard.clipboard;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Canonical v2 text codec plus the frozen v1 migration decoder. */
public final class ClipboardHistoryCodec {
    public static final int FORMAT_VERSION = 2;
    static final int LEGACY_FORMAT_VERSION = 1;
    public static final String MAGIC = "opentypeless-clipboard-history";
    public static final int MAX_ENCODED_PAYLOAD_CHARS = 700_000;

    private ClipboardHistoryCodec() {}

    public static String encode(ClipboardHistory history) {
        Objects.requireNonNull(history, "history");
        StringBuilder encoded = new StringBuilder(MAGIC).append('\n').append(FORMAT_VERSION);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (ClipboardHistory.Entry entry : history.entries()) {
            encoded.append('\n').append(entry.pinned() ? "p:" : "u:").append(encoder.encodeToString(
                    entry.text().getBytes(StandardCharsets.UTF_8)));
        }
        if (encoded.length() > MAX_ENCODED_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("clipboard payload is too large");
        }
        return encoded.toString();
    }

    public static ClipboardHistory decode(String encoded) {
        return decodeVersion2(encoded);
    }

    static int payloadVersion(String encoded) {
        if (encoded == null
                || encoded.isEmpty()
                || encoded.length() > MAX_ENCODED_PAYLOAD_CHARS
                || encoded.indexOf('\r') >= 0
                || encoded.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid clipboard payload");
        }
        int firstNewline = encoded.indexOf('\n');
        int secondNewline = firstNewline < 0 ? -1 : encoded.indexOf('\n', firstNewline + 1);
        String magic = firstNewline < 0 ? "" : encoded.substring(0, firstNewline);
        String version = secondNewline < 0
                ? encoded.substring(firstNewline + 1)
                : encoded.substring(firstNewline + 1, secondNewline);
        if (!MAGIC.equals(magic) || version.isEmpty()) {
            throw new IllegalArgumentException("invalid clipboard payload header");
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid clipboard payload version", invalid);
        }
    }

    static ClipboardHistory decodeVersion1(String encoded) {
        String[] lines = checkedLines(encoded, LEGACY_FORMAT_VERSION);
        List<ClipboardHistory.StoredEntry> entries = new ArrayList<>(lines.length - 2);
        for (int index = 2; index < lines.length; index++) {
            entries.add(new ClipboardHistory.StoredEntry(decodeText(lines[index]), false));
        }
        ClipboardHistory history = ClipboardHistory.fromStoredEntries(entries);
        if (!encodeVersion1(history).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical clipboard payload");
        }
        return history;
    }

    private static ClipboardHistory decodeVersion2(String encoded) {
        String[] lines = checkedLines(encoded, FORMAT_VERSION);
        List<ClipboardHistory.StoredEntry> entries = new ArrayList<>(lines.length - 2);
        for (int index = 2; index < lines.length; index++) {
            String line = lines[index];
            if (line.length() < 3 || line.charAt(1) != ':') {
                throw new IllegalArgumentException("invalid clipboard entry marker");
            }
            boolean pinned = switch (line.charAt(0)) {
                case 'p' -> true;
                case 'u' -> false;
                default -> throw new IllegalArgumentException("invalid clipboard entry marker");
            };
            entries.add(new ClipboardHistory.StoredEntry(decodeText(line.substring(2)), pinned));
        }
        ClipboardHistory history = ClipboardHistory.fromStoredEntries(entries);
        if (!encode(history).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical clipboard payload");
        }
        return history;
    }

    private static String[] checkedLines(String encoded, int expectedVersion) {
        if (encoded == null
                || encoded.isEmpty()
                || encoded.length() > MAX_ENCODED_PAYLOAD_CHARS
                || encoded.indexOf('\r') >= 0
                || encoded.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid clipboard payload");
        }
        String[] lines = encoded.split("\\n", -1);
        if (lines.length < 2
                || lines.length > ClipboardHistory.MAX_ENTRIES + 2
                || !MAGIC.equals(lines[0])) {
            throw new IllegalArgumentException("invalid clipboard payload header");
        }
        final int version = payloadVersion(encoded);
        if (version != expectedVersion) {
            throw new IllegalArgumentException("unsupported clipboard payload version");
        }
        return lines;
    }

    private static String decodeText(String line) {
        if (line.isEmpty() || line.indexOf('=') >= 0) {
            throw new IllegalArgumentException("invalid clipboard entry encoding");
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        final byte[] bytes;
        try {
            bytes = decoder.decode(line);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid clipboard entry encoding", invalid);
        }
        if (!encoder.encodeToString(bytes).equals(line)) {
            throw new IllegalArgumentException("non-canonical clipboard entry encoding");
        }
        return strictUtf8(bytes);
    }

    private static String encodeVersion1(ClipboardHistory history) {
        StringBuilder encoded = new StringBuilder(MAGIC).append('\n').append(LEGACY_FORMAT_VERSION);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (ClipboardHistory.Entry entry : history.entries()) {
            if (entry.pinned()) {
                throw new IllegalArgumentException("legacy clipboard entries cannot be pinned");
            }
            encoded.append('\n').append(encoder.encodeToString(
                    entry.text().getBytes(StandardCharsets.UTF_8)));
        }
        return encoded.toString();
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("invalid clipboard entry UTF-8", invalid);
        }
    }
}
