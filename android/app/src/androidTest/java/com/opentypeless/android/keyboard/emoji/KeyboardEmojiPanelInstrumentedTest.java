package com.opentypeless.android.keyboard.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class KeyboardEmojiPanelInstrumentedTest {
    @Test
    public void recentCategorySelectsExactMultiCodePointEmojiAndMeetsTouchTargets() {
        onMain(() -> {
            Harness harness = new Harness();
            EmojiRecents recents = EmojiRecents.empty().record("😀").record("🐻‍❄️");
            harness.panel.render(recents, true);

            assertEquals(EmojiCatalog.Category.RECENT, harness.panel.selectedCategory());
            assertEquals(View.VISIBLE,
                    harness.panel.categoryButton(EmojiCatalog.Category.RECENT).getVisibility());
            assertEquals(2, harness.panel.grid().getAdapter().getCount());
            Button first = adapterButton(harness.panel.grid(), 0);
            assertEquals("🐻‍❄️", first.getText().toString());
            assertTrue(first.performClick());
            assertEquals("🐻‍❄️", harness.selected.get());

            int minimum = harness.dp(KeyboardEmojiPanel.MINIMUM_TOUCH_TARGET_DP);
            measure(first, minimum, minimum);
            measure(harness.panel.closeButton(), minimum, minimum);
            measure(harness.panel.searchButton(), minimum, minimum);
            assertTrue(first.getMeasuredHeight() >= minimum);
            assertTrue(harness.panel.closeButton().getMeasuredHeight() >= minimum);
            assertTrue(harness.panel.searchButton().getMeasuredHeight() >= minimum);
        });
    }

    @Test
    public void sensitiveProjectionHidesRecentsButKeepsFullStaticCatalogAndClose() {
        onMain(() -> {
            Harness harness = new Harness();
            harness.panel.render(EmojiRecents.empty().record("😀"), false);

            assertEquals(View.GONE,
                    harness.panel.categoryButton(EmojiCatalog.Category.RECENT).getVisibility());
            assertEquals(EmojiCatalog.Category.SMILEYS, harness.panel.selectedCategory());
            assertEquals(
                    EmojiCatalog.entries(EmojiCatalog.Category.SMILEYS).size(),
                    harness.panel.grid().getAdapter().getCount());
            Button first = adapterButton(harness.panel.grid(), 0);
            assertEquals("😀", first.getText().toString());
            assertTrue(first.performClick());
            assertEquals("😀", harness.selected.get());
            assertEquals(View.VISIBLE,
                    harness.panel.categoryButton(EmojiCatalog.Category.FLAGS).getVisibility());

            assertTrue(harness.panel.closeButton().performClick());
            assertEquals(1, harness.closes.get());
            harness.panel.clear();
            assertEquals(0, harness.panel.grid().getAdapter().getCount());
        });
    }

    @Test
    public void categorySelectionUsesVirtualizedGridAndBottomCategoryRail() {
        onMain(() -> {
            Harness harness = new Harness();
            harness.panel.render(EmojiRecents.empty(), true);

            assertTrue(harness.panel.categoryButton(
                    EmojiCatalog.Category.SYMBOLS).performClick());
            assertEquals(EmojiCatalog.Category.SYMBOLS, harness.panel.selectedCategory());
            assertEquals(
                    EmojiCatalog.entries(EmojiCatalog.Category.SYMBOLS).size(),
                    harness.panel.grid().getAdapter().getCount());
            assertEquals("🏧", adapterButton(harness.panel.grid(), 0).getText().toString());
            View categoryScroller = (View) harness.panel.categoryStrip().getParent();
            assertEquals(
                    harness.panel.root().getChildCount() - 1,
                    harness.panel.root().indexOfChild(categoryScroller));
        });
    }

    @Test
    public void searchEditsOnlyMemoryThenRendersBoundedCatalogMatches() {
        onMain(() -> {
            Harness harness = new Harness();
            harness.panel.render(EmojiRecents.empty(), true);

            assertTrue(harness.panel.searchButton().performClick());
            assertTrue(harness.panel.isSearchEditing());
            assertTrue(harness.searching.get());
            assertTrue(harness.panel.appendSearchText("d"));
            assertTrue(harness.panel.appendSearchText("o"));
            assertTrue(harness.panel.appendSearchText("g"));
            assertEquals("dog", harness.panel.searchQuery());
            assertTrue(harness.panel.searchQueryView().getText().toString().contains("dog"));
            assertTrue(harness.panel.finishSearchEditing());
            assertFalse(harness.panel.isSearchEditing());
            assertFalse(harness.searching.get());
            assertEquals(2, harness.searchChanges.get());
            assertEquals(
                    EmojiCatalog.search("dog").size(),
                    harness.panel.grid().getAdapter().getCount());

            int dog = indexOf(harness.panel.grid(), "🐕");
            assertTrue(dog >= 0);
            assertTrue(adapterButton(harness.panel.grid(), dog).performClick());
            assertEquals("🐕", harness.selected.get());
        });
    }

    private static int indexOf(GridView grid, String emoji) {
        for (int index = 0; index < grid.getAdapter().getCount(); index++) {
            EmojiCatalog.Entry entry = (EmojiCatalog.Entry) grid.getAdapter().getItem(index);
            if (emoji.equals(entry.emoji())) return index;
        }
        return -1;
    }

    private static Button adapterButton(GridView grid, int position) {
        return (Button) grid.getAdapter().getView(position, null, grid);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void measure(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static final class Harness {
        final Context context = ApplicationProvider.getApplicationContext();
        final AtomicReference<String> selected = new AtomicReference<>();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger searchChanges = new AtomicInteger();
        final AtomicBoolean searching = new AtomicBoolean();
        final KeyboardEmojiPanel panel = new KeyboardEmojiPanel(
                context,
                new KeyboardEmojiPanel.Listener() {
                    @Override
                    public void onEmojiSelected(String emoji) {
                        selected.set(emoji);
                    }

                    @Override
                    public void onClose() {
                        closes.incrementAndGet();
                    }

                    @Override
                    public void onSearchEditingChanged(boolean editing) {
                        searching.set(editing);
                        searchChanges.incrementAndGet();
                    }
                });

        int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
