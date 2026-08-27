package com.opentypeless.android.keyboard.emoji;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.opentypeless.android.R;
import com.opentypeless.android.keyboard.ui.CenteredIconButton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Capability-free categorized Emoji renderer with a bounded in-memory search query. */
public final class KeyboardEmojiPanel {
    public interface Listener {
        void onEmojiSelected(String emoji);

        void onClose();

        void onSearchEditingChanged(boolean editing);
    }

    public static final String ROOT_TAG = "opentypeless-emoji-panel";
    public static final String GRID_TAG = "opentypeless-emoji-grid";
    public static final String CLOSE_TAG = "opentypeless-emoji-close";
    public static final String SEARCH_TAG = "opentypeless-emoji-search";
    public static final String SEARCH_QUERY_TAG = "opentypeless-emoji-search-query";
    public static final int MINIMUM_TOUCH_TARGET_DP = 48;
    public static final int PANEL_MINIMUM_HEIGHT_DP = 334;
    public static final int SEARCH_OVERLAY_HEIGHT_DP = 60;
    private static final int GRID_HEIGHT_DP = 196;

    private final Context context;
    private final Listener listener;
    private final LinearLayout root;
    private final LinearLayout normalHeader;
    private final LinearLayout searchHeader;
    private final TextView title;
    private final TextView sectionTitle;
    private final TextView searchQuery;
    private final TextView empty;
    private final FrameLayout gridFrame;
    private final GridView grid;
    private final EmojiAdapter adapter;
    private final HorizontalScrollView categoryScroller;
    private final LinearLayout categoryStrip;
    private final CenteredIconButton close;
    private final CenteredIconButton search;
    private final CenteredIconButton searchBack;
    private final CenteredIconButton searchClear;
    private final Button searchDone;
    private final Map<EmojiCatalog.Category, Button> categoryButtons =
            new EnumMap<>(EmojiCatalog.Category.class);
    private final boolean simplifiedChinese;

    private EmojiRecents recents = EmojiRecents.empty();
    private boolean recentsVisible;
    private EmojiCatalog.Category selected = EmojiCatalog.Category.SMILEYS;
    private String query = "";
    private boolean searchEditing;
    private long renderGeneration;

    public KeyboardEmojiPanel(Context context, Listener listener) {
        this.context = Objects.requireNonNull(context, "context");
        this.listener = Objects.requireNonNull(listener, "listener");
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        simplifiedChinese = "zh".equals(locale.getLanguage());

        root = new LinearLayout(context);
        root.setTag(ROOT_TAG);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setMinimumHeight(dp(PANEL_MINIMUM_HEIGHT_DP));
        root.setPadding(dp(6), dp(4), dp(6), dp(6));

        normalHeader = new LinearLayout(context);
        normalHeader.setOrientation(LinearLayout.HORIZONTAL);
        normalHeader.setGravity(Gravity.CENTER_VERTICAL);
        close = iconAction(
                R.drawable.ime_ic_arrow_back,
                R.string.ime_cd_emoji_close,
                CLOSE_TAG,
                ignored -> listener.onClose());
        normalHeader.addView(close, touchTarget());
        title = textView(16, R.color.ime_on_surface, Gravity.START | Gravity.CENTER_VERTICAL);
        title.setText(R.string.ime_emoji_title);
        title.setSingleLine(true);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        normalHeader.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        search = iconAction(
                R.drawable.ime_ic_search,
                R.string.ime_cd_emoji_search,
                SEARCH_TAG,
                ignored -> beginSearchEditing());
        normalHeader.addView(search, touchTarget());
        root.addView(normalHeader, matchHeight(48));

        searchHeader = new LinearLayout(context);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchBack = iconAction(
                R.drawable.ime_ic_arrow_back,
                R.string.ime_cd_emoji_finish_search,
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
                R.string.ime_cd_emoji_clear_search,
                SEARCH_TAG + "-clear",
                ignored -> clearSearchQuery());
        searchHeader.addView(searchClear, touchTarget());
        searchDone = textAction(
                R.string.ime_emoji_search_done,
                R.string.ime_cd_emoji_finish_search,
                ignored -> finishSearchEditing());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(64), dp(48));
        doneParams.setMarginStart(dp(4));
        searchHeader.addView(searchDone, doneParams);
        searchHeader.setVisibility(View.GONE);
        root.addView(searchHeader, matchHeight(48));

