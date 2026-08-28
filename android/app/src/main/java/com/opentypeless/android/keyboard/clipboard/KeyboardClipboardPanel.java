package com.opentypeless.android.keyboard.clipboard;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.opentypeless.android.R;
import com.opentypeless.android.keyboard.ui.CenteredIconButton;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Capability-free renderer for bounded clipboard history and its in-memory search query. */
public final class KeyboardClipboardPanel {
    public interface Listener {
        void onPaste(String text);

        void onRefresh();

        void onPinChanged(String text, boolean pinned);

        void onDelete(String text);

        void onClose();

        void onSearchEditingChanged(boolean editing);

        void onClearHistory();
    }

    public static final String ROOT_TAG = "opentypeless-clipboard-panel";
    public static final String CONTENT_TAG_PREFIX = "opentypeless-clipboard-entry-";
    public static final String PIN_TAG_PREFIX = "opentypeless-clipboard-pin-";
    public static final String DELETE_TAG_PREFIX = "opentypeless-clipboard-delete-";
    public static final String SEARCH_TAG = "opentypeless-clipboard-search";
    public static final String SEARCH_QUERY_TAG = "opentypeless-clipboard-search-query";
    public static final String REFRESH_TAG = "opentypeless-clipboard-refresh";
    public static final String CLEAR_TAG = "opentypeless-clipboard-clear";
    public static final String CLOSE_TAG = "opentypeless-clipboard-close";
    public static final int MINIMUM_TOUCH_TARGET_DP = 48;
    public static final int PANEL_MINIMUM_HEIGHT_DP = 276;
    public static final int SEARCH_OVERLAY_HEIGHT_DP = 60;
    private static final int CARD_PREVIEW_CODE_POINTS = 120;

    private final Context context;
    private final Listener listener;
    private final LinearLayout root;
    private final LinearLayout normalHeader;
    private final LinearLayout searchHeader;
    private final HorizontalScrollView categoryScroller;
    private final TextView title;
    private final TextView searchQuery;
    private final TextView message;
    private final LinearLayout entries;
    private final ScrollView entriesScroller;
    private final CenteredIconButton search;
    private final CenteredIconButton refresh;
    private final CenteredIconButton clearHistory;
    private final CenteredIconButton close;
    private final CenteredIconButton searchBack;
    private final CenteredIconButton searchClear;
    private final Button searchDone;
    private final EnumMap<ClipboardHistory.Category, Button> categoryButtons =
            new EnumMap<>(ClipboardHistory.Category.class);

    private ClipboardHistory history = ClipboardHistory.empty();
    private ClipboardPanelSnapshot.State currentState = ClipboardPanelSnapshot.State.EMPTY;
    private ClipboardHistory.Category category = ClipboardHistory.Category.ALL;
    private String query = "";
    private boolean searchEditing;
    private boolean clearArmed;
    private long renderGeneration;

