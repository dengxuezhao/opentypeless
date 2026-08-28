package com.opentypeless.android.keyboard.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import com.opentypeless.android.security.LocalClipboardCipher;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Serialized local-I/O adapter for the versioned, encrypted clipboard history. */
public final class ClipboardHistoryStore {
    public enum Status { OK, MIGRATED, RECOVERED, WRITE_FAILED, UNAVAILABLE, FUTURE_VERSION }

    public record Result(ClipboardHistory history, Status status) {
        public Result {
            history = Objects.requireNonNull(history, "history");
            status = Objects.requireNonNull(status, "status");
        }

        public boolean persisted() {
            return status == Status.OK || status == Status.MIGRATED || status == Status.RECOVERED;
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
        if (!write(visible)) return new Result(visible, Status.WRITE_FAILED);
        return new Result(visible, retainedSuccessStatus(loaded.status));
    }

    /** Must run off the IME main thread. */
    public Result loadOnly() {
        Loaded loaded = load();
        return new Result(loaded.history, loaded.status);
    }

    /** Must run off the IME main thread. */
    public Result setPinned(String text, boolean pinned) {
        Objects.requireNonNull(text, "text");
        return mutate(history -> history.setPinned(text, pinned));
    }

    /** Must run off the IME main thread. */
    public Result delete(String text) {
        Objects.requireNonNull(text, "text");
        return mutate(history -> history.delete(text));
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
            if (version != ClipboardHistoryCodec.FORMAT_VERSION
                    && version != ClipboardHistoryCodec.LEGACY_FORMAT_VERSION) {
                return new Loaded(ClipboardHistory.empty(), Status.FUTURE_VERSION);
            }
            String stored = preferences.getString(ENCRYPTED_PAYLOAD, "");
            if (!hasPayload || !cipher.isEncrypted(stored)) return recoverCorrupt();
            String plaintext = cipher.decrypt(stored);
            int payloadVersion = ClipboardHistoryCodec.payloadVersion(plaintext);
            if (payloadVersion > ClipboardHistoryCodec.FORMAT_VERSION) {
                return new Loaded(ClipboardHistory.empty(), Status.FUTURE_VERSION);
            }
            if (payloadVersion != version) return recoverCorrupt();
            if (version == ClipboardHistoryCodec.LEGACY_FORMAT_VERSION) {
                ClipboardHistory migrated = ClipboardHistoryCodec.decodeVersion1(plaintext);
                if (!write(migrated)) return new Loaded(migrated, Status.WRITE_FAILED);
                return new Loaded(migrated, Status.MIGRATED);
            }
            ClipboardHistory history = ClipboardHistoryCodec.decode(plaintext);
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

    private Result mutate(UnaryOperator<ClipboardHistory> mutation) {
        Loaded loaded = load();
        if (loaded.status == Status.FUTURE_VERSION || loaded.status == Status.UNAVAILABLE) {
            return new Result(loaded.history, loaded.status);
        }
        ClipboardHistory updated = Objects.requireNonNull(
                mutation.apply(loaded.history), "clipboard mutation result");
        if (!write(updated)) return new Result(updated, Status.WRITE_FAILED);
        return new Result(updated, retainedSuccessStatus(loaded.status));
    }

    private boolean write(ClipboardHistory history) {
        try {
            String encrypted = cipher.encrypt(ClipboardHistoryCodec.encode(history));
            return preferences.edit()
                    .putInt(FORMAT_VERSION, ClipboardHistoryCodec.FORMAT_VERSION)
                    .putString(ENCRYPTED_PAYLOAD, encrypted)
                    .commit();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static Status retainedSuccessStatus(Status loaded) {
        if (loaded == Status.RECOVERED) return Status.RECOVERED;
        if (loaded == Status.MIGRATED) return Status.MIGRATED;
        return Status.OK;
    }

    private record Loaded(ClipboardHistory history, Status status) {}
}
