package com.opentypeless.android.keyboard.voice;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.opentypeless.android.R;
import com.opentypeless.android.keyboard.ui.CenteredIconButton;
import java.util.Objects;

/** Capability-free, Typeless-inspired control surface for the Voice input page. */
public final class VoiceInputPanel {
    public enum Phase {
        IDLE,
        PREPARING,
        LISTENING,
        PROCESSING
    }

    public interface Listener {
        void onDelete();

        void onPunctuation(View anchor);

        void onEditorAction();

        void onLatinKeyboard();

        void onChineseKeyboard();
    }

    public static final int MINIMUM_TOUCH_TARGET_DP = 48;
    public static final String ROOT_TAG = "opentypeless-voice-input-panel";
    public static final String BRAND_TAG = "opentypeless-voice-brand";
    public static final String VOICE_TAB_TAG = "opentypeless-voice-tab";
    public static final String LATIN_TAB_TAG = "opentypeless-voice-latin-tab";
    public static final String CHINESE_TAB_TAG = "opentypeless-voice-chinese-tab";
    public static final String MICROPHONE_TAG = "opentypeless-voice-microphone";
    public static final String DELETE_TAG = "opentypeless-voice-delete";
    public static final String PUNCTUATION_TAG = "opentypeless-voice-punctuation";
    public static final String ENTER_TAG = "opentypeless-voice-enter";

    private final Context context;
    private final LinearLayout root;
    private final TextView brand;
    private final TextView hint;
    private final CenteredIconButton voiceTab;
    private final Button latinTab;
    private final Button chineseTab;
    private final CenteredIconButton microphone;
    private final CenteredIconButton delete;
    private final Button punctuation;
    private final Button enter;
    private Phase phase = Phase.IDLE;
    private String statusMessage = "";
    private boolean statusError;

    public VoiceInputPanel(
            Context context,
            CenteredIconButton microphone,
            boolean wideLandscape,
            Listener listener) {
        this.context = Objects.requireNonNull(context, "context");
        this.microphone = requireMicrophone(microphone);
        Listener callbacks = Objects.requireNonNull(listener, "listener");

        root = new LinearLayout(context);
        root.setTag(ROOT_TAG);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(8), dp(4), dp(8), dp(8));

        brand = new TextView(context);
        brand.setTag(BRAND_TAG);
        brand.setText(R.string.ime_voice_brand);
        brand.setTextColor(context.getColor(R.color.ime_on_surface));
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brand.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        brand.setIncludeFontPadding(false);

        voiceTab = new CenteredIconButton(context);
        configureSegmentButton(voiceTab, R.string.ime_cd_voice_tab_active);
        voiceTab.setTag(VOICE_TAB_TAG);
        voiceTab.setCenteredIconResource(R.drawable.ime_ic_microphone_toolbar);
        voiceTab.setSelected(true);
        voiceTab.setEnabled(false);

        latinTab = segmentTextButton(
                context.getString(R.string.ime_key_engine_latin),
                context.getString(R.string.ime_cd_open_latin_tab),
                ignored -> callbacks.onLatinKeyboard());
        latinTab.setTag(LATIN_TAB_TAG);
        chineseTab = segmentTextButton(
                context.getString(R.string.ime_voice_chinese_tab),
                context.getString(R.string.ime_cd_open_chinese_tab),
                ignored -> callbacks.onChineseKeyboard());
        chineseTab.setTag(CHINESE_TAB_TAG);

        hint = new TextView(context);
        hint.setText(R.string.ime_voice_tap_hint);
        hint.setTextColor(context.getColor(R.color.ime_on_surface_variant));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(true);
        hint.setIncludeFontPadding(false);

        configureMicrophone();
        delete = iconButton(
                R.drawable.ime_ic_backspace,
                R.string.ime_cd_delete,
                ignored -> callbacks.onDelete());
        delete.setTag(DELETE_TAG);

        punctuation = textButton(
                context.getString(R.string.ime_voice_punctuation_key),
                context.getString(R.string.ime_cd_punctuation),
                callbacks::onPunctuation);
        punctuation.setTag(PUNCTUATION_TAG);