    public KeyboardClipboardPanel(Context context, Listener listener) {
        this.context = Objects.requireNonNull(context, "context");
        this.listener = Objects.requireNonNull(listener, "listener");

        root = new LinearLayout(context);
        root.setTag(ROOT_TAG);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setMinimumHeight(dp(PANEL_MINIMUM_HEIGHT_DP));
        root.setPadding(dp(6), dp(4), dp(6), dp(6));

        title = textView(16, R.color.ime_on_surface, Gravity.START | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        normalHeader = new LinearLayout(context);
        normalHeader.setOrientation(LinearLayout.HORIZONTAL);
        normalHeader.setGravity(Gravity.CENTER_VERTICAL);
        normalHeader.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        search = iconAction(
                R.drawable.ime_ic_search,
                R.string.ime_cd_clipboard_search,
                SEARCH_TAG,
                ignored -> beginSearchEditing());
        refresh = iconAction(
                R.drawable.ime_ic_refresh,
                R.string.ime_cd_clipboard_refresh,
                REFRESH_TAG,
                ignored -> {
                    disarmClear();
                    listener.onRefresh();
                });
        clearHistory = iconAction(
                R.drawable.ime_ic_delete_all,
                R.string.ime_cd_clipboard_clear,
                CLEAR_TAG,
                ignored -> requestClearHistory());
        close = iconAction(
                R.drawable.ime_ic_close,
                R.string.ime_cd_clipboard_close,
                CLOSE_TAG,
                ignored -> {
                    disarmClear();
                    listener.onClose();
                });
        normalHeader.addView(search, touchTarget());
        normalHeader.addView(refresh, touchTarget());
        normalHeader.addView(clearHistory, touchTarget());
        normalHeader.addView(close, touchTarget());
        root.addView(normalHeader, matchHeight(48));

        searchHeader = new LinearLayout(context);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchBack = iconAction(
                R.drawable.ime_ic_arrow_back,
                R.string.ime_cd_clipboard_finish_search,
                SEARCH_TAG + "-back",
                ignored -> finishSearchEditing());
        searchHeader.addView(searchBack, touchTarget());

        searchQuery = textView(15, R.color.ime_on_surface, Gravity.START | Gravity.CENTER_VERTICAL);
        searchQuery.setTag(SEARCH_QUERY_TAG);
        searchQuery.setSingleLine(true);
        searchQuery.setEllipsize(TextUtils.TruncateAt.START);
        searchQuery.setBackgroundResource(R.drawable.ime_clipboard_search_background);
        searchQuery.setPadding(dp(14), 0, dp(10), 0);
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        queryParams.setMarginStart(dp(4));
        queryParams.setMarginEnd(dp(4));
        searchHeader.addView(searchQuery, queryParams);

        searchClear = iconAction(
                R.drawable.ime_ic_close,
                R.string.ime_cd_clipboard_clear_search,
                SEARCH_TAG + "-clear",
                ignored -> clearSearchQuery());
        searchHeader.addView(searchClear, touchTarget());
        searchDone = textAction(
                R.string.ime_clipboard_search_done,
                R.string.ime_cd_clipboard_finish_search,
                ignored -> finishSearchEditing());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(64), dp(48));
        doneParams.setMarginStart(dp(4));
        searchHeader.addView(searchDone, doneParams);
        searchHeader.setVisibility(View.GONE);
        root.addView(searchHeader, matchHeight(48));

        LinearLayout categoryRow = new LinearLayout(context);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        addCategory(categoryRow, ClipboardHistory.Category.ALL, R.string.ime_clipboard_category_all);
        addCategory(categoryRow, ClipboardHistory.Category.TEXT, R.string.ime_clipboard_category_text);
        addCategory(categoryRow, ClipboardHistory.Category.NUMBER, R.string.ime_clipboard_category_number);
        addCategory(categoryRow, ClipboardHistory.Category.LINK, R.string.ime_clipboard_category_link);
        categoryScroller = new HorizontalScrollView(context);
        categoryScroller.setHorizontalScrollBarEnabled(false);
        categoryScroller.setFillViewport(true);
        categoryScroller.addView(categoryRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                dp(MINIMUM_TOUCH_TARGET_DP)));
        root.addView(categoryScroller, matchHeight(MINIMUM_TOUCH_TARGET_DP));

