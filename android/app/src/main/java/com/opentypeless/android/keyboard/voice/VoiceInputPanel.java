package com.opentypeless.android.keyboard.voice;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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

        void onSwitchKeyboard();

        void onShowKeyboardPicker();
    }

    public static final int MINIMUM_TOUCH_TARGET_DP = 48;
    public static final String ROOT_TAG = "opentypeless-voice-input-panel";
    public static final String MICROPHONE_TAG = "opentypeless-voice-microphone";
    public static final String DELETE_TAG = "opentypeless-voice-delete";
    public static final String PUNCTUATION_TAG = "opentypeless-voice-punctuation";
    public static final String ENTER_TAG = "opentypeless-voice-enter";
    public static final String SYSTEM_KEYBOARD_TAG = "opentypeless-voice-system-keyboard";

    private final Context context;
    private final LinearLayout root;
    private final TextView hint;
    private final CenteredIconButton microphone;
    private final CenteredIconButton delete;
    private final Button punctuation;
    private final Button enter;
    private final CenteredIconButton systemKeyboard;

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

        systemKeyboard = iconButton(
                R.drawable.ime_ic_globe,
                R.string.ime_cd_switch_keyboard,
                ignored -> callbacks.onSwitchKeyboard());
        systemKeyboard.setTag(SYSTEM_KEYBOARD_TAG);
        systemKeyboard.setOnLongClickListener(ignored -> {
            callbacks.onShowKeyboardPicker();
            return true;
        });

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

    public CenteredIconButton systemKeyboardButton() {
        return systemKeyboard;
    }

    public void setPhase(Phase phase) {
        Phase value = Objects.requireNonNull(phase, "phase");
        hint.setText(switch (value) {
            case IDLE -> R.string.ime_voice_tap_hint;
            case PREPARING -> R.string.ime_voice_preparing_hint;
            case LISTENING -> R.string.ime_voice_finish_hint;
            case PROCESSING -> R.string.ime_voice_processing_hint;
        });
        microphone.setSelected(value == Phase.LISTENING);
    }

    public void setEditorActionsEnabled(boolean enabled) {
        delete.setEnabled(enabled);
        punctuation.setEnabled(enabled);
        enter.setEnabled(enabled);
    }

    public void setSystemSwitchEnabled(boolean enabled) {
        systemKeyboard.setEnabled(enabled);
    }

    private void buildPortrait() {
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(212));

        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24));
        hintParams.bottomMargin = dp(4);
        root.addView(hint, hintParams);

        LinearLayout primaryRow = horizontalRow();
        addExact(primaryRow, microphone, 160, 64, 4, 6);

        LinearLayout utilities = new LinearLayout(context);
        utilities.setOrientation(LinearLayout.VERTICAL);
        utilities.setGravity(Gravity.CENTER);
        addExact(utilities, delete, 48, 48, 0, 0);
        LinearLayout.LayoutParams punctuationParams = exactParams(48, 48, 0, 0);
        punctuationParams.topMargin = dp(4);
        utilities.addView(punctuation, punctuationParams);
        primaryRow.addView(utilities, new LinearLayout.LayoutParams(dp(48), dp(100)));

        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(100));
        primaryParams.bottomMargin = dp(8);
        root.addView(primaryRow, primaryParams);

        LinearLayout actionRow = horizontalRow();
        addExact(actionRow, systemKeyboard, 48, 48, 0, 4);
        addExact(actionRow, enter, 112, 48, 4, 4);
        View balance = new View(context);
        balance.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addExact(actionRow, balance, 48, 48, 4, 0);
        root.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void buildWideLandscape() {
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setMinimumHeight(dp(76));

        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, dp(56), 1f);
        hintParams.setMarginEnd(dp(8));
        root.addView(hint, hintParams);

        LinearLayout actions = horizontalRow();
        addExact(actions, systemKeyboard, 48, 48, 0, 4);
        addExact(actions, microphone, 148, 56, 4, 6);
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
