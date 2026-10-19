package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SystemDLC-style mod menu:
 * - Sidebar categories (tappable)
 * - Module cards in a 3-column grid
 * - Tap card header → settings panel slides down; tap again → slides up
 * - Toggles / values persist in SharedPreferences
 */
final class SystemDlcMenuController {
    private static final String PREFS = "system_dlc_menu";
    private static final int ACCENT = 0xFFE8EAED;
    private static final int TEXT = 0xFFF2F4F8;
    private static final int MUTED = 0xFF9AA3B5;
    private static final int DIM = 0xFF5C6370;

    private final Activity activity;
    private final View root;
    private final SharedPreferences prefs;
    private final LinearLayout moduleHost;
    private final TextView topCategory;
    private final List<View> categoryViews = new ArrayList<>();
    private final String[] categoryNames = {
            "All", "Combat", "Movement", "Player", "Render", "Visual", "Client"
    };
    private final Map<String, List<ModuleDef>> modulesByCategory = new LinkedHashMap<>();
    private String expandedId = null;
    private String activeCategory = "Combat";
    private boolean favoritesOnly = false;
    private boolean sidebarVisible = true;
    private boolean russian = false;
    private boolean lightTheme = false;
    private View sidebar;
    private ImageButton btnFavorites;
    private ImageButton btnTheme;

