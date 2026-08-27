package com.opentypeless.android.keyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.opentypeless.android.R;
import com.opentypeless.android.keyboard.ui.CenteredIconButton;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class VoiceInputPanelInstrumentedTest {
    @Test
    public void portraitPanelUsesBalancedTypelessHierarchyAndExactCenteredIcons() {
        onMain(() -> {
            Harness harness = new Harness(false);
            measure(harness.panel.root(), harness.dp(360), harness.dp(260));

            assertEquals(LinearLayout.VERTICAL, harness.panel.root().getOrientation());
            int exactContentHeight = harness.dp(4) * 3
                    + harness.dp(8) * 2
                    + harness.dp(48) * 2
                    + harness.dp(32)
                    + harness.dp(100);
            assertEquals(exactContentHeight, harness.panel.root().getMeasuredHeight());
            assertEquals(4, harness.panel.root().getChildCount());
            assertEquals(VoiceInputPanel.ROOT_TAG, harness.panel.root().getTag());
            assertEquals(VoiceInputPanel.BRAND_TAG, harness.panel.brand().getTag());
            assertEquals(VoiceInputPanel.VOICE_TAB_TAG, harness.panel.voiceTab().getTag());
            assertEquals(VoiceInputPanel.LATIN_TAB_TAG, harness.panel.latinTab().getTag());
            assertEquals(VoiceInputPanel.CHINESE_TAB_TAG, harness.panel.chineseTab().getTag());
            assertEquals(VoiceInputPanel.MICROPHONE_TAG,
                    harness.panel.microphoneButton().getTag());
            assertEquals(VoiceInputPanel.DELETE_TAG, harness.panel.deleteButton().getTag());
            assertEquals(VoiceInputPanel.PUNCTUATION_TAG,
                    harness.panel.punctuationButton().getTag());
            assertEquals(VoiceInputPanel.ENTER_TAG, harness.panel.enterButton().getTag());

            assertEquals(harness.dp(168), harness.panel.microphoneButton().getWidth());
            assertEquals(harness.dp(64), harness.panel.microphoneButton().getHeight());
            assertTrue("microphone is not centered in the panel",
                    Math.abs(harness.panel.root().getWidth() / 2
                            - absoluteCenterX(harness.panel.microphoneButton())) <= 1);
            assertCentered(harness.panel.microphoneButton());
            assertCentered(harness.panel.deleteButton());
            for (View action : new View[] {
                    harness.panel.microphoneButton(),
                    harness.panel.deleteButton(),
                    harness.panel.punctuationButton(),
                    harness.panel.enterButton(),
                    harness.panel.voiceTab(),
                    harness.panel.latinTab(),
                    harness.panel.chineseTab()
            }) {
                assertTrue(action.getWidth() >= harness.dp(
                        VoiceInputPanel.MINIMUM_TOUCH_TARGET_DP));
                assertTrue(action.getHeight() >= harness.dp(
                        VoiceInputPanel.MINIMUM_TOUCH_TARGET_DP));
            }

            assertTrue(harness.panel.microphoneButton().performClick());
            assertTrue(harness.panel.deleteButton().performClick());
            assertTrue(harness.panel.punctuationButton().performClick());
            assertTrue(harness.panel.enterButton().performClick());
            assertTrue(harness.panel.latinTab().performClick());
            assertTrue(harness.panel.chineseTab().performClick());
            assertEquals(1, harness.microphone.get());
            assertEquals(1, harness.delete.get());
            assertEquals(1, harness.punctuation.get());
            assertEquals(1, harness.enter.get());
            assertEquals(1, harness.latin.get());
            assertEquals(1, harness.chinese.get());
        });
    }

    @Test
    public void wideLandscapeKeepsAllActionsInsideOneCompactRow() {
        onMain(() -> {
            Harness harness = new Harness(true);
            measure(harness.panel.root(), harness.dp(640), harness.dp(120));

            assertEquals(LinearLayout.HORIZONTAL, harness.panel.root().getOrientation());
            assertEquals(2, harness.panel.root().getChildCount());
            assertTrue(harness.panel.root().getMeasuredHeight() <= harness.dp(96));
            for (View action : new View[] {
                    harness.panel.microphoneButton(),
                    harness.panel.deleteButton(),
                    harness.panel.punctuationButton(),
                    harness.panel.enterButton(),
                    harness.panel.latinTab(),
                    harness.panel.chineseTab()
            }) {
                assertTrue("action starts outside panel", absoluteLeft(action) >= 0);
                assertTrue("action is clipped at panel end",
                        absoluteLeft(action) + action.getWidth()
                                <= harness.panel.root().getWidth());
            }
            assertCentered(harness.panel.microphoneButton());
            assertCentered(harness.panel.deleteButton());
        });
    }

    @Test
    public void phaseAndAvailabilityChangeOnlyPresentationAndButtonState() {
        onMain(() -> {
            Harness harness = new Harness(false);

            harness.panel.setPhase(VoiceInputPanel.Phase.LISTENING);
            assertEquals(harness.context.getString(R.string.ime_voice_finish_hint),
                    harness.panel.hint().getText().toString());
            assertTrue(harness.panel.microphoneButton().isSelected());

            harness.panel.setPhase(VoiceInputPanel.Phase.PROCESSING);
            assertEquals(harness.context.getString(R.string.ime_voice_processing_hint),
                    harness.panel.hint().getText().toString());
            assertFalse(harness.panel.microphoneButton().isSelected());

            harness.panel.setEditorActionsEnabled(false);
            assertFalse(harness.panel.deleteButton().isEnabled());
            assertFalse(harness.panel.punctuationButton().isEnabled());
            assertFalse(harness.panel.enterButton().isEnabled());
            assertTrue(harness.panel.latinTab().isEnabled());
            assertTrue(harness.panel.chineseTab().isEnabled());

            harness.panel.setKeyboardTabsEnabled(false);
            assertFalse(harness.panel.latinTab().isEnabled());
            assertFalse(harness.panel.chineseTab().isEnabled());
        });
    }

    @Test
    public void unlabelledMicrophoneFailsClosed() {
        onMain(() -> {
            Context context = ApplicationProvider.getApplicationContext();
            CenteredIconButton microphone = new CenteredIconButton(context);
            microphone.setOnClickListener(ignored -> {});
            assertThrows(IllegalArgumentException.class, () -> new VoiceInputPanel(
                    context, microphone, false, new EmptyListener()));
        });
    }

    private static int absoluteLeft(View view) {
        int left = view.getLeft();
        View parent = (View) view.getParent();
        while (parent != null) {
            left += parent.getLeft();
            if (!(parent.getParent() instanceof View)) break;
            parent = (View) parent.getParent();
        }
        return left;
    }

    private static int absoluteCenterX(View view) {
        return absoluteLeft(view) + view.getWidth() / 2;
    }

    private static void assertCentered(CenteredIconButton button) {
        Rect bounds = button.centeredIconBounds();
        assertTrue("empty icon bounds", !bounds.isEmpty());
        assertTrue("horizontal icon drift=" + bounds,
                Math.abs(button.getWidth() / 2 - bounds.centerX()) <= 1);
        assertTrue("vertical icon drift=" + bounds,
                Math.abs(button.getHeight() / 2 - bounds.centerY()) <= 1);
    }

    private static void measure(View view, int width, int maximumHeight) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static final class Harness extends EmptyListener {
        final Context context = ApplicationProvider.getApplicationContext();
        final AtomicInteger microphone = new AtomicInteger();
        final AtomicInteger delete = new AtomicInteger();
        final AtomicInteger punctuation = new AtomicInteger();
        final AtomicInteger enter = new AtomicInteger();
        final AtomicInteger latin = new AtomicInteger();
        final AtomicInteger chinese = new AtomicInteger();
        final VoiceInputPanel panel;

        Harness(boolean wideLandscape) {
            CenteredIconButton microphoneButton = new CenteredIconButton(context);
            microphoneButton.setContentDescription("Record");
            microphoneButton.setBackgroundResource(R.drawable.ime_key_background);
            microphoneButton.setOnClickListener(ignored -> microphone.incrementAndGet());
            panel = new VoiceInputPanel(context, microphoneButton, wideLandscape, this);
        }

        @Override
        public void onDelete() {
            delete.incrementAndGet();
        }

        @Override
        public void onPunctuation(View anchor) {
            punctuation.incrementAndGet();
        }

        @Override
        public void onEditorAction() {
            enter.incrementAndGet();
        }

        @Override
        public void onLatinKeyboard() {
            latin.incrementAndGet();
        }

        @Override
        public void onChineseKeyboard() {
            chinese.incrementAndGet();
        }

        int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }

    private static class EmptyListener implements VoiceInputPanel.Listener {
        @Override public void onDelete() {}
        @Override public void onPunctuation(View anchor) {}
        @Override public void onEditorAction() {}
        @Override public void onLatinKeyboard() {}
        @Override public void onChineseKeyboard() {}
    }
}