        message = textView(14, R.color.ime_on_surface_variant, Gravity.CENTER);
        message.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        entries = new LinearLayout(context);
        entries.setOrientation(LinearLayout.VERTICAL);
        entries.setPadding(0, dp(2), 0, dp(2));
        entriesScroller = new ScrollView(context);
        entriesScroller.setFillViewport(true);
        entriesScroller.setVerticalScrollBarEnabled(false);
        entriesScroller.addView(entries, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(entriesScroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        render(ClipboardHistory.empty(), ClipboardPanelSnapshot.State.EMPTY);
    }

    public LinearLayout root() {
        return root;
    }

    public CenteredIconButton searchButton() {
        return search;
    }

    public CenteredIconButton refreshButton() {
        return refresh;
    }

    public CenteredIconButton clearHistoryButton() {
        return clearHistory;
    }

    public CenteredIconButton closeButton() {
        return close;
    }

    public TextView searchQueryView() {
        return searchQuery;
    }

    public LinearLayout entriesContainer() {
        return entries;
    }

    public Button categoryButton(ClipboardHistory.Category requested) {
        return categoryButtons.get(Objects.requireNonNull(requested, "requested"));
    }

    public boolean isSearchEditing() {
        return searchEditing;
    }

    public String searchQuery() {
        return query;
    }

    public void showLoading() {
        renderGeneration++;
        entries.removeAllViews();
        entriesScroller.setVisibility(View.GONE);
        message.setText(R.string.ime_clipboard_loading);
        message.setVisibility(View.VISIBLE);
        updateTitle(0);
    }

    public void render(ClipboardHistory next, ClipboardPanelSnapshot.State state) {
        history = Objects.requireNonNull(next, "next");
        currentState = Objects.requireNonNull(state, "state");
        disarmClear();
        renderFilteredEntries();
    }

    public boolean appendSearchText(String text) {
        if (!searchEditing || text == null || text.isEmpty()) return false;
        String candidate = query + text;
        if (candidate.codePointCount(0, candidate.length()) > ClipboardHistory.MAX_SEARCH_CODE_POINTS
                || !ClipboardPanelSnapshot.fromPrimaryText(candidate).hasText()) {
            return false;
        }
        query = candidate;
        updateSearchQuery();
        return true;
    }

    public boolean deleteSearchCodePoint() {
        if (!searchEditing) return false;
        if (query.isEmpty()) return true;
        int end = query.offsetByCodePoints(query.length(), -1);
        query = query.substring(0, end);
        updateSearchQuery();
        return true;
    }

    public boolean finishSearchEditing() {
        if (!searchEditing) return false;
        searchEditing = false;
        root.setMinimumHeight(dp(PANEL_MINIMUM_HEIGHT_DP));
        searchHeader.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        categoryScroller.setVisibility(View.VISIBLE);
        listener.onSearchEditingChanged(false);
        renderFilteredEntries();
        return true;
    }

    /** Drops all body/query references before leaving the active ordinary-field lifecycle. */
    public void clear() {
        renderGeneration++;
        history = ClipboardHistory.empty();
        currentState = ClipboardPanelSnapshot.State.UNAVAILABLE;
        query = "";
        category = ClipboardHistory.Category.ALL;
        searchEditing = false;
        clearArmed = false;
        clearHistory.setSelected(false);
        clearHistory.setContentDescription(context.getString(R.string.ime_cd_clipboard_clear));
        entries.removeAllViews();
        searchQuery.setText("");
        title.setText("");
        message.setText(R.string.ime_clipboard_unavailable);
        message.setVisibility(View.VISIBLE);
        entriesScroller.setVisibility(View.GONE);
        searchHeader.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        categoryScroller.setVisibility(View.VISIBLE);
        root.setMinimumHeight(dp(PANEL_MINIMUM_HEIGHT_DP));
        updateCategories();
    }

    private void beginSearchEditing() {
        disarmClear();
        if (searchEditing) return;
        searchEditing = true;
        renderGeneration++;
        root.setMinimumHeight(dp(SEARCH_OVERLAY_HEIGHT_DP));
        normalHeader.setVisibility(View.GONE);
        categoryScroller.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        entriesScroller.setVisibility(View.GONE);
        searchHeader.setVisibility(View.VISIBLE);
        updateSearchQuery();
        listener.onSearchEditingChanged(true);
    }

    private void clearSearchQuery() {
        query = "";
        updateSearchQuery();
    }

    private void requestClearHistory() {
        if (history.size() == 0) return;
        if (!clearArmed) {
            clearArmed = true;
            clearHistory.setSelected(true);
            clearHistory.setContentDescription(context.getString(
                    R.string.ime_cd_clipboard_confirm_clear));
            title.setText(R.string.ime_clipboard_confirm_clear);
            return;
        }
        clearArmed = false;
        clearHistory.setSelected(false);
        clearHistory.setContentDescription(context.getString(R.string.ime_cd_clipboard_clear));
        listener.onClearHistory();
    }

    private void disarmClear() {
        if (!clearArmed) return;
        clearArmed = false;
        clearHistory.setSelected(false);
        clearHistory.setContentDescription(context.getString(R.string.ime_cd_clipboard_clear));
        updateTitle(history.filtered(query, category).size());
    }

    private void addCategory(
            LinearLayout row,
            ClipboardHistory.Category value,
            int labelResource) {
        Button button = textAction(labelResource, labelResource, ignored -> {
            disarmClear();
            category = value;
            renderFilteredEntries();
        });
        button.setTag("opentypeless-clipboard-category-" + value.name().toLowerCase());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(84), dp(48));
        params.setMarginEnd(dp(2));
        row.addView(button, params);
        categoryButtons.put(value, button);
    }

