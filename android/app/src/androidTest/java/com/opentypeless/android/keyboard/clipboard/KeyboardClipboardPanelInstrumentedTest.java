package com.opentypeless.android.keyboard.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.opentypeless.android.keyboard.ui.CenteredIconButton;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class KeyboardClipboardPanelInstrumentedTest {
    @Test
    public void cardsRenderMultipleEntriesPasteExactTextAndInvalidateOldViews() {
        onMain(() -> {
            Harness harness = new Harness();
            String longText = "🙂".repeat(140);
            ClipboardHistory history = ClipboardHistory.empty()
                    .record("first")
                    .record(longText)
                    .record("https://example.com/path");

            harness.panel.render(history, ClipboardPanelSnapshot.State.TEXT);

            assertEquals(3, harness.panel.entriesContainer().getChildCount());
            Button newest = (Button) harness.panel.entriesContainer().getChildAt(0);
            Button longCard = (Button) harness.panel.entriesContainer().getChildAt(1);
            assertEquals("https://example.com/path", newest.getText().toString());
            assertTrue(longCard.getText().toString().endsWith("…"));
            assertTrue(longCard.performClick());
            assertEquals(longText, harness.pasted.get());

            Button stale = newest;
            harness.pasted.set(null);
            harness.panel.render(
                    ClipboardHistory.empty().record("replacement"),
                    ClipboardPanelSnapshot.State.TEXT);
            stale.performClick();
            assertNull(harness.pasted.get());
        });
    }

    @Test
    public void categoryAndQwertySearchFilterTheBoundedHistory() {
        onMain(() -> {
            Harness harness = new Harness();
            harness.panel.render(
                    ClipboardHistory.empty()
                            .record("Meeting notes")
                            .record("13800138000")
                            .record("https://example.com/path"),
                    ClipboardPanelSnapshot.State.TEXT);

            assertTrue(harness.panel.categoryButton(
                    ClipboardHistory.Category.LINK).performClick());
            assertEquals(1, harness.panel.entriesContainer().getChildCount());
            assertTrue(harness.panel.categoryButton(
                    ClipboardHistory.Category.ALL).performClick());

            assertTrue(harness.panel.searchButton().performClick());
            assertTrue(harness.panel.isSearchEditing());
            assertEquals(1, harness.searchStarts.get());
            assertTrue(harness.panel.appendSearchText("exam"));
            assertEquals("exam", harness.panel.searchQuery());
            assertTrue(harness.panel.deleteSearchCodePoint());
            assertEquals("exa", harness.panel.searchQuery());
            assertTrue(harness.panel.appendSearchText("m"));

            measure(harness.panel.root(), harness.dp(360), harness.dp(320));
            assertTrue(harness.panel.root().getMeasuredHeight()
                    <= harness.dp(KeyboardClipboardPanel.SEARCH_OVERLAY_HEIGHT_DP));
            assertTrue(harness.panel.finishSearchEditing());
            assertFalse(harness.panel.isSearchEditing());
            assertEquals(1, harness.searchFinishes.get());
            assertEquals(1, harness.panel.entriesContainer().getChildCount());
            assertTrue(((Button) harness.panel.entriesContainer().getChildAt(0))
                    .getText().toString().contains("example"));
        });
    }

    @Test
    public void clearRequiresTwoClicksAndLifecycleDropsEveryBodyAndQuery() {
        onMain(() -> {
            Harness harness = new Harness();
            harness.panel.render(
                    ClipboardHistory.empty().record("temporary body"),
                    ClipboardPanelSnapshot.State.TEXT);

            assertTrue(harness.panel.clearHistoryButton().performClick());
            assertEquals(0, harness.clears.get());
            assertTrue(harness.panel.clearHistoryButton().performClick());
            assertEquals(1, harness.clears.get());

            harness.panel.searchButton().performClick();
            harness.panel.appendSearchText("secret query");
            harness.panel.clear();
            assertEquals("", harness.panel.searchQuery());
            assertEquals(0, harness.panel.entriesContainer().getChildCount());
            assertFalse(harness.panel.isSearchEditing());
        });
    }

    @Test
    public void headerIconsKeepExactCenteredFortyEightDpTargets() {
        onMain(() -> {
            Harness harness = new Harness();
            for (CenteredIconButton button : new CenteredIconButton[] {
                    harness.panel.searchButton(),
                    harness.panel.refreshButton(),
                    harness.panel.clearHistoryButton(),
                    harness.panel.closeButton()
            }) {
                int target = harness.dp(KeyboardClipboardPanel.MINIMUM_TOUCH_TARGET_DP);
                measure(button, target, target);
                assertEquals(target, button.getMeasuredWidth());
                assertEquals(target, button.getMeasuredHeight());
                Rect bounds = button.centeredIconBounds();
                assertTrue(!bounds.isEmpty());
                assertTrue(Math.abs(button.getWidth() / 2 - bounds.centerX()) <= 1);
                assertTrue(Math.abs(button.getHeight() / 2 - bounds.centerY()) <= 1);
            }
            assertTrue(harness.panel.refreshButton().performClick());
            assertTrue(harness.panel.closeButton().performClick());
            assertEquals(1, harness.refreshes.get());
            assertEquals(1, harness.closes.get());
        });
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void measure(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static final class Harness {
        final Context context = ApplicationProvider.getApplicationContext();
        final AtomicReference<String> pasted = new AtomicReference<>();
        final AtomicInteger refreshes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger clears = new AtomicInteger();
        final AtomicInteger searchStarts = new AtomicInteger();
        final AtomicInteger searchFinishes = new AtomicInteger();
        final KeyboardClipboardPanel panel = new KeyboardClipboardPanel(
                context,
                new KeyboardClipboardPanel.Listener() {
                    @Override
                    public void onPaste(String text) {
                        pasted.set(text);
                    }

                    @Override
                    public void onRefresh() {
                        refreshes.incrementAndGet();
                    }

                    @Override
                    public void onClose() {
                        closes.incrementAndGet();
                    }

                    @Override
                    public void onSearchEditingChanged(boolean editing) {
                        if (editing) searchStarts.incrementAndGet();
                        else searchFinishes.incrementAndGet();
                    }

                    @Override
                    public void onClearHistory() {
                        clears.incrementAndGet();
                    }
                });

        int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
