package com.opentypeless.android.keyboard.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ClipboardHistoryStoreInstrumentedTest {
    private Context context;
    private SharedPreferences raw;

    @Before
    public void clearStore() {
        context = ApplicationProvider.getApplicationContext();
        raw = context.getSharedPreferences(ClipboardHistoryStore.STORE, Context.MODE_PRIVATE);
        assertTrue(raw.edit().clear().commit());
    }

    @After
    public void removeStore() {
        assertTrue(raw.edit().clear().commit());
    }

    @Test
    public void realKeystorePersistsMultipleEntriesWithoutPlaintextAtRest() {
        ClipboardHistoryStore first = new ClipboardHistoryStore(context);
        assertTrue(first.loadAndRecord(
                ClipboardPanelSnapshot.fromPrimaryText("first private clip")).persisted());
        ClipboardHistoryStore.Result written = first.loadAndRecord(
                ClipboardPanelSnapshot.fromPrimaryText("second 中文🙂"));

        String encrypted = raw.getString(ClipboardHistoryStore.ENCRYPTED_PAYLOAD, "");
        assertTrue(encrypted.startsWith("opentypeless-encrypted-clipboard:v1:"));
        assertFalse(encrypted.contains("first private clip"));
        assertFalse(encrypted.contains("second 中文🙂"));
        assertEquals(ClipboardHistoryCodec.FORMAT_VERSION,
                raw.getInt(ClipboardHistoryStore.FORMAT_VERSION, -1));

        ClipboardHistoryStore recreated = new ClipboardHistoryStore(context);
        assertEquals(
                List.of("second 中文🙂", "first private clip"),
                recreated.loadOnly().history().entries().stream()
                        .map(ClipboardHistory.Entry::text).toList());
        assertEquals(ClipboardHistoryStore.Status.OK, written.status());
    }

    @Test
    public void corruptV1RecoversButUnknownFutureVersionIsNotOverwritten() {
        assertTrue(raw.edit()
                .putInt(ClipboardHistoryStore.FORMAT_VERSION, ClipboardHistoryCodec.FORMAT_VERSION)
                .putString(ClipboardHistoryStore.ENCRYPTED_PAYLOAD, "plaintext")
                .commit());
        ClipboardHistoryStore store = new ClipboardHistoryStore(context);
        ClipboardHistoryStore.Result recovered = store.loadAndRecord(
                ClipboardPanelSnapshot.fromPrimaryText("fresh"));
        assertEquals(ClipboardHistoryStore.Status.RECOVERED, recovered.status());
        assertTrue(raw.getString(ClipboardHistoryStore.ENCRYPTED_PAYLOAD, "")
                .startsWith("opentypeless-encrypted-clipboard:v1:"));

        assertTrue(raw.edit()
                .putInt(ClipboardHistoryStore.FORMAT_VERSION, 99)
                .putString(ClipboardHistoryStore.ENCRYPTED_PAYLOAD, "future-payload")
                .commit());
        ClipboardHistoryStore.Result future = store.loadAndRecord(
                ClipboardPanelSnapshot.fromPrimaryText("ephemeral"));
        assertEquals(ClipboardHistoryStore.Status.FUTURE_VERSION, future.status());
        assertEquals("future-payload",
                raw.getString(ClipboardHistoryStore.ENCRYPTED_PAYLOAD, ""));
        assertEquals("ephemeral", future.history().entries().get(0).text());
    }

    @Test
    public void clearRequiresExplicitStoreCallAndRemovesBothKeys() {
        ClipboardHistoryStore store = new ClipboardHistoryStore(context);
        store.loadAndRecord(ClipboardPanelSnapshot.fromPrimaryText("temporary"));

        assertTrue(store.clear());
        assertTrue(raw.getAll().isEmpty());
        assertEquals(0, store.loadOnly().history().size());
    }
}