    private void renderFilteredEntries() {
        renderGeneration++;
        long generation = renderGeneration;
        List<ClipboardHistory.Entry> visible = history.filtered(query, category);
        entries.removeAllViews();
        updateCategories();
        updateTitle(visible.size());
        if (visible.isEmpty()) {
            entriesScroller.setVisibility(View.GONE);
            message.setText(emptyMessage());
            message.setVisibility(View.VISIBLE);
            return;
        }
        message.setVisibility(View.GONE);
        entriesScroller.setVisibility(View.VISIBLE);
        int index = 0;
        for (ClipboardHistory.Entry entry : visible) {
            LinearLayout cardRow = new LinearLayout(context);
            cardRow.setOrientation(LinearLayout.HORIZONTAL);
            cardRow.setGravity(Gravity.CENTER_VERTICAL);
            cardRow.setPadding(dp(4), 0, dp(4), 0);
            cardRow.setBackgroundResource(R.drawable.ime_clipboard_card_background);

            Button card = new Button(context);
            card.setTag(CONTENT_TAG_PREFIX + index);
            card.setAllCaps(false);
            card.setSingleLine(false);
            card.setMaxLines(2);
            card.setEllipsize(TextUtils.TruncateAt.END);
            card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            card.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            card.setText(entry.preview(CARD_PREVIEW_CODE_POINTS));
            card.setTextColor(context.getColor(R.color.ime_on_surface));
            card.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            card.setPadding(dp(10), dp(8), dp(8), dp(8));
            card.setBackgroundColor(context.getColor(android.R.color.transparent));
            card.setContentDescription(context.getString(
                    R.string.ime_cd_clipboard_paste_item,
                    context.getString(categoryLabel(entry.category())),
                    entry.preview(40)));
            String exactText = entry.text();
            card.setOnClickListener(ignored -> {
                disarmClear();
                if (generation == renderGeneration) listener.onPaste(exactText);
            });
            cardRow.addView(card, new LinearLayout.LayoutParams(0, dp(68), 1f));

            CenteredIconButton pin = iconAction(
                    R.drawable.ime_ic_push_pin,
                    entry.pinned()
                            ? R.string.ime_cd_clipboard_unpin_item
                            : R.string.ime_cd_clipboard_pin_item,
                    PIN_TAG_PREFIX + index,
                    ignored -> {
                        disarmClear();
                        if (generation == renderGeneration) {
                            listener.onPinChanged(exactText, !entry.pinned());
                        }
                    });
            pin.setSelected(entry.pinned());
            pin.setBackgroundResource(entry.pinned()
                    ? R.drawable.ime_clipboard_tab_selected
                    : R.drawable.ime_clipboard_tab_default);
            pin.setContentDescription(context.getString(
                    entry.pinned()
                            ? R.string.ime_cd_clipboard_unpin_item
                            : R.string.ime_cd_clipboard_pin_item,
                    entry.preview(40)));
            cardRow.addView(pin, touchTarget());

            CenteredIconButton delete = iconAction(
                    R.drawable.ime_ic_delete_all,
                    R.string.ime_cd_clipboard_delete_item,
                    DELETE_TAG_PREFIX + index,
                    ignored -> {
                        disarmClear();
                        if (generation == renderGeneration) listener.onDelete(exactText);
                    });
            delete.setContentDescription(context.getString(
                    R.string.ime_cd_clipboard_delete_item,
                    entry.preview(40)));
            cardRow.addView(delete, touchTarget());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
            params.topMargin = dp(3);
            entries.addView(cardRow, params);
            index++;
        }
        entriesScroller.scrollTo(0, 0);
    }