        enter = textButton(
                context.getString(R.string.ime_key_enter),
                context.getString(R.string.ime_cd_enter),
                ignored -> callbacks.onEditorAction());
        enter.setTag(ENTER_TAG);

        if (wideLandscape) {
            buildWideLandscape();
        } else {
            buildPortrait();
        }
        setPhase(Phase.IDLE);
    }

    public LinearLayout root() {
        return root;
    }

    public TextView hint() {
        return hint;
    }

    public TextView brand() {
        return brand;
    }

    public CenteredIconButton voiceTab() {
        return voiceTab;
    }

    public Button latinTab() {
        return latinTab;
    }

    public Button chineseTab() {
        return chineseTab;
    }

    public CenteredIconButton microphoneButton() {
        return microphone;
    }

    public CenteredIconButton deleteButton() {
        return delete;
    }

    public Button punctuationButton() {
        return punctuation;
    }

    public Button enterButton() {
        return enter;
    }

    public void setPhase(Phase phase) {
        this.phase = Objects.requireNonNull(phase, "phase");
        renderHint();
        microphone.setSelected(phase == Phase.LISTENING);
    }

    public void setStatusMessage(String message, boolean error) {
        statusMessage = message == null ? "" : message.trim();
        statusError = error;
        renderHint();
    }

    private void renderHint() {
        if (!statusMessage.isBlank()) {
            hint.setText(statusMessage);
            hint.setTextColor(context.getColor(
                    statusError ? R.color.ime_error : R.color.ime_on_surface_variant));
            return;
        }
        hint.setText(switch (phase) {
            case IDLE -> R.string.ime_voice_tap_hint;
            case PREPARING -> R.string.ime_voice_preparing_hint;
            case LISTENING -> R.string.ime_voice_finish_hint;
            case PROCESSING -> R.string.ime_voice_processing_hint;
        });
        hint.setTextColor(context.getColor(R.color.ime_on_surface_variant));
    }

    public void setEditorActionsEnabled(boolean enabled) {
        delete.setEnabled(enabled);
        punctuation.setEnabled(enabled);
        enter.setEnabled(enabled);
    }

    public void setKeyboardTabsEnabled(boolean enabled) {
        latinTab.setEnabled(enabled);
        chineseTab.setEnabled(enabled);
    }

    private void buildPortrait() {
        root.setOrientation(LinearLayout.VERTICAL);
        // The fixed children and vertical margins total 256dp. Advertising the smaller legacy
        // minimum lets edge-to-edge IME windows constrain this page and clip the Enter capsule.
        root.setMinimumHeight(dp(256));

        root.addView(createHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32));
        hintParams.topMargin = dp(4);
        hintParams.bottomMargin = dp(4);
        root.addView(hint, hintParams);

        FrameLayout primaryStage = new FrameLayout(context);
        FrameLayout.LayoutParams microphoneParams = new FrameLayout.LayoutParams(
                dp(168), dp(64), Gravity.CENTER);
        primaryStage.addView(microphone, microphoneParams);

        LinearLayout utilities = new LinearLayout(context);
        utilities.setOrientation(LinearLayout.VERTICAL);
        utilities.setGravity(Gravity.CENTER);
        addExact(utilities, delete, 48, 48, 0, 0);
        LinearLayout.LayoutParams punctuationParams = exactParams(48, 48, 0, 0);
        punctuationParams.topMargin = dp(4);
        utilities.addView(punctuation, punctuationParams);
        FrameLayout.LayoutParams utilityParams = new FrameLayout.LayoutParams(
                dp(48), dp(100), Gravity.END | Gravity.CENTER_VERTICAL);
        utilityParams.setMarginEnd(dp(10));
        primaryStage.addView(utilities, utilityParams);

        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(100));
        primaryParams.bottomMargin = dp(8);
        root.addView(primaryStage, primaryParams);

        FrameLayout actionStage = new FrameLayout(context);
        actionStage.addView(enter, new FrameLayout.LayoutParams(
                dp(128), dp(48), Gravity.CENTER));
        root.addView(actionStage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void buildWideLandscape() {
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setMinimumHeight(dp(88));

        LinearLayout contextColumn = new LinearLayout(context);
        contextColumn.setOrientation(LinearLayout.VERTICAL);
        contextColumn.addView(createHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        contextColumn.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(dp(238), dp(72));
        contextParams.setMarginEnd(dp(6));
        root.addView(contextColumn, contextParams);

        LinearLayout actions = horizontalRow();
        addExact(actions, microphone, 148, 56, 0, 6);
        addExact(actions, delete, 48, 48, 0, 2);
        addExact(actions, punctuation, 48, 48, 2, 4);
        addExact(actions, enter, 96, 48, 4, 0);
        root.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(56)));
    }

    private void configureMicrophone() {
        microphone.setTag(MICROPHONE_TAG);
        microphone.setBackgroundResource(R.drawable.ime_voice_button_background);
        microphone.setCenteredIconResource(R.drawable.ime_ic_microphone);
        microphone.setBackgroundTintList(null);
        microphone.setTextColor(context.getColor(R.color.ime_on_voice_primary));
        microphone.setMinWidth(dp(148));
        microphone.setMinimumWidth(dp(148));
        microphone.setMinHeight(dp(56));
        microphone.setMinimumHeight(dp(56));
        microphone.setPadding(0, 0, 0, 0);
        microphone.setElevation(0f);
        microphone.setStateListAnimator(null);
    }

    private LinearLayout createHeader() {
        LinearLayout header = horizontalRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(brand, new LinearLayout.LayoutParams(
                0, dp(48), 1f));
        LinearLayout segments = horizontalRow();
        segments.setBackgroundResource(R.drawable.ime_voice_segment_container);
        addExact(segments, voiceTab, 48, 48, 0, 0);
        addExact(segments, latinTab, 48, 48, 0, 0);
        addExact(segments, chineseTab, 48, 48, 0, 0);
        header.addView(segments, new LinearLayout.LayoutParams(dp(144), dp(48)));
        return header;
    }

    private void configureSegmentButton(Button button, int descriptionResource) {
        button.setContentDescription(context.getString(descriptionResource));
        button.setBackgroundResource(R.drawable.ime_voice_segment_item_background);
        button.setBackgroundTintList(null);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setPadding(0, 0, 0, 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);
    }

    private Button segmentTextButton(
            String label,
            String description,
            View.OnClickListener listener) {
        Button button = new Button(context);
        configureSegmentButton(button, R.string.ime_cd_open_keyboard_tab);
        button.setText(label);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setTextColor(context.getColorStateList(R.color.ime_key_text));
        button.setOnClickListener(listener);
        return button;
    }

    private CenteredIconButton iconButton(
            int iconResource,
            int descriptionResource,
            View.OnClickListener listener) {
        CenteredIconButton button = new CenteredIconButton(context);
        button.setContentDescription(context.getString(descriptionResource));
        button.setBackgroundResource(R.drawable.ime_voice_aux_button_background);
        button.setCenteredIconResource(iconResource);
        button.setBackgroundTintList(null);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setPadding(0, 0, 0, 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setOnClickListener(listener);
        return button;
    }

    private Button textButton(
            String label,
            String description,
            View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(label);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setAutoSizeTextTypeUniformWithConfiguration(
                9, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setTextColor(context.getColorStateList(R.color.ime_key_text));
        button.setBackgroundResource(R.drawable.ime_voice_aux_button_background);
        button.setBackgroundTintList(null);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void addExact(
            LinearLayout parent,
            View child,
            int widthDp,
            int heightDp,
            int startMarginDp,
            int endMarginDp) {
        parent.addView(child, exactParams(
                widthDp, heightDp, startMarginDp, endMarginDp));
    }

    private LinearLayout.LayoutParams exactParams(
            int widthDp,
            int heightDp,
            int startMarginDp,
            int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(widthDp), dp(heightDp));
        params.setMarginStart(dp(startMarginDp));
        params.setMarginEnd(dp(endMarginDp));
        return params;
    }

    private CenteredIconButton requireMicrophone(CenteredIconButton button) {
        CenteredIconButton value = Objects.requireNonNull(button, "microphone");
        CharSequence description = value.getContentDescription();
        if (!value.isClickable() || description == null || description.toString().isBlank()) {
            throw new IllegalArgumentException("microphone must be clickable and labelled");
        }
        return value;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