        sectionTitle = textView(
                13, R.color.ime_on_surface_variant, Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(sectionTitle, matchHeight(32));

        gridFrame = new FrameLayout(context);
        grid = new GridView(context);
        grid.setTag(GRID_TAG);
        grid.setNumColumns(context.getResources().getConfiguration().screenWidthDp < 390 ? 7 : 8);
        grid.setColumnWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        grid.setStretchMode(GridView.NO_STRETCH);
        grid.setHorizontalSpacing(dp(2));
        grid.setVerticalSpacing(dp(1));
        grid.setGravity(Gravity.CENTER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(2), 0, dp(2));
        adapter = new EmojiAdapter();
        grid.setAdapter(adapter);
        gridFrame.addView(grid, frameMatch());
        empty = textView(14, R.color.ime_on_surface_variant, Gravity.CENTER);
        empty.setText(R.string.ime_emoji_recent_empty);
        gridFrame.addView(empty, frameMatch());
        root.addView(gridFrame, matchHeight(GRID_HEIGHT_DP));

        categoryScroller = new HorizontalScrollView(context);
        categoryScroller.setHorizontalScrollBarEnabled(false);
        categoryScroller.setFillViewport(true);
        categoryStrip = new LinearLayout(context);
        categoryStrip.setOrientation(LinearLayout.HORIZONTAL);
        categoryStrip.setGravity(Gravity.CENTER_VERTICAL);
        addCategory(EmojiCatalog.Category.RECENT, "◷", R.string.ime_emoji_category_recent);
        addCategory(EmojiCatalog.Category.SMILEYS, "😀", R.string.ime_emoji_category_smileys);
        addCategory(EmojiCatalog.Category.PEOPLE, "👋", R.string.ime_emoji_category_people);
        addCategory(EmojiCatalog.Category.ANIMALS, "🐻", R.string.ime_emoji_category_animals);
        addCategory(EmojiCatalog.Category.FOOD, "🍎", R.string.ime_emoji_category_food);
        addCategory(EmojiCatalog.Category.ACTIVITIES, "⚽", R.string.ime_emoji_category_activities);
        addCategory(EmojiCatalog.Category.TRAVEL, "🚗", R.string.ime_emoji_category_travel);
        addCategory(EmojiCatalog.Category.OBJECTS, "💡", R.string.ime_emoji_category_objects);
        addCategory(EmojiCatalog.Category.SYMBOLS, "❤️", R.string.ime_emoji_category_symbols);
        addCategory(EmojiCatalog.Category.FLAGS, "🏳️", R.string.ime_emoji_category_flags);
        categoryScroller.addView(categoryStrip, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                dp(MINIMUM_TOUCH_TARGET_DP)));
        root.addView(categoryScroller, matchHeight(MINIMUM_TOUCH_TARGET_DP));
        clear();
    }

    public LinearLayout root() {
        return root;
    }

    public CenteredIconButton closeButton() {
        return close;
    }

    public CenteredIconButton searchButton() {
        return search;
    }

    public TextView searchQueryView() {
        return searchQuery;
    }

    public Button categoryButton(EmojiCatalog.Category category) {
        return categoryButtons.get(Objects.requireNonNull(category, "category"));
    }

    public GridView grid() {
        return grid;
    }

    public LinearLayout categoryStrip() {
        return categoryStrip;
    }

    public EmojiCatalog.Category selectedCategory() {
        return selected;
    }

    public boolean isSearchEditing() {
        return searchEditing;
    }

    public String searchQuery() {
        return query;
    }

    public void render(EmojiRecents nextRecents, boolean allowRecents) {
        recents = Objects.requireNonNull(nextRecents, "nextRecents");
        recentsVisible = allowRecents;
        Button recent = categoryButtons.get(EmojiCatalog.Category.RECENT);
        recent.setVisibility(allowRecents ? View.VISIBLE : View.GONE);
        if (!allowRecents && selected == EmojiCatalog.Category.RECENT) {
            selected = EmojiCatalog.Category.SMILEYS;
        } else if (allowRecents && !recents.isEmpty() && query.isEmpty()) {
            selected = EmojiCatalog.Category.RECENT;
        }
        renderSelected();
    }

    public void recordSelection(EmojiRecents nextRecents) {
        recents = Objects.requireNonNull(nextRecents, "nextRecents");
        if (selected == EmojiCatalog.Category.RECENT && query.isEmpty()) renderSelected();
    }

    public boolean appendSearchText(String text) {
        if (!searchEditing || !EmojiCatalog.validSearchAppend(query, text)) return false;
        query += text;
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
        sectionTitle.setVisibility(View.VISIBLE);
        gridFrame.setVisibility(View.VISIBLE);
        categoryScroller.setVisibility(View.VISIBLE);
        listener.onSearchEditingChanged(false);
        renderSelected();
        return true;
    }

    /** Drops all query and rendered references before leaving the active editor lifecycle. */
    public void clear() {
        renderGeneration++;
        recents = EmojiRecents.empty();
        recentsVisible = false;
        selected = EmojiCatalog.Category.SMILEYS;
        query = "";
        searchEditing = false;
        adapter.replace(List.of(), renderGeneration);
        empty.setVisibility(View.GONE);
        searchQuery.setText("");
        searchHeader.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        sectionTitle.setVisibility(View.VISIBLE);
        gridFrame.setVisibility(View.VISIBLE);
        categoryScroller.setVisibility(View.VISIBLE);
        root.setMinimumHeight(dp(PANEL_MINIMUM_HEIGHT_DP));
        updateCategories();
    }

    private void beginSearchEditing() {
        if (searchEditing) return;
        searchEditing = true;
        renderGeneration++;
        adapter.replace(List.of(), renderGeneration);
        root.setMinimumHeight(dp(SEARCH_OVERLAY_HEIGHT_DP));
        normalHeader.setVisibility(View.GONE);
        sectionTitle.setVisibility(View.GONE);
        gridFrame.setVisibility(View.GONE);
        categoryScroller.setVisibility(View.GONE);
        searchHeader.setVisibility(View.VISIBLE);
        updateSearchQuery();
        listener.onSearchEditingChanged(true);
    }

    private void clearSearchQuery() {
        query = "";
        updateSearchQuery();
    }

    private void addCategory(EmojiCatalog.Category category, String label, int description) {
        Button button = textAction(label, description, ignored -> selectCategory(category));
        button.setTag("opentypeless-emoji-category-" + category.name().toLowerCase(Locale.ROOT));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        categoryButtons.put(category, button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(MINIMUM_TOUCH_TARGET_DP), dp(MINIMUM_TOUCH_TARGET_DP));
        params.setMarginEnd(dp(2));
        categoryStrip.addView(button, params);
    }

    private void selectCategory(EmojiCatalog.Category category) {
        if (category == EmojiCatalog.Category.RECENT && !recentsVisible) return;
        selected = category;
        query = "";
        renderSelected();
        Button button = categoryButtons.get(category);
        categoryScroller.post(() -> categoryScroller.smoothScrollTo(button.getLeft(), 0));
    }

    private void renderSelected() {
        renderGeneration++;
        long generation = renderGeneration;
        List<EmojiCatalog.Entry> entries;
        if (!query.isBlank()) {
            entries = EmojiCatalog.search(query);
        } else if (selected == EmojiCatalog.Category.RECENT) {
            ArrayList<EmojiCatalog.Entry> recentEntries = new ArrayList<>();
            for (String emoji : recents.entries()) {
                EmojiCatalog.Entry entry = EmojiCatalog.find(emoji);
                if (entry != null) recentEntries.add(entry);
            }
            entries = List.copyOf(recentEntries);
        } else {
            entries = EmojiCatalog.entries(selected);
        }
        adapter.replace(entries, renderGeneration);
        boolean showEmpty = entries.isEmpty();
        empty.setText(query.isBlank()
                ? R.string.ime_emoji_recent_empty
                : R.string.ime_emoji_no_results);
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        grid.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        // A hidden GridView can ignore a synchronous selection reset and reopen at the old
        // adapter position. Reset after the panel becomes visible and reject stale posts.
        grid.post(() -> {
            if (generation == renderGeneration && adapter.getCount() > 0) {
                grid.setSelectionFromTop(0, 0);
            }
        });
        updateSectionTitle(entries.size());
        updateCategories();
    }

    private void updateSectionTitle(int visibleCount) {
        if (!query.isBlank()) {
            sectionTitle.setText(context.getString(
                    R.string.ime_emoji_search_result_count, query, visibleCount));
            return;
        }
        sectionTitle.setText(context.getString(
                R.string.ime_emoji_category_count,
                context.getString(categoryLabel(selected)),
                visibleCount));
    }

    private void updateSearchQuery() {
        List<EmojiCatalog.Entry> matches = EmojiCatalog.search(query);
        searchQuery.setText(query.isEmpty()
                ? context.getString(R.string.ime_emoji_search_placeholder)
                : context.getString(R.string.ime_emoji_search_query_count, query, matches.size()));
        searchQuery.setTextColor(context.getColor(query.isEmpty()
                ? R.color.ime_on_surface_variant
                : R.color.ime_on_surface));
        searchClear.setEnabled(!query.isEmpty());
    }

    private void updateCategories() {
        for (Map.Entry<EmojiCatalog.Category, Button> item : categoryButtons.entrySet()) {
            boolean active = query.isBlank() && item.getKey() == selected;
            Button button = item.getValue();
            button.setSelected(active);
            button.setBackgroundResource(active
                    ? R.drawable.ime_clipboard_tab_selected
                    : R.drawable.ime_clipboard_tab_default);
        }
    }

    private int categoryLabel(EmojiCatalog.Category category) {
        return switch (category) {
            case RECENT -> R.string.ime_emoji_category_recent;
            case SMILEYS -> R.string.ime_emoji_category_smileys;
            case PEOPLE -> R.string.ime_emoji_category_people;
            case ANIMALS -> R.string.ime_emoji_category_animals;
            case FOOD -> R.string.ime_emoji_category_food;
            case ACTIVITIES -> R.string.ime_emoji_category_activities;
            case TRAVEL -> R.string.ime_emoji_category_travel;
            case OBJECTS -> R.string.ime_emoji_category_objects;
            case SYMBOLS -> R.string.ime_emoji_category_symbols;
            case FLAGS -> R.string.ime_emoji_category_flags;
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
            String label,
            int descriptionResource,
            View.OnClickListener action) {
        Button button = new Button(context);
        button.setText(label);
        button.setContentDescription(context.getString(descriptionResource));
        styleTextAction(button);
        button.setOnClickListener(action);
        return button;
    }

    private Button textAction(
            int labelResource,
            int descriptionResource,
            View.OnClickListener action) {
        Button button = textAction(context.getString(labelResource), descriptionResource, action);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return button;
    }

    private void styleTextAction(Button button) {
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(context.getColor(R.color.ime_on_surface));
        button.setBackgroundResource(R.drawable.ime_clipboard_tab_default);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
        button.setElevation(0f);
        button.setStateListAnimator(null);
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

    private static FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class EmojiAdapter extends BaseAdapter {
        private List<EmojiCatalog.Entry> entries = List.of();
        private long generation;

        void replace(List<EmojiCatalog.Entry> next, long nextGeneration) {
            entries = List.copyOf(next);
            generation = nextGeneration;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public EmojiCatalog.Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            Button button = recycled instanceof Button ? (Button) recycled : new Button(context);
            EmojiCatalog.Entry entry = getItem(position);
            long boundGeneration = generation;
            button.setTag("opentypeless-emoji-" + codePointTag(entry.emoji()));
            button.setText(entry.emoji());
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            button.setContentDescription(context.getString(
                    R.string.ime_cd_insert_named_emoji,
                    entry.localizedName(simplifiedChinese),
                    entry.emoji()));
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setTextColor(context.getColor(R.color.ime_on_surface));
            button.setBackgroundResource(R.drawable.ime_emoji_cell_background);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(dp(MINIMUM_TOUCH_TARGET_DP));
            button.setMinimumWidth(dp(MINIMUM_TOUCH_TARGET_DP));
            button.setMinHeight(dp(MINIMUM_TOUCH_TARGET_DP));
            button.setMinimumHeight(dp(MINIMUM_TOUCH_TARGET_DP));
            button.setElevation(0f);
            button.setStateListAnimator(null);
            button.setLayoutParams(new GridView.LayoutParams(
                    dp(MINIMUM_TOUCH_TARGET_DP), dp(MINIMUM_TOUCH_TARGET_DP)));
            button.setOnClickListener(ignored -> {
                if (boundGeneration == renderGeneration) {
                    listener.onEmojiSelected(entry.emoji());
                }
            });
            return button;
        }
    }

    private static String codePointTag(String emoji) {
        ArrayList<String> values = new ArrayList<>();
        emoji.codePoints().forEach(value -> values.add(Integer.toHexString(value)));
        return String.join("-", values);
    }
}