    private int emptyMessage() {
        if (!query.isEmpty()) return R.string.ime_clipboard_no_results;
        if (history.size() != 0) return R.string.ime_clipboard_category_empty;
        return switch (currentState) {
            case UNSUPPORTED -> R.string.ime_clipboard_text_only;
            case TOO_LARGE -> R.string.ime_clipboard_too_large;
            case UNAVAILABLE -> R.string.ime_clipboard_unavailable;
            case TEXT, EMPTY -> R.string.ime_clipboard_history_empty;
        };
    }

    private void updateTitle(int visibleCount) {
        if (clearArmed) return;
        if (query.isEmpty()) {
            title.setText(context.getString(
                    R.string.ime_clipboard_title_count,
                    history.size(),
                    ClipboardHistory.MAX_ENTRIES));
        } else {
            title.setText(context.getString(
                    R.string.ime_clipboard_search_result_count,
                    query,
                    visibleCount));
        }
    }

    private void updateSearchQuery() {
        searchQuery.setText(query.isEmpty()
                ? context.getString(R.string.ime_clipboard_search_placeholder)
                : query);
        searchQuery.setTextColor(context.getColor(query.isEmpty()
                ? R.color.ime_on_surface_variant
                : R.color.ime_on_surface));
        searchClear.setEnabled(!query.isEmpty());
    }

    private void updateCategories() {
        for (var entry : categoryButtons.entrySet()) {
            Button button = entry.getValue();
            boolean selected = entry.getKey() == category;
            button.setSelected(selected);
            button.setBackgroundResource(selected
                    ? R.drawable.ime_clipboard_tab_selected
                    : R.drawable.ime_clipboard_tab_default);
            button.setTextColor(context.getColor(selected
                    ? R.color.ime_on_primary_container
                    : R.color.ime_on_surface_variant));
        }
    }

    private int categoryLabel(ClipboardHistory.Category value) {
        return switch (value) {
            case ALL -> R.string.ime_clipboard_category_all;
            case TEXT -> R.string.ime_clipboard_category_text;
            case NUMBER -> R.string.ime_clipboard_category_number;
            case LINK -> R.string.ime_clipboard_category_link;
        };
    }

    private CenteredIconButton iconAction(
            int iconResource,
            int descriptionResource,
            String tag,
            View.OnClickListener action) {
        CenteredIconButton button = new CenteredIconButton(context);
        button.setTag(tag);
        button.setContentDescription(context.getString(descriptionResource));
        button.setCenteredIconResource(iconResource);
        button.setBackgroundResource(R.drawable.ime_voice_aux_button_background);
        button.setBackgroundTintList(null);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setPadding(0, 0, 0, 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setOnClickListener(action);
        return button;
    }

    private Button textAction(
            int labelResource,
            int descriptionResource,
            View.OnClickListener action) {
        Button button = new Button(context);
        button.setText(labelResource);
        button.setContentDescription(context.getString(descriptionResource));
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setAutoSizeTextTypeUniformWithConfiguration(
                9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setTextColor(context.getColor(R.color.ime_on_surface_variant));
        button.setBackgroundResource(R.drawable.ime_clipboard_tab_default);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setOnClickListener(action);
        return button;
    }

    private TextView textView(int sp, int colorResource, int gravity) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(context.getColor(colorResource));
        view.setGravity(gravity);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private LinearLayout.LayoutParams touchTarget() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(MINIMUM_TOUCH_TARGET_DP), dp(MINIMUM_TOUCH_TARGET_DP));
        params.setMarginStart(dp(2));
        return params;
    }

    private LinearLayout.LayoutParams matchHeight(int heightDp) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
