package com.opentypeless.android.keyboard.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import com.opentypeless.android.security.LocalClipboardCipher;
import java.util.Objects;

/** Serialized local-I/O adapter for the versioned, encrypted clipboard history. */
public final class ClipboardHistoryStore {
    public enum Status { OK, RECOVERED, WRITE_FAILED, UNAVAILABLE, FUTURE_VERSION }

    public record Result(ClipboardHistory history, Status status) {
        public Result {
            history = Objects.requireNonNull(history, "history");
            status = Objects.requireNonNull(status, "status");
        }

        public boolean persisted() {
            return status == Status.OK || status == Status.RECOVERED;
        }
    }

    public static final String STORE = "opentypeless_clipboard_history_v1";
    public static final String FORMAT_VERSION = "format_version";
    public static final String ENCRYPTED_PAYLOAD = "encrypted_payload";

    private final SharedPreferences preferences;
    private final LocalClipboardCipher cipher;

    public ClipboardHistoryStore(Context context) {
        this(
                Objects.requireNonNull(context, "context").getApplicationContext()
                        .getSharedPreferences(STORE, Context.MODE_PRIVATE),
                new LocalClipboardCipher());
    }

    ClipboardHistoryStore(SharedPreferences preferences, LocalClipboardCipher cipher) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    /** Must run off the IME main thread. */
    public Result loadAndRecord(ClipboardPanelSnapshot current) {
        Objects.requireNonNull(current, "current");
        Loaded loaded = load();
        ClipboardHistory visible = loaded.history.record(current);
        if (loaded.status == Status.FUTURE_VERSION || loaded.status == Status.UNAVAILABLE) {
            ClipboardHistory ephemeral = ClipboardHistory.empty().record(current);
            return new Result(ephemeral, loaded.status);
        }
        if (!current.hasText()) return new Result(visible, loaded.status);
        try {
            String payload = ClipboardHistoryCodec.encode(visible);
            String encrypted = cipher.encrypt(payload);
            boolean committed = preferences.edit()
                    .putInt(FORMAT_VERSION, ClipboardHistoryCodec.FORMAT_VERSION)
                    .putString(ENCRYPTED_PAYLOAD, encrypted)
                    .commit();
            if (!committed) return new Result(visible, Status.WRITE_FAILED);
            return new Result(visible,
                    loaded.status == Status.RECOVERED ? Status.RECOVERED : Status.OK);
        } catch (RuntimeException unavailable) {
            return new Result(visible, Status.WRITE_FAILED);
        }
    }

    /** Must run off the IME main thread. */
    public Result loadOnly() {
        Loaded loaded = load();
        return new Result(loaded.history, loaded.status);
    }

    /** Must run off the IME main thread after a second explicit UI confirmation. */
    public boolean clear() {
        try {
            return preferences.edit().clear().commit();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private Loaded load() {
        try {
            boolean hasVersion = preferences.contains(FORMAT_VERSION);
            boolean hasPayload = preferences.contains(ENCRYPTED_PAYLOAD);
            if (!hasVersion && !hasPayload) return new Loaded(ClipboardHistory.empty(), Status.OK);
            int version = preferences.getInt(FORMAT_VERSION, -1);
            if (version != ClipboardHistoryCodec.FORMAT_VERSION) {
                return new Loaded(ClipboardHistory.empty(), Status.FUTURE_VERSION);
            }
            String stored = preferences.getString(ENCRYPTED_PAYLOAD, "");
            if (!hasPayload || !cipher.isEncrypted(stored)) return recoverCorrupt();
            ClipboardHistory history = ClipboardHistoryCodec.decode(cipher.decrypt(stored));
            return new Loaded(history, Status.OK);
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException invalid) {
            return recoverCorrupt();
        } catch (RuntimeException unavailable) {
            return new Loaded(ClipboardHistory.empty(), Status.UNAVAILABLE);
        }
    }

    private Loaded recoverCorrupt() {
        try {
            boolean cleared = preferences.edit().clear().commit();
            return new Loaded(
                    ClipboardHistory.empty(),
                    cleared ? Status.RECOVERED : Status.UNAVAILABLE);
        } catch (RuntimeException unavailable) {
            return new Loaded(ClipboardHistory.empty(), Status.UNAVAILABLE);
        }
    }

    private record Loaded(ClipboardHistory history, Status status) {}
}