    SystemDlcMenuController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.moduleHost = root.findViewById(R.id.sys_module_host);
        this.topCategory = root.findViewById(R.id.sys_top_category);
        seedModules();
    }

    void attach() {
        sidebar = root.findViewById(R.id.sys_sidebar);
        sidebarVisible = prefs.getBoolean("sidebar_visible", true);
        russian = prefs.getBoolean("lang_ru", false);
        lightTheme = prefs.getBoolean("theme_light", false);
        favoritesOnly = prefs.getBoolean("favorites_only", false);

        applySidebarVisibility();
        applyThemeChrome();

        View btnSidebar = root.findViewById(R.id.sys_btn_sidebar);
        if (btnSidebar != null) {
            btnSidebar.setOnClickListener(v -> {
                sidebarVisible = !sidebarVisible;
                prefs.edit().putBoolean("sidebar_visible", sidebarVisible).apply();
                applySidebarVisibility();
            });
        }

        btnFavorites = root.findViewById(R.id.sys_btn_favorites);
        if (btnFavorites != null) {
            updateFavoritesButton();
            btnFavorites.setOnClickListener(v -> {
                favoritesOnly = !favoritesOnly;
                prefs.edit().putBoolean("favorites_only", favoritesOnly).apply();
                updateFavoritesButton();
                rebuildGrid(currentQuery());
            });
        }

        View btnLang = root.findViewById(R.id.sys_btn_language);
        if (btnLang != null) {
            btnLang.setOnClickListener(v -> {
                russian = !russian;
                prefs.edit().putBoolean("lang_ru", russian).apply();
                applyLanguageLabels();
                Toast.makeText(activity, russian ? "Русский" : "English", Toast.LENGTH_SHORT).show();
                rebuildGrid(currentQuery());
            });
        }

        btnTheme = root.findViewById(R.id.sys_btn_theme);
        if (btnTheme != null) {
            updateThemeButton();
            btnTheme.setOnClickListener(v -> {
                lightTheme = !lightTheme;
                prefs.edit().putBoolean("theme_light", lightTheme).apply();
                updateThemeButton();
                applyThemeChrome();
                rebuildGrid(currentQuery());
                Toast.makeText(activity, lightTheme ? "Light mode" : "Night mode", Toast.LENGTH_SHORT).show();
            });
        }

        // close is already wired by ModMenuOverlay via btn_close_menu

        int[] catIds = {
                R.id.sys_cat_all, R.id.sys_cat_combat, R.id.sys_cat_movement,
                R.id.sys_cat_player, R.id.sys_cat_render, R.id.sys_cat_visual, R.id.sys_cat_client
        };
        for (int i = 0; i < catIds.length; i++) {
            View row = root.findViewById(catIds[i]);
            if (row == null) continue;
            categoryViews.add(row);
            final int idx = i;
            row.setOnClickListener(v -> selectCategory(categoryNames[idx]));
        }

        View configs = root.findViewById(R.id.sys_nav_configs);
        if (configs != null) configs.setOnClickListener(v -> openConfigsPanel());

        View friends = root.findViewById(R.id.sys_nav_friends);
        if (friends != null) friends.setOnClickListener(v ->
                Toast.makeText(activity, "Friends", Toast.LENGTH_SHORT).show());

        View waypoints = root.findViewById(R.id.sys_nav_waypoints);
        if (waypoints != null) waypoints.setOnClickListener(v ->
                Toast.makeText(activity, "Waypoints", Toast.LENGTH_SHORT).show());

        View clientSettings = root.findViewById(R.id.sys_nav_client_settings);
        if (clientSettings != null) clientSettings.setOnClickListener(v -> openClientSettingsPanel());

        View navSearch = root.findViewById(R.id.sys_nav_search);
        if (navSearch != null) navSearch.setOnClickListener(v -> {
            EditText search = root.findViewById(R.id.sys_search);
            if (search != null) search.requestFocus();
        });

        EditText search = root.findViewById(R.id.sys_search);
        if (search != null) {
            search.setHint(russian ? "Поиск" : "Search");
            search.addTextChangedListener(new SimpleTextWatcher(s -> rebuildGrid(s)));
        }

        applyLanguageLabels();
        applyPanelStyle();
        selectCategory("Combat");
    }

    private void applySidebarVisibility() {
        if (sidebar == null) return;
        // Always stay INSIDE the window. Expanded = labels; collapsed = icon rail (reference style).
        sidebar.setVisibility(View.VISIBLE);
        final float density = activity.getResources().getDisplayMetrics().density;
        final int expandedPx = (int) (200f * density + 0.5f);
        final int railPx = (int) (56f * density + 0.5f);
        ViewGroup.LayoutParams lp = sidebar.getLayoutParams();
        if (lp != null) {
            lp.width = sidebarVisible ? expandedPx : railPx;
            sidebar.setLayoutParams(lp);
        }
        setSidebarTextsVisible(sidebarVisible);
        // Hide footer brand strip text in rail mode; keep the "S" avatar
        View footer = root.findViewById(R.id.sys_footer_row);
        if (footer instanceof ViewGroup) {
            ViewGroup fg = (ViewGroup) footer;
            for (int i = 0; i < fg.getChildCount(); i++) {
                View c = fg.getChildAt(i);
                if (c instanceof android.widget.TextView) {
                    c.setVisibility(sidebarVisible ? View.VISIBLE : View.GONE);
                } else if (c instanceof ViewGroup) {
                    // the name+online column
                    c.setVisibility(sidebarVisible ? View.VISIBLE : View.GONE);
                }
            }
        }
        // brand text only when expanded
        View brand = root.findViewById(R.id.sys_brand_text);
        if (brand != null) brand.setVisibility(sidebarVisible ? View.VISIBLE : View.GONE);
        View brandRow = root.findViewById(R.id.sys_brand_row);
        if (brandRow instanceof LinearLayout) {
            ((LinearLayout) brandRow).setGravity(sidebarVisible
                    ? android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START
                    : android.view.Gravity.CENTER);
        }
    }

    private void setSidebarTextsVisible(boolean visible) {
        int[] textIds = {
                R.id.sys_label_categories, R.id.sys_label_settings,
                R.id.sys_cat_all_text, R.id.sys_cat_combat_text, R.id.sys_cat_movement_text,
                R.id.sys_cat_player_text, R.id.sys_cat_render_text, R.id.sys_cat_visual_text,
                R.id.sys_cat_client_text, R.id.sys_cat_combat_count,
                R.id.sys_nav_configs_text, R.id.sys_nav_friends_text, R.id.sys_nav_waypoints_text,
                R.id.sys_nav_client_settings_text, R.id.sys_nav_search_text
        };
        for (int id : textIds) {
            View v = root.findViewById(id);
            if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        // nav rows: center icons when rail
        int[] rowIds = {
                R.id.sys_cat_all, R.id.sys_cat_combat, R.id.sys_cat_movement,
                R.id.sys_cat_player, R.id.sys_cat_render, R.id.sys_cat_visual, R.id.sys_cat_client,
                R.id.sys_nav_configs, R.id.sys_nav_friends, R.id.sys_nav_waypoints,
                R.id.sys_nav_client_settings, R.id.sys_nav_search
        };
        for (int id : rowIds) {
            View row = root.findViewById(id);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout g = (LinearLayout) row;
            g.setGravity(visible
                    ? android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START
                    : android.view.Gravity.CENTER);
            int hPad = (int) ((visible ? 10f : 0f) * activity.getResources().getDisplayMetrics().density);
            row.setPadding(hPad, row.getPaddingTop(), hPad, row.getPaddingBottom());
        }
    }

    private void updateFavoritesButton() {
        if (btnFavorites == null) return;
        btnFavorites.setBackgroundResource(favoritesOnly
                ? R.drawable.bg_sys_icon_btn_active
                : R.drawable.bg_sys_icon_btn);
    }

    private void updateThemeButton() {
        if (btnTheme == null) return;
        btnTheme.setImageResource(lightTheme ? R.drawable.ic_sys_sun : R.drawable.ic_sys_moon);
    }

    private void applyThemeChrome() {
        View container = root.findViewById(R.id.mod_menu_container);
        // Keep dark SystemDLC chrome for now; light mode softens panel via alpha tags later
        if (container != null) {
            container.setAlpha(1f);
        }
    }

    private void applyLanguageLabels() {
        String[] en = {"All", "Combat", "Movement", "Player", "Render", "Visual", "Client"};
        String[] ru = {"Все", "Бой", "Движение", "Игрок", "Рендер", "Визуал", "Клиент"};
        String[] labels = russian ? ru : en;
        int[] textIds = {
                R.id.sys_cat_all_text, R.id.sys_cat_combat_text, R.id.sys_cat_movement_text,
                R.id.sys_cat_player_text, R.id.sys_cat_render_text, R.id.sys_cat_visual_text, R.id.sys_cat_client_text
        };
        for (int i = 0; i < textIds.length && i < labels.length; i++) {
            TextView tv = root.findViewById(textIds[i]);
            if (tv != null) tv.setText(labels[i]);
        }
        TextView configsT = root.findViewById(R.id.sys_nav_configs_text);
        if (configsT != null) configsT.setText(russian ? "Конфиги" : "Configs");
        TextView csT = root.findViewById(R.id.sys_nav_client_settings_text);
        if (csT != null) csT.setText(russian ? "Настройки клиента" : "Client Settings");
        TextView catLabel = root.findViewById(R.id.sys_label_categories);
        if (catLabel != null) catLabel.setText(russian ? "Категории" : "Client categories");
        TextView setLabel = root.findViewById(R.id.sys_label_settings);
        if (setLabel != null) setLabel.setText(russian ? "Настройки" : "Settings");
        EditText search = root.findViewById(R.id.sys_search);
        if (search != null) search.setHint(russian ? "Поиск" : "Search");
        if (topCategory != null) {
            int idx = 0;
            for (int i = 0; i < categoryNames.length; i++) {
                if (categoryNames[i].equals(activeCategory)) { idx = i; break; }
            }
            topCategory.setText(labels[idx]);
        }
    }

    private void openConfigsPanel() {
        if (topCategory != null) topCategory.setText(russian ? "Конфиги" : "Configs");
        if (moduleHost == null) return;
        moduleHost.removeAllViews();
        expandedId = null;

        String[][] configs = {
                {"Legit", "Safe visuals, low risk"},
                {"Balanced", "Default SystemClient setup"},
                {"Rage", "All combat modules aggressive"},
                {"HvH", "High-intensity PvP preset"},
                {"Minimal", "HUD + fullbright only"}
        };
        String active = prefs.getString("active_config", "Balanced");
        for (String[] cfg : configs) {
            final String name = cfg[0];
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(name.equals(active)
                    ? R.drawable.bg_sys_module_card_expanded
                    : R.drawable.bg_sys_module_card);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);

            TextView title = new TextView(activity);
            title.setText(name);
            title.setTextColor(TEXT);
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            TextView desc = new TextView(activity);
            desc.setText(cfg[1]);
            desc.setTextColor(MUTED);
            desc.setTextSize(12);
            card.addView(title);
            card.addView(desc);
            card.setOnClickListener(v -> {
                prefs.edit().putString("active_config", name).apply();
                Toast.makeText(activity, "Config: " + name, Toast.LENGTH_SHORT).show();
                openConfigsPanel();
            });
            moduleHost.addView(card);
        }
    }

    private void openClientSettingsPanel() {
        if (topCategory != null) topCategory.setText(russian ? "Настройки клиента" : "Client Settings");
        if (moduleHost == null) return;
        moduleHost.removeAllViews();
        expandedId = null;

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_sys_module_card);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        moduleHost.addView(panel);

        TextView head = new TextView(activity);
        head.setText(russian ? "Клиент" : "Client");
        head.setTextColor(TEXT);
        head.setTextSize(16);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(head);

        // Theme row already on top bar — mirror here
        addToggleRow(panel, russian ? "Светлая тема" : "Light theme", "theme_light", lightTheme);
        Switch themeSwitch = null;
        // re-bind lightTheme field when toggled via panel - use listener after add
        // UI size
        addLabel(panel, russian ? "Размер UI" : "UI size", russian ? "Масштаб меню" : "Menu scale");
        addSeek(panel, "ui_scale", 100, v -> v + "%");

        // Transparent / Simple mode chips
        addLabel(panel, russian ? "Стиль панели" : "Panel style", null);
        LinearLayout modeRow = new LinearLayout(activity);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        modeRow.setLayoutParams(mlp);

        String style = prefs.getString("panel_style", "simple");
        TextView simple = makeStyleChip(russian ? "Простой" : "Simple", "simple".equals(style));
        TextView transparent = makeStyleChip(russian ? "Прозрачный" : "Transparent", "transparent".equals(style));
        simple.setOnClickListener(v -> {
            prefs.edit().putString("panel_style", "simple").apply();
            applyPanelStyle();
            openClientSettingsPanel();
        });
        transparent.setOnClickListener(v -> {
            prefs.edit().putString("panel_style", "transparent").apply();
            applyPanelStyle();
            openClientSettingsPanel();
        });
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        simple.setLayoutParams(chipLp);
        LinearLayout.LayoutParams chipLp2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        chipLp2.setMarginStart(dp(8));
        transparent.setLayoutParams(chipLp2);
        modeRow.addView(simple);
        modeRow.addView(transparent);
        panel.addView(modeRow);

        TextView hint = new TextView(activity);
        hint.setText(russian
                ? "Прозрачный делает меню более прозрачным. Простой — стандартная тема."
                : "Transparent softens the panel glass. Simple keeps the default client theme.");
        hint.setTextColor(DIM);
        hint.setTextSize(11);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        hint.setLayoutParams(hlp);
        panel.addView(hint);

        applyPanelStyle();
    }

    private TextView makeStyleChip(String label, boolean active) {
        TextView t = new TextView(activity);
        t.setText(label);
        t.setGravity(android.view.Gravity.CENTER);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setBackgroundResource(active ? R.drawable.bg_sys_module_card_expanded : R.drawable.bg_sys_input);
        return t;
    }

    private void applyPanelStyle() {
        View container = root.findViewById(R.id.mod_menu_container);
        if (container == null) return;
        String style = prefs.getString("panel_style", "simple");
        container.setAlpha("transparent".equals(style) ? 0.82f : 1f);
    }

    private void wireNav(int id, String name) {
        View v = root.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(view -> {
            if (topCategory != null) topCategory.setText(name);
            Toast.makeText(activity, name + " opened", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectCategory(String name) {
        activeCategory = name;
        expandedId = null;
        for (int i = 0; i < categoryViews.size(); i++) {
            View row = categoryViews.get(i);
            boolean sel = categoryNames[i].equals(name);
            row.setSelected(sel);
            // tint label text inside row
            if (row instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) row;
                for (int c = 0; c < vg.getChildCount(); c++) {
                    View child = vg.getChildAt(c);
                    if (child instanceof TextView && child.getId() != R.id.sys_cat_combat_count) {
                        TextView tv = (TextView) child;
                        tv.setTextColor(sel ? TEXT : MUTED);
                        tv.setTypeface(Typeface.create(Typeface.DEFAULT, sel ? Typeface.BOLD : Typeface.NORMAL));
                    }
                }
            }
        }
        if (topCategory != null) {
            // show localized label
            String[] en = {"All", "Combat", "Movement", "Player", "Render", "Visual", "Client"};
            String[] ru = {"Все", "Бой", "Движение", "Игрок", "Рендер", "Визуал", "Клиент"};
            String label = name;
            for (int i = 0; i < en.length; i++) {
                if (en[i].equals(name)) { label = russian ? ru[i] : en[i]; break; }
            }
            topCategory.setText(label);
        }
        rebuildGrid("");
    }

    private void rebuildGrid(String query) {
        if (moduleHost == null) return;
        moduleHost.removeAllViews();

        List<ModuleDef> list = new ArrayList<>();
        if ("All".equals(activeCategory)) {
            for (List<ModuleDef> group : modulesByCategory.values()) list.addAll(group);
        } else {
            List<ModuleDef> group = modulesByCategory.get(activeCategory);
            if (group != null) list.addAll(group);
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (!q.isEmpty() || favoritesOnly) {
            List<ModuleDef> filtered = new ArrayList<>();
            for (ModuleDef m : list) {
                if (!q.isEmpty() && !m.title.toLowerCase(Locale.US).contains(q)) continue;
                if (favoritesOnly && !prefs.getBoolean(m.id + "_fav", false)) continue;
                filtered.add(m);
            }
            list = filtered;
        }

        // 3-column rows
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                moduleHost.addView(row);
            }
            View card = buildModuleCard(list.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int gap = dp(8);
            lp.setMargins(i % 3 == 0 ? 0 : gap, gap, 0, 0);
            card.setLayoutParams(lp);
            row.addView(card);
        }
        // pad incomplete row
        if (row != null && list.size() % 3 != 0) {
            int missing = 3 - (list.size() % 3);
            for (int i = 0; i < missing; i++) {
                View spacer = new View(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.setMargins(dp(8), 0, 0, 0);
                spacer.setLayoutParams(lp);
                row.addView(spacer);
            }
        }
    }

    private View buildModuleCard(ModuleDef mod) {
        final boolean on = prefs.getBoolean(mod.id + "_on", mod.defaultOn);
        final boolean fav = prefs.getBoolean(mod.id + "_fav", false);
        final boolean expanded = mod.id.equals(expandedId);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        // ON = brighter rim/fill; OFF = dimmer (reference look)
        if (expanded) {
            card.setBackgroundResource(R.drawable.bg_sys_module_card_expanded);
        } else {
            card.setBackgroundResource(on ? R.drawable.bg_sys_module_card_on : R.drawable.bg_sys_module_card);
        }
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setMinimumHeight(dp(52));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Left module icon (swords style)
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_mod_swords);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
        // Dim icon when module is off
        icon.setAlpha(on ? 1f : 0.45f);

        TextView title = new TextView(activity);
        title.setText(mod.title);
        title.setTextColor(on ? 0xFFF2F4F8 : 0xFF8B929E);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(12));
        title.setLayoutParams(titleLp);

        // Outline star (no keybind badge)
        ImageView star = new ImageView(activity);
        star.setImageResource(fav ? R.drawable.ic_sys_star_filled : R.drawable.ic_sys_star_outline);
        star.setAlpha(on ? 1f : 0.55f);
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        starLp.setMarginEnd(dp(10));
        star.setLayoutParams(starLp);
        star.setOnClickListener(v -> {
            boolean now = !prefs.getBoolean(mod.id + "_fav", false);
            prefs.edit().putBoolean(mod.id + "_fav", now).apply();
            star.setImageResource(now ? R.drawable.ic_sys_star_filled : R.drawable.ic_sys_star_outline);
        });

        Switch toggle = new Switch(activity);
        toggle.setChecked(on);
        toggle.setTrackResource(R.drawable.bg_sys_switch_track);
        toggle.setThumbResource(R.drawable.bg_sys_switch_thumb);
        // avoid default green/teal tint fighting our drawables
        try {
            toggle.getTrackDrawable().setTintList(null);
            toggle.getThumbDrawable().setTintList(null);
        } catch (Throwable ignored) {}
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(mod.id + "_on", checked).apply();
            // live visual update without full rebuild
            card.setBackgroundResource(checked
                    ? (mod.id.equals(expandedId) ? R.drawable.bg_sys_module_card_expanded : R.drawable.bg_sys_module_card_on)
                    : R.drawable.bg_sys_module_card);
            title.setTextColor(checked ? 0xFFF2F4F8 : 0xFF8B929E);
            icon.setAlpha(checked ? 1f : 0.45f);
            star.setAlpha(checked ? 1f : 0.55f);
        });

        header.addView(icon);
        header.addView(title);
        header.addView(star);
        header.addView(toggle);
        card.addView(header);

        // Expandable settings
        LinearLayout settings = new LinearLayout(activity);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setBackgroundResource(R.drawable.bg_sys_settings_panel);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsLp.topMargin = dp(10);
        settings.setLayoutParams(settingsLp);
        settings.setVisibility(expanded ? View.VISIBLE : View.GONE);

        fillSettings(settings, mod);
        card.addView(settings);

        // Tap header (not switch/star) toggles expand
        header.setOnClickListener(v -> {
            if (mod.id.equals(expandedId)) {
                // collapse
                Animation up = AnimationUtils.loadAnimation(activity, R.anim.sys_slide_up);
                up.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationEnd(Animation animation) {
                        expandedId = null;
                        rebuildGrid(currentQuery());
                    }
                    @Override public void onAnimationRepeat(Animation animation) {}
                });
                settings.startAnimation(up);
            } else {
                expandedId = mod.id;
                rebuildGrid(currentQuery());
                // after rebuild, animate the new expanded panel
                moduleHost.post(() -> {
                    // find expanded settings and animate
                    animateExpandedIn();
                });
            }
        });

        return card;
    }

    private void animateExpandedIn() {
        // best-effort: animate last visible settings panel
        for (int i = 0; i < moduleHost.getChildCount(); i++) {
            View row = moduleHost.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            ViewGroup vg = (ViewGroup) row;
            for (int j = 0; j < vg.getChildCount(); j++) {
                View card = vg.getChildAt(j);
                if (!(card instanceof ViewGroup)) continue;
                ViewGroup cg = (ViewGroup) card;
                if (cg.getChildCount() < 2) continue;
                View settings = cg.getChildAt(1);
                if (settings.getVisibility() == View.VISIBLE) {
                    Animation down = AnimationUtils.loadAnimation(activity, R.anim.sys_slide_down);
                    settings.startAnimation(down);
                }
            }
        }
    }

    private void fillSettings(LinearLayout panel, ModuleDef mod) {
        switch (mod.kind) {
            case "function":
                addLabel(panel, "Preset", "Pick a preset to bias the function.");
                addChoice(panel, mod.id + "_preset", new String[]{"Balanced", "Aggressive", "Safe"}, "Balanced");
                addLabel(panel, "Mode", "How aggressively the function runs.");
                addChoice(panel, mod.id + "_mode", new String[]{"Smart", "Always", "Legit"}, "Smart");
                addLabel(panel, "Range", null);
                addSeek(panel, mod.id + "_range", 28, v -> String.format(Locale.US, "%.1f blocks", v / 10f));
                addToggleRow(panel, "Sync to TPS", mod.id + "_tps", false);
                addLabel(panel, "Hotkey", null);
                addReadonly(panel, prefs.getString(mod.id + "_hotkey", "^K"));
                addLabel(panel, "Accent colour", null);
                addAccentDots(panel, mod.id + "_accent");
                break;
            case "preview":
                addLabel(panel, "Layout", "Pick which layout the preview uses.");
                addChoice(panel, mod.id + "_layout", new String[]{"Compact", "Comfortable", "Spacious"}, "Compact");
                addLabel(panel, "Display mode", null);
                addChoice(panel, mod.id + "_display", new String[]{"Normal", "Transparent"}, "Transparent");
                addLabel(panel, "Client colour", null);
                addChoice(panel, mod.id + "_colour", new String[]{"Default", "Custom", "Dark", "Light"}, "Default");
                addLabel(panel, "Styling", "Fine-tune the style of each parameter.");
                addSeek(panel, mod.id + "_intensity", 88, v -> String.format(Locale.US, "%.2f", v / 10f));
                addSeek(panel, mod.id + "_refraction", 40, v -> String.format(Locale.US, "%.1f", v / 100f));
                addToggleRow(panel, "Chromatic", mod.id + "_chromatic", false);
                break;
            case "elements":
                addLabel(panel, "Style", null);
                addChoice(panel, mod.id + "_style", new String[]{"Flat", "Glass", "Minimal"}, "Flat");
                addLabel(panel, "Displayed elements", null);
                addToggleRow(panel, "Waypoints", mod.id + "_el_wp", true);
                addToggleRow(panel, "StaffList", mod.id + "_el_staff", true);
                addToggleRow(panel, "Potions", mod.id + "_el_pot", true);
                addToggleRow(panel, "Keybinds", mod.id + "_el_kb", true);
                addToggleRow(panel, "Notifications", mod.id + "_el_nt", true);
                addButton(panel, "Reset positions", () -> {
                    Toast.makeText(activity, "Positions reset", Toast.LENGTH_SHORT).show();
                });
                addLabel(panel, "Accent colour", null);
                addAccentDots(panel, mod.id + "_accent");
                break;
            default:
                addLabel(panel, "Options", "Configure " + mod.title);
                addToggleRow(panel, "Enabled in menu", mod.id + "_detail", true);
                addSeek(panel, mod.id + "_strength", 50, v -> v + "%");
                break;
        }
    }

    private void addLabel(LinearLayout panel, String title, String desc) {
        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        panel.addView(t);
        if (desc != null) {
            TextView d = new TextView(activity);
            d.setText(desc);
            d.setTextColor(DIM);
            d.setTextSize(11);
            panel.addView(d);
        }
    }

    private void addReadonly(LinearLayout panel, String value) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_sys_input);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        panel.addView(t);
    }

    private void addChoice(LinearLayout panel, String key, String[] options, String def) {
        String current = prefs.getString(key, def);
        TextView t = new TextView(activity);
        t.setText(current);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackgroundResource(R.drawable.bg_sys_input);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(prefs.getString(key, def))) { idx = i; break; }
            }
            idx = (idx + 1) % options.length;
            prefs.edit().putString(key, options[idx]).apply();
            t.setText(options[idx]);
        });
        panel.addView(t);
    }

    private void addSeek(LinearLayout panel, String key, int def, ValueFmt fmt) {
        int cur = prefs.getInt(key, def);
        TextView value = new TextView(activity);
        value.setText(fmt.format(cur));
        value.setTextColor(MUTED);
        value.setTextSize(11);
        value.setGravity(Gravity.END);
        panel.addView(value);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(100);
        seek.setProgress(cur);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(fmt.format(progress));
                if (fromUser) prefs.edit().putInt(key, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(seek);
    }

    private void addToggleRow(LinearLayout panel, String label, String key, boolean def) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(activity);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(key, c).apply());
        row.addView(t);
        row.addView(sw);
        panel.addView(row);
    }

    private void addButton(LinearLayout panel, String label, Runnable action) {
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_sys_chip);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> action.run());
        panel.addView(t);
    }

    private void addAccentDots(LinearLayout panel, String key) {
        int[] colors = {0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFFE8EAED, 0xFF8E24AA};
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        int selected = prefs.getInt(key, ACCENT);
        for (int c : colors) {
            View dot = new View(activity);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(22), dp(22));
            dlp.setMarginEnd(dp(8));
            dot.setLayoutParams(dlp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(c);
            if (c == selected) gd.setStroke(dp(2), Color.WHITE);
            dot.setBackground(gd);
            final int color = c;
            dot.setOnClickListener(v -> {
                prefs.edit().putInt(key, color).apply();
                rebuildGrid(currentQuery());
            });
            row.addView(dot);
        }
        panel.addView(row);
    }

    private String currentQuery() {
        EditText search = root.findViewById(R.id.sys_search);
        return search != null ? search.getText().toString() : "";
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private void seedModules() {
        modulesByCategory.put("Combat", list(
                m("function", "Function", "None", true),
                m("preview", "Preview", "None", true),
                m("elements", "Elements", "None", true),
                m("notifications", "Notifications", "None", true),
                m("waypoints", "Waypoints", "N", false),
                m("interface", "Interface", "None", true)
        ));
        modulesByCategory.put("Movement", list(
                m("speed", "Speed", "None", false),
                m("sprint", "Auto Sprint", "None", true),
                m("jesus", "Jesus", "None", false)
        ));
        modulesByCategory.put("Player", list(
                m("scaffold", "Scaffold", "None", false),
                m("invwalk", "Inventory Walk", "None", false)
        ));
        modulesByCategory.put("Render", list(
                m("fullbright", "Fullbright", "None", true),
                m("esp", "ESP", "None", false),
                m("tracers", "Tracers", "None", false)
        ));
        modulesByCategory.put("Visual", list(
                m("animations", "Animations", "None", true),
                m("ambience", "Ambience", "None", false)
        ));
        modulesByCategory.put("Client", list(
                m("clickgui", "Click GUI", "RSHIFT", true),
                m("arraylist", "ArrayList", "None", true)
        ));
    }

    private static List<ModuleDef> list(ModuleDef... items) {
        List<ModuleDef> out = new ArrayList<>();
        for (ModuleDef m : items) out.add(m);
        return out;
    }

    private static ModuleDef m(String id, String title, String keybind, boolean on) {
        ModuleDef def = new ModuleDef();
        def.id = id;
        def.title = title;
        def.keybind = keybind;
        def.defaultOn = on;
        if ("function".equals(id) || "preview".equals(id) || "elements".equals(id)) def.kind = id;
        else def.kind = "generic";
        return def;
    }

    private static final class ModuleDef {
        String id;
        String title;
        String keybind;
        String kind;
        boolean defaultOn;
    }

    private interface ValueFmt {
        String format(int v);
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final java.util.function.Consumer<String> onChange;
        SimpleTextWatcher(java.util.function.Consumer<String> onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { onChange.accept(s.toString()); }
    }
}
