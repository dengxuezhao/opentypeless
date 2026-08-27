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

/** Canonical v1 text codec used inside the authenticated clipboard-history ciphertext. */
public final class ClipboardHistoryCodec {
    public static final int FORMAT_VERSION = 1;
    public static final String MAGIC = "opentypeless-clipboard-history";
    public static final int MAX_ENCODED_PAYLOAD_CHARS = 700_000;

    private ClipboardHistoryCodec() {}

    public static String encode(ClipboardHistory history) {
        Objects.requireNonNull(history, "history");
        StringBuilder encoded = new StringBuilder(MAGIC).append('\n').append(FORMAT_VERSION);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (ClipboardHistory.Entry entry : history.entries()) {
            encoded.append('\n').append(encoder.encodeToString(
                    entry.text().getBytes(StandardCharsets.UTF_8)));
        }
        if (encoded.length() > MAX_ENCODED_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("clipboard payload is too large");
        }
        return encoded.toString();
    }

    public static ClipboardHistory decode(String encoded) {
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
        final int version;
        try {
            version = Integer.parseInt(lines[1]);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid clipboard payload version", invalid);
        }
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported clipboard payload version");
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        List<String> texts = new ArrayList<>(lines.length - 2);
        for (int index = 2; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty() || line.indexOf('=') >= 0) {
                throw new IllegalArgumentException("invalid clipboard entry encoding");
            }
            final byte[] bytes;
            try {
                bytes = decoder.decode(line);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("invalid clipboard entry encoding", invalid);
            }
            if (!encoder.encodeToString(bytes).equals(line)) {
                throw new IllegalArgumentException("non-canonical clipboard entry encoding");
            }
            texts.add(strictUtf8(bytes));
        }
        ClipboardHistory history = ClipboardHistory.fromNewestFirst(texts);
        if (!encode(history).equals(encoded)) {
            throw new IllegalArgumentException("non-canonical clipboard payload");
        }
        return history;
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
