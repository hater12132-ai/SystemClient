package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.ModManager;
import org.levimc.launcher.core.mods.Mod;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final String[] categoryNames = {"All", "Player", "HUD", "Visual", "Client", "Combat", "Movement", "Render"};
    private final Map<String, List<ModuleDef>> modulesByCategory = new LinkedHashMap<>();
    private String expandedId = null;
    private FrameLayout settingsBubbleHost;
    /** module id -> bubble view (multiple open at once) */
    private final HashMap<String, View> openBubbles = new HashMap<>();
    private int bubbleSpawnOffset = 0;
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
        root.setTag(this); // stash controller for hide()
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
        String[] en = {"All", "Player", "HUD", "Visual", "Client", "Combat", "Movement", "Render"};
        String[] ru = {"Все", "Игрок", "HUD", "Визуал", "Клиент", "Бой", "Движение", "Рендер"};
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
        TextView glass = makeStyleChip(russian ? "Стекло" : "Liquid glass", "liquid_glass".equals(style));
        simple.setOnClickListener(v -> {
            prefs.edit().putString("panel_style", "simple").apply();
            applyPanelStyle();
            rebuildGrid(currentQuery());
            openClientSettingsPanel();
        });
        transparent.setOnClickListener(v -> {
            prefs.edit().putString("panel_style", "transparent").apply();
            applyPanelStyle();
            rebuildGrid(currentQuery());
            openClientSettingsPanel();
        });
        glass.setOnClickListener(v -> {
            prefs.edit().putString("panel_style", "liquid_glass").apply();
            applyPanelStyle();
            rebuildGrid(currentQuery());
            openClientSettingsPanel();
        });
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        simple.setLayoutParams(chipLp);
        LinearLayout.LayoutParams chipLp2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        chipLp2.setMarginStart(dp(6));
        transparent.setLayoutParams(chipLp2);
        LinearLayout.LayoutParams chipLp3 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        chipLp3.setMarginStart(dp(6));
        glass.setLayoutParams(chipLp3);
        modeRow.addView(simple);
        modeRow.addView(transparent);
        modeRow.addView(glass);
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

    private boolean isGlass() {
        return "liquid_glass".equals(prefs.getString("panel_style", "simple"));
    }

    private void applyPanelStyle() {
        View container = root.findViewById(R.id.mod_menu_container);
        if (container == null) return;
        String style = prefs.getString("panel_style", "simple");
        // Alpha: transparent fades whole panel; liquid_glass uses tinted drawables instead
        if ("transparent".equals(style)) {
            container.setAlpha(0.82f);
            container.setBackgroundResource(R.drawable.bg_sys_root);
        } else if ("liquid_glass".equals(style)) {
            container.setAlpha(1f);
            container.setBackgroundResource(R.drawable.bg_sys_root_glass);
        } else {
            container.setAlpha(1f);
            container.setBackgroundResource(R.drawable.bg_sys_root);
        }

        View sidebar = root.findViewById(R.id.sys_sidebar);
        if (sidebar != null) {
            sidebar.setBackgroundResource(isGlass()
                    ? R.drawable.bg_sys_sidebar_glass
                    : R.drawable.bg_sys_sidebar);
        }
        View footer = root.findViewById(R.id.sys_footer_row);
        if (footer != null) {
            footer.setBackgroundResource(isGlass()
                    ? R.drawable.bg_sys_footer_glass
                    : R.drawable.bg_sys_footer);
        }
        // Top bar chips / icon buttons
        int[] iconBtns = {
                R.id.sys_btn_sidebar, R.id.sys_btn_favorites, R.id.sys_btn_language,
                R.id.sys_btn_theme, R.id.btn_close_menu
        };
        for (int id : iconBtns) {
            View v = root.findViewById(id);
            if (v != null) {
                v.setBackgroundResource(isGlass()
                        ? R.drawable.bg_sys_icon_btn_glass
                        : R.drawable.bg_sys_icon_btn);
            }
        }
        View topCat = root.findViewById(R.id.sys_top_category);
        if (topCat != null) {
            topCat.setBackgroundResource(isGlass()
                    ? R.drawable.bg_sys_top_chip_glass
                    : R.drawable.bg_sys_top_chip);
        }
        // Search field parent is a LinearLayout with bg_sys_input — best-effort
        View search = root.findViewById(R.id.sys_search);
        if (search != null && search.getParent() instanceof View) {
            View parent = (View) search.getParent();
            try {
                parent.setBackgroundResource(isGlass()
                        ? R.drawable.bg_sys_input_glass
                        : R.drawable.bg_sys_input);
            } catch (Throwable ignored) {}
        }
        // Category rows use bg_sys_nav
        int[] rows = {
                R.id.sys_cat_all, R.id.sys_cat_combat, R.id.sys_cat_movement,
                R.id.sys_cat_player, R.id.sys_cat_render, R.id.sys_cat_visual, R.id.sys_cat_client,
                R.id.sys_nav_configs, R.id.sys_nav_friends, R.id.sys_nav_waypoints,
                R.id.sys_nav_client_settings, R.id.sys_nav_search
        };
        for (int id : rows) {
            View v = root.findViewById(id);
            if (v != null) {
                v.setBackgroundResource(isGlass()
                        ? R.drawable.bg_sys_nav_glass
                        : R.drawable.bg_sys_nav);
            }
        }
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
        dismissAllSettingsBubbles();
        expandedId = null;
        for (int i = 0; i < categoryViews.size(); i++) {
            View row = categoryViews.get(i);
            boolean sel = categoryNames[i].equals(name);
            row.setSelected(sel);
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
            // slow soft highlight pulse on the selected sidebar row
            if (sel) {
                row.setAlpha(0.55f);
                row.animate().alpha(1f).setDuration(380)
                        .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                row.setScaleX(0.97f);
                row.setScaleY(0.97f);
                row.animate().scaleX(1f).scaleY(1f).setDuration(420)
                        .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
        }
        if (topCategory != null) {
            String[] en = {"All", "Player", "HUD", "Visual", "Client", "Combat", "Movement", "Render"};
            String[] ru = {"Все", "Игрок", "HUD", "Визуал", "Клиент", "Бой", "Движение", "Рендер"};
            String label = name;
            for (int i = 0; i < en.length; i++) {
                if (en[i].equals(name)) { label = russian ? ru[i] : en[i]; break; }
            }
            topCategory.setAlpha(0.4f);
            topCategory.setText(label);
            topCategory.animate().alpha(1f).setDuration(360).start();
        }
        // fade grid out then rebuild with stagger-in
        if (moduleHost != null) {
            moduleHost.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> {
                        rebuildGrid("");
                        moduleHost.setAlpha(0f);
                        moduleHost.setTranslationY(dp(10));
                        moduleHost.animate().alpha(1f).translationY(0f).setDuration(360)
                                .setInterpolator(new DecelerateInterpolator(1.3f)).start();
                    }).start();
        } else {
            rebuildGrid("");
        }
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
        final boolean[] on = { prefs.getBoolean(mod.id + "_on", mod.defaultOn) };
        final boolean[] fav = { prefs.getBoolean(mod.id + "_fav", false) };

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        if (isGlass()) {
            card.setBackgroundResource(on[0]
                    ? R.drawable.bg_sys_module_card_glass_on
                    : R.drawable.bg_sys_module_card_glass);
        } else {
            card.setBackgroundResource(on[0] ? R.drawable.bg_sys_module_card_on : R.drawable.bg_sys_module_card);
        }
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setMinimumHeight(dp(50));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Title stack (name + short desc)
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginEnd(dp(8));
        titles.setLayoutParams(titlesLp);

        TextView title = new TextView(activity);
        title.setText(mod.title);
        title.setTextColor(on[0] ? 0xFFF2F4F8 : 0xFF7A808C);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);

        if (mod.desc != null && !mod.desc.isEmpty()) {
            TextView desc = new TextView(activity);
            desc.setText(mod.desc);
            desc.setTextColor(0xFF6A7080);
            desc.setTextSize(11);
            desc.setMaxLines(2);
            titles.addView(desc);
        }

        ImageView star = new ImageView(activity);
        star.setImageResource(fav[0] ? R.drawable.ic_sys_star_filled : R.drawable.ic_sys_star_outline);
        star.setAlpha(on[0] ? 1f : 0.5f);
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        starLp.setMarginEnd(dp(12));
        star.setLayoutParams(starLp);
        star.setOnClickListener(v -> {
            fav[0] = !fav[0];
            prefs.edit().putBoolean(mod.id + "_fav", fav[0]).apply();
            star.setImageResource(fav[0] ? R.drawable.ic_sys_star_filled : R.drawable.ic_sys_star_outline);
            if (favoritesOnly) rebuildGrid(currentQuery());
        });

        // Smooth sliding toggle
        final int trackW = dp(44);
        final int trackH = dp(26);
        final int thumbSize = dp(20);
        final int pad = dp(3);
        final float thumbOffX = pad;
        final float thumbOnX = trackW - thumbSize - pad;

        FrameLayout toggle = new FrameLayout(activity);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(trackW, trackH));
        toggle.setBackgroundResource(on[0] ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);

        final View thumb = new View(activity);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.CENTER_VERTICAL;
        thumb.setLayoutParams(thumbLp);
        thumb.setBackgroundResource(on[0] ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
        thumb.setTranslationX(on[0] ? thumbOnX : thumbOffX);
        toggle.addView(thumb);

        final boolean[] animating = { false };
        toggle.setOnClickListener(v -> {
            if (animating[0]) return;
            animating[0] = true;
            final boolean next = !on[0];
            on[0] = next;
            applyModuleEnabled(mod, next);

            ObjectAnimator slide = ObjectAnimator.ofFloat(thumb, "translationX",
                    thumb.getTranslationX(), next ? thumbOnX : thumbOffX);
            slide.setDuration(300);
            slide.setInterpolator(new DecelerateInterpolator(1.6f));

            int cFrom = next ? 0xFF2C2C32 : 0xFFF2F4F8;
            int cTo = next ? 0xFFF2F4F8 : 0xFF2C2C32;
            ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), cFrom, cTo);
            colorAnim.setDuration(300);
            colorAnim.addUpdateListener(a -> {
                android.graphics.drawable.GradientDrawable gd =
                        new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(trackH / 2f);
                gd.setColor((int) a.getAnimatedValue());
                toggle.setBackground(gd);
            });
            slide.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    animating[0] = false;
                    thumb.setBackgroundResource(next
                            ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
                    toggle.setBackgroundResource(next
                            ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);
                }
            });
            slide.start();
            colorAnim.start();

            title.setTextColor(next ? 0xFFF2F4F8 : 0xFF7A808C);
            star.setAlpha(next ? 1f : 0.5f);
            if (isGlass()) {
                card.setBackgroundResource(next
                        ? R.drawable.bg_sys_module_card_glass_on
                        : R.drawable.bg_sys_module_card_glass);
            } else {
                card.setBackgroundResource(next
                        ? R.drawable.bg_sys_module_card_on
                        : R.drawable.bg_sys_module_card);
            }
        });

        header.addView(titles);
        header.addView(star);
        header.addView(toggle);
        card.addView(header);

        // Tap card (not toggle/star) → settings bubble like reference UI
        card.setOnClickListener(v -> showSettingsBubble(mod));
        titles.setOnClickListener(v -> showSettingsBubble(mod));

        return card;
    }

    /** Close every open settings bubble (menu hide / category switch). */
    void dismissAllSettingsBubbles() {
        if (settingsBubbleHost == null) return;
        List<View> copy = new ArrayList<>(openBubbles.values());
        openBubbles.clear();
        for (View b : copy) {
            try {
                b.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(120)
                        .withEndAction(() -> {
                            try { settingsBubbleHost.removeView(b); } catch (Throwable ignored) {}
                            if (openBubbles.isEmpty() && settingsBubbleHost.getChildCount() == 0) {
                                settingsBubbleHost.setVisibility(View.GONE);
                            }
                        }).start();
            } catch (Throwable ignored) {
                try { settingsBubbleHost.removeView(b); } catch (Throwable ignored2) {}
            }
        }
        if (openBubbles.isEmpty()) {
            try {
                settingsBubbleHost.removeAllViews();
                settingsBubbleHost.setVisibility(View.GONE);
            } catch (Throwable ignored) {}
        }
        expandedId = null;
        bubbleSpawnOffset = 0;
    }

    private void dismissOneBubble(String modId, View bubble) {
        openBubbles.remove(modId);
        if (bubble == null) return;
        bubble.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                .withEndAction(() -> {
                    try {
                        if (settingsBubbleHost != null) settingsBubbleHost.removeView(bubble);
                    } catch (Throwable ignored) {}
                    if (openBubbles.isEmpty() && settingsBubbleHost != null
                            && settingsBubbleHost.getChildCount() == 0) {
                        settingsBubbleHost.setVisibility(View.GONE);
                    }
                }).start();
        if (modId.equals(expandedId)) expandedId = null;
    }

    private void ensureBubbleHost() {
        if (settingsBubbleHost != null) return;
        // Pass-through host: only children (bubbles) receive touches; empty area falls through
        settingsBubbleHost = new FrameLayout(activity) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return false;
            }
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                // Don't consume — but Android still won't deliver to views under us.
                // So we only size-wrap content by keeping host match_parent with
                // pointer-events via hit test: if no child under point, return false
                // and request parent to not block — use dispatchTouchEvent instead.
                return false;
            }
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                final float rawX = ev.getRawX();
                final float rawY = ev.getRawY();
                final int[] loc = new int[2];
                for (int i = getChildCount() - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if (child.getVisibility() != View.VISIBLE) continue;
                    child.getLocationOnScreen(loc);
                    int w = child.getWidth();
                    int h = child.getHeight();
                    if (rawX >= loc[0] && rawY >= loc[1]
                            && rawX < loc[0] + w && rawY < loc[1] + h) {
                        // offset event into child local coords
                        MotionEvent copy = MotionEvent.obtain(ev);
                        copy.offsetLocation(-(loc[0]), -(loc[1]));
                        boolean handled = child.dispatchTouchEvent(copy);
                        copy.recycle();
                        if (handled) return true;
                    }
                }
                return false; // fall through to menu under the host
            }
        };
        settingsBubbleHost.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsBubbleHost.setClickable(false);
        settingsBubbleHost.setFocusable(false);
        settingsBubbleHost.setBackgroundColor(Color.TRANSPARENT);
        ((ViewGroup) root).addView(settingsBubbleHost);
    }

    private void makeBubbleDraggable(View shell, View dragHandle) {
        final float[] last = new float[2];
        View.OnTouchListener drag = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    last[0] = event.getRawX();
                    last[1] = event.getRawY();
                    if (settingsBubbleHost != null) {
                        settingsBubbleHost.bringChildToFront(shell);
                        settingsBubbleHost.invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - last[0];
                    float dy = event.getRawY() - last[1];
                    last[0] = event.getRawX();
                    last[1] = event.getRawY();
                    shell.setTranslationX(shell.getTranslationX() + dx);
                    shell.setTranslationY(shell.getTranslationY() + dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return false;
        };
        dragHandle.setOnTouchListener(drag);
    }

    /** Corner resize: bottom-right, bottom-left, top-right, top-left grey grips. */
    private void attachResizeHandles(FrameLayout shell, int minW, int minH) {
        final int grip = dp(18);
        final int hit = dp(28);
        int[] corners = {
                Gravity.TOP | Gravity.START,
                Gravity.TOP | Gravity.END,
                Gravity.BOTTOM | Gravity.START,
                Gravity.BOTTOM | Gravity.END
        };
        for (int g : corners) {
            View handle = new View(activity);
            handle.setBackgroundResource(R.drawable.bg_sys_resize_grip);
            FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(grip, grip);
            hlp.gravity = g;
            int m = dp(4);
            hlp.setMargins(m, m, m, m);
            handle.setLayoutParams(hlp);
            // expand touch area
            handle.setPadding(0, 0, 0, 0);
            final int gravity = g;
            final float[] last = new float[2];
            final int[] startSize = new int[2];
            handle.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        last[0] = event.getRawX();
                        last[1] = event.getRawY();
                        startSize[0] = shell.getWidth();
                        startSize[1] = shell.getHeight();
                        if (settingsBubbleHost != null) {
                            settingsBubbleHost.bringChildToFront(shell);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - last[0];
                        float dy = event.getRawY() - last[1];
                        ViewGroup.LayoutParams lp = shell.getLayoutParams();
                        int w = startSize[0];
                        int h = startSize[1];
                        boolean left = (gravity & Gravity.START) == Gravity.START
                                || (gravity & Gravity.LEFT) == Gravity.LEFT;
                        boolean top = (gravity & Gravity.TOP) == Gravity.TOP;
                        boolean right = (gravity & Gravity.END) == Gravity.END
                                || (gravity & Gravity.RIGHT) == Gravity.RIGHT;
                        boolean bottom = (gravity & Gravity.BOTTOM) == Gravity.BOTTOM;

                        if (right) w = Math.max(minW, (int) (startSize[0] + dx));
                        if (left) {
                            w = Math.max(minW, (int) (startSize[0] - dx));
                            shell.setTranslationX(shell.getTranslationX() + (startSize[0] - w));
                            startSize[0] = w;
                            last[0] = event.getRawX();
                        }
                        if (bottom) h = Math.max(minH, (int) (startSize[1] + dy));
                        if (top) {
                            h = Math.max(minH, (int) (startSize[1] - dy));
                            shell.setTranslationY(shell.getTranslationY() + (startSize[1] - h));
                            startSize[1] = h;
                            last[1] = event.getRawY();
                        }
                        if (right) { startSize[0] = w; last[0] = event.getRawX(); }
                        if (bottom) { startSize[1] = h; last[1] = event.getRawY(); }
                        lp.width = w;
                        lp.height = h;
                        shell.setLayoutParams(lp);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            });
            shell.addView(handle);
        }
    }

    private void showSettingsBubble(ModuleDef mod) {
        if (openBubbles.containsKey(mod.id)) {
            View existing = openBubbles.get(mod.id);
            if (existing != null && settingsBubbleHost != null) {
                settingsBubbleHost.bringChildToFront(existing);
                settingsBubbleHost.invalidate();
                existing.animate().scaleX(1.03f).scaleY(1.03f).setDuration(80)
                        .withEndAction(() -> existing.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
            }
            return;
        }

        ensureBubbleHost();
        settingsBubbleHost.setVisibility(View.VISIBLE);
        settingsBubbleHost.setBackgroundColor(Color.TRANSPARENT);
        expandedId = mod.id;

        final int minW = dp(220);
        final int minH = dp(140);
        final int defaultW = dp(280);
        final int defaultH = ViewGroup.LayoutParams.WRAP_CONTENT;

        // Outer shell — solid floating window
        FrameLayout shell = new FrameLayout(activity);
        shell.setBackgroundResource(R.drawable.bg_sys_settings_bubble);
        shell.setElevation(dp(16));
        shell.setClickable(true);
        shell.setTag(mod.id);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.setPadding(0, 0, 0, 0);

        // === Compact drag bar: small ⋯ only ===
        LinearLayout titleBar = new LinearLayout(activity);
        titleBar.setOrientation(LinearLayout.VERTICAL);
        titleBar.setBackgroundResource(R.drawable.bg_sys_bubble_titlebar);
        titleBar.setGravity(Gravity.CENTER_HORIZONTAL);
        titleBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Small centered dots — low height so it doesn't look like a huge bar
        TextView dots = new TextView(activity);
        dots.setText("···");
        dots.setTextColor(0xFF7A7A82);
        dots.setTextSize(11);
        dots.setGravity(Gravity.CENTER);
        dots.setIncludeFontPadding(false);
        dots.setPadding(dp(20), dp(2), dp(20), dp(2));
        dots.setLetterSpacing(0.35f);
        titleBar.addView(dots);

        // Title + red close dot
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleRow.setPadding(dp(8), dp(2), dp(4), dp(4));

        TextView title = new TextView(activity);
        title.setText(mod.title);
        title.setTextColor(0xFFE8E8EC);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(title);

        // Red close dot — small visual, larger hitbox
        FrameLayout closeHit = new FrameLayout(activity);
        closeHit.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        closeHit.setPadding(dp(8), dp(8), dp(8), dp(8));
        View closeDot = new View(activity);
        FrameLayout.LayoutParams cdlp = new FrameLayout.LayoutParams(dp(10), dp(10));
        cdlp.gravity = Gravity.CENTER;
        closeDot.setLayoutParams(cdlp);
        closeDot.setBackgroundResource(R.drawable.bg_sys_close_dot);
        closeHit.addView(closeDot);
        closeHit.setOnClickListener(v -> dismissOneBubble(mod.id, shell));
        titleRow.addView(closeHit);

        titleBar.addView(titleRow);
        column.addView(titleBar);

        // Body: wrap height to content — ends at last setting (no forced max)
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(12));
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fillSettings(body, mod);
        column.addView(body);

        // If content is very tall, allow scroll without leaving empty space when short
        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setFillViewport(false);
        // restructure: column goes inside scroll for tall content only after measure
        // For simplicity: put column in scroll, height WRAP on shell
        shell.removeAllViews();
        scroll.addView(column);
        shell.addView(scroll);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(defaultW, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER;
        int offset = (bubbleSpawnOffset % 6) * dp(24);
        bubbleSpawnOffset++;
        shell.setLayoutParams(blp);
        shell.setTranslationX(offset);
        shell.setTranslationY(offset);

        openBubbles.put(mod.id, shell);
        settingsBubbleHost.addView(shell);

        makeBubbleDraggable(shell, titleBar);
        makeBubbleDraggable(shell, dots);

        // Cap only if taller than ~70% of screen — still ends at last item via scroll
        shell.post(() -> {
            int maxH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.72f);
            if (shell.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = shell.getLayoutParams();
                lp.height = maxH;
                shell.setLayoutParams(lp);
            }
        });

        shell.setAlpha(0f);
        shell.setScaleX(0.92f);
        shell.setScaleY(0.92f);
        shell.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateExpandedIn() {
        // kept no-op for any leftover callers
    }

    private void fillSettings(LinearLayout panel, ModuleDef mod) {
        if (mod.desc != null && !mod.desc.isEmpty()) {
            addLabel(panel, mod.title, mod.desc);
        }
        final String nn = systemModsModuleName(mod);
        final org.levimc.launcher.core.mods.ModManager mm =
                org.levimc.launcher.core.mods.ModManager.getInstance();
        // Hint: stock SO loads config at game start — toggle then relaunch MC if needed
        addLabel(panel, "Tip", "Toggle on, then Launch MC (or re-enter world) so SystemMods reloads config.");

        if (nn == null) {
            addLabel(panel, "Info", "No extra settings.");
            return;
        }

        switch (mod.id) {
            case "hitbox":
                addConfigToggle(panel, nn, "Show entities", "showEntities", true, mm);
                addConfigToggle(panel, nn, "Show players", "showPlayers", true, mm);
                addConfigToggle(panel, nn, "Show self", "showSelf", true, mm);
                addConfigToggle(panel, nn, "Eye line", "showEyeLine", true, mm);
                addConfigToggle(panel, nn, "Look line", "showLookLine", true, mm);
                addConfigSeek(panel, nn, "Look line length", "lookLineLength", 20, 1, 50, mm); // /10
                break;
            case "swingmodifier":
                addConfigToggle(panel, nn, "Flux swing", "m_fluxSwing", false, mm);
                addConfigSeekInt(panel, nn, "Swing speed", "m_swingSpeed", 6, 1, 20, mm);
                break;
            case "viewmodel":
                addConfigSeekFloat(panel, nn, "Pos X", "m_posX", 0, -50, 50, mm);
                addConfigSeekFloat(panel, nn, "Pos Y", "m_posY", 0, -50, 50, mm);
                addConfigSeekFloat(panel, nn, "Pos Z", "m_posZ", 0, -50, 50, mm);
                addConfigSeekFloat(panel, nn, "Scale X", "m_scaleX", 10, 1, 30, mm); // /10
                addConfigSeekFloat(panel, nn, "Scale Y", "m_scaleY", 10, 1, 30, mm);
                addConfigSeekFloat(panel, nn, "Scale Z", "m_scaleZ", 10, 1, 30, mm);
                addConfigSeekFloat(panel, nn, "Item FOV", "m_itemFov", 70, 30, 120, mm);
                addConfigToggle(panel, nn, "Apply in 3rd person", "m_applyThirdPerson", false, mm);
                break;
            case "fogcolor":
                addLabel(panel, "Color", "Uses hex in config (default white fog tint).");
                break;
            case "glintcolor":
                addLabel(panel, "Color", "Enchant glint color stored in config as hex.");
                break;
            case "nick":
                addLabel(panel, "Name", "Set local nick via config key m_fakeName (edit in file if needed).");
                addConfigToggle(panel, nn, "Bold", "m_bold", false, mm);
                addConfigToggle(panel, nn, "Italic", "m_italic", false, mm);
                break;
            case "ambience":
                addConfigSeekInt(panel, "Time Changer", "Custom time", "m_customTime", 6000, 0, 24000, mm);
                addLabel(panel, "Weather", "Weather Changer follows this module toggle.");
                break;
            case "airswing":
                addLabel(panel, "Settings", "Loaded with SystemMods — enable pack keeps Air Swing injected.");
                break;
            case "fullbright":
            case "nofog":
            case "connectedglass":
            case "thirdpersonnametag":
            case "armorhud":
                addLabel(panel, "Settings", "On/off only — no extra options for this module.");
                break;
            default:
                addLabel(panel, "Settings", "On/off only.");
                break;
        }
    }

    private void addConfigToggle(LinearLayout panel, String moduleName, String label,
                                 String key, boolean def,
                                 org.levimc.launcher.core.mods.ModManager mm) {
        final boolean[] on = { prefs.getBoolean(moduleName + "_" + key, def) };
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final int trackW = dp(40), trackH = dp(22), thumbSize = dp(18);
        final float thumbOffX = dp(2), thumbOnX = trackW - thumbSize - dp(2);
        final FrameLayout toggle = new FrameLayout(activity);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(trackW, trackH));
        toggle.setBackgroundResource(on[0] ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);
        final View thumb = new View(activity);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.CENTER_VERTICAL;
        thumb.setLayoutParams(thumbLp);
        thumb.setBackgroundResource(on[0] ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
        thumb.setTranslationX(on[0] ? thumbOnX : thumbOffX);
        toggle.addView(thumb);
        toggle.setOnClickListener(v -> {
            boolean next = !on[0];
            on[0] = next;
            prefs.edit().putBoolean(moduleName + "_" + key, next).apply();
            org.levimc.launcher.core.mods.SystemModsInstaller.setModuleSetting(
                    activity, mm, moduleName, key, next);
            thumb.animate().translationX(next ? thumbOnX : thumbOffX).setDuration(200).start();
            toggle.setBackgroundResource(next ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);
            thumb.setBackgroundResource(next ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
        });
        row.addView(t);
        row.addView(toggle);
        panel.addView(row);
    }

    /** Seek for float stored as int UI (value = progress / 10 for scale keys). */
    private void addConfigSeek(LinearLayout panel, String moduleName, String label, String key,
                               int def, int min, int max,
                               org.levimc.launcher.core.mods.ModManager mm) {
        addConfigSeekInt(panel, moduleName, label, key, def, min, max, mm);
    }

    private void addConfigSeekInt(LinearLayout panel, String moduleName, String label, String key,
                                  int def, int min, int max,
                                  org.levimc.launcher.core.mods.ModManager mm) {
        int cur = prefs.getInt(moduleName + "_" + key, def);
        if (cur < min) cur = min;
        if (cur > max) cur = max;
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);
        TextView value = new TextView(activity);
        value.setText(label + ": " + cur);
        value.setTextColor(MUTED);
        value.setTextSize(11);
        wrap.addView(value);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(max - min);
        seek.setProgress(cur - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress + min;
                value.setText(label + ": " + v);
                if (fromUser) {
                    prefs.edit().putInt(moduleName + "_" + key, v).apply();
                    // lookLineLength is float in native
                    if ("lookLineLength".equals(key) || key.startsWith("m_scale") || key.startsWith("m_pos")
                            || key.startsWith("m_rot") || key.startsWith("m_pivot")) {
                        float fv = "lookLineLength".equals(key) || key.startsWith("m_scale")
                                ? v / 10f : (float) v;
                        if (key.startsWith("m_scale")) fv = v / 10f;
                        else if ("lookLineLength".equals(key)) fv = v / 10f;
                        else fv = (float) v;
                        org.levimc.launcher.core.mods.SystemModsInstaller.setModuleSetting(
                                activity, mm, moduleName, key, fv);
                    } else {
                        org.levimc.launcher.core.mods.SystemModsInstaller.setModuleSetting(
                                activity, mm, moduleName, key, v);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(seek);
        panel.addView(wrap);
    }

    private void addConfigSeekFloat(LinearLayout panel, String moduleName, String label, String key,
                                    int def, int min, int max,
                                    org.levimc.launcher.core.mods.ModManager mm) {
        addConfigSeekInt(panel, moduleName, label, key, def, min, max, mm);
    }

    private void addLabel(LinearLayout panel, String title, String body) {
        TextView h = new TextView(activity);
        h.setText(title);
        h.setTextColor(TEXT);
        h.setTextSize(13);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(8);
        h.setLayoutParams(hlp);
        panel.addView(h);
        if (body != null && !body.isEmpty()) {
            TextView b = new TextView(activity);
            b.setText(body);
            b.setTextColor(MUTED);
            b.setTextSize(11);
            panel.addView(b);
        }
    }

    private void addSeek(LinearLayout panel, String key, int def, ValueFmt fmt) {
        int cur = prefs.getInt(key, def);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);
        TextView value = new TextView(activity);
        value.setText(fmt.format(cur));
        value.setTextColor(MUTED);
        value.setTextSize(11);
        value.setGravity(Gravity.END);
        wrap.addView(value);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(100);
        seek.setProgress(cur);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        slp.topMargin = dp(4);
        seek.setLayoutParams(slp);
        try {
            seek.setProgressDrawable(activity.getResources().getDrawable(R.drawable.bg_sys_slider_layer, null).mutate());
        } catch (Throwable ignored) {}
        try {
            seek.setThumb(activity.getResources().getDrawable(R.drawable.bg_sys_slider_thumb, null).mutate());
            seek.setThumbOffset(0);
        } catch (Throwable ignored) {}
        seek.setSplitTrack(false);
        seek.setPadding(dp(4), dp(4), dp(4), dp(4));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(fmt.format(progress));
                if (fromUser) prefs.edit().putInt(key, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                seekBar.animate().scaleY(1.15f).setDuration(120).start();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.animate().scaleY(1f).setDuration(180)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        });
        wrap.addView(seek);
        panel.addView(wrap);
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
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final boolean[] on = { prefs.getBoolean(key, def) };
        final int trackW = dp(36);
        final int trackH = dp(20);
        final int thumbSize = dp(16);
        final int pad = dp(2);
        final float thumbOffX = pad;
        final float thumbOnX = trackW - thumbSize - pad;
        FrameLayout toggle = new FrameLayout(activity);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(trackW, trackH));
        toggle.setBackgroundResource(on[0] ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);
        final View thumb = new View(activity);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.CENTER_VERTICAL;
        thumb.setLayoutParams(thumbLp);
        thumb.setBackgroundResource(on[0] ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
        thumb.setTranslationX(on[0] ? thumbOnX : thumbOffX);
        toggle.addView(thumb);
        final boolean[] animating = { false };
        toggle.setOnClickListener(v -> {
            if (animating[0]) return;
            animating[0] = true;
            final boolean next = !on[0];
            on[0] = next;
            prefs.edit().putBoolean(key, next).apply();
            ObjectAnimator slide = ObjectAnimator.ofFloat(thumb, "translationX",
                    thumb.getTranslationX(), next ? thumbOnX : thumbOffX);
            slide.setDuration(280);
            slide.setInterpolator(new DecelerateInterpolator(1.5f));
            slide.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    animating[0] = false;
                    thumb.setBackgroundResource(next
                            ? R.drawable.bg_toggle_thumb : R.drawable.bg_toggle_thumb_off);
                    toggle.setBackgroundResource(next
                            ? R.drawable.bg_toggle_track_on : R.drawable.bg_toggle_track_off);
                }
            });
            slide.start();
        });
        row.addView(t);
        row.addView(toggle);
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

    private void seedModules() {
        modulesByCategory.clear();
        // SystemMods.levipack modules (shown in SystemDLC)
        modulesByCategory.put("HUD", list(
                inbuilt("armorhud", "Armor HUD", "Show armor durability on screen")
        ));
        modulesByCategory.put("Visual", list(
                inbuilt("connectedglass", "Connected Glass", "Seamless glass rendering"),
                inbuilt("fogcolor", "Fog Color", "Tint world fog"),
                inbuilt("fullbright", "Fullbright", "Full brightness"),
                inbuilt("glintcolor", "Glint Color", "Custom enchantment glint"),
                inbuilt("hitbox", "Hitbox", "Entity hitbox outlines"),
                inbuilt("nofog", "No Fog", "Remove distance fog"),
                inbuilt("swingmodifier", "Swing Modifier", "Customize arm swing"),
                inbuilt("thirdpersonnametag", "Third Person Nametag", "Show your nametag in 3rd person"),
                inbuilt("viewmodel", "View Model", "Adjust held-item view model"),
                inbuilt("ambience", "Ambience", "Time of day + weather")
        ));
        modulesByCategory.put("Player", list(
                inbuilt("nick", "Nick", "Local display name"),
                inbuilt("airswing", "Air Swing", "Swing animation while in air")
        ));
        // No Disconnect is always-on inside SystemMods — not listed
    }

    private void refreshEnabledFromManagers() {
        try {
            org.levimc.launcher.core.mods.ModManager mm =
                    org.levimc.launcher.core.mods.ModManager.getInstance();
            for (List<ModuleDef> group : modulesByCategory.values()) {
                for (ModuleDef m : group) {
                    String nn = systemModsModuleName(m);
                    if (nn != null) {
                        m.defaultOn = org.levimc.launcher.core.mods.SystemModsInstaller
                                .isModuleEnabled(activity, mm, nn)
                                || prefs.getBoolean(m.id + "_on", false);
                    } else {
                        m.defaultOn = prefs.getBoolean(m.id + "_on", m.defaultOn);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void applyModuleEnabled(ModuleDef mod, boolean enabled) {
        prefs.edit().putBoolean(mod.id + "_on", enabled).apply();
        try {
            String nativeName = systemModsModuleName(mod);
            org.levimc.launcher.core.mods.ModManager mm =
                    org.levimc.launcher.core.mods.ModManager.getInstance();
            // Keep SystemMods pack itself enabled
            try { mm.setModEnabled("SystemMods", true); } catch (Throwable ignored) {}
            if (nativeName != null) {
                org.levimc.launcher.core.mods.SystemModsInstaller.setModuleEnabled(
                        activity, mm, nativeName, enabled);
            }
            if ("ambience".equals(mod.id)) {
                org.levimc.launcher.core.mods.SystemModsInstaller.setModuleEnabled(
                        activity, mm, "Time Changer", enabled);
                org.levimc.launcher.core.mods.SystemModsInstaller.setModuleEnabled(
                        activity, mm, "Weather Changer", enabled);
            }
            // Always force No Disconnect on
            org.levimc.launcher.core.mods.SystemModsInstaller.setModuleEnabled(
                    activity, mm, "No Disconnect", true);
        } catch (Throwable ignored) {}
        if ("inbuilt".equals(mod.kind)) {
            try {
                InbuiltModManager im = InbuiltModManager.getInstance(activity);
                im.setInbuiltModEnabled(mod.id, enabled);
            } catch (Throwable ignored) {}
        }
    }

    /** Map SystemDLC card id → native Module::name used in config.json */
    private static String systemModsModuleName(ModuleDef mod) {
        if (mod == null || mod.id == null) return null;
        // Exact names from stock libBedrockTools.so Module("...") constructors
        switch (mod.id) {
            case "armorhud": return "ArmorHUD";
            case "connectedglass": return "Connected Glass";
            case "fogcolor": return "Fog Color";
            case "airswing":
                addLabel(panel, "Settings", "Loaded with SystemMods — enable pack keeps Air Swing injected.");
                break;
            case "fullbright": return "Fullbright";
            case "glintcolor": return "Glint Color";
            case "hitbox": return "Hitbox";
            case "nofog": return "NoFog";
            case "swingmodifier": return "Swing Modifier";
            case "thirdpersonnametag": return "Third Person Nametag";
            case "viewmodel": return "View Model";
            case "nick": return "Nick";
            case "ambience": return "Time Changer"; // Weather Changer toggled separately
            default: return null;
        }
    }


    private String currentQuery() {
        try {
            android.widget.EditText search = root != null ? root.findViewById(R.id.sys_search) : null;
            return search != null && search.getText() != null ? search.getText().toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private static List<ModuleDef> list(ModuleDef... items) {
        List<ModuleDef> out = new ArrayList<>();
        for (ModuleDef m : items) out.add(m);
        return out;
    }

    private static ModuleDef inbuilt(String id, String title, String desc) {
        ModuleDef def = new ModuleDef();
        def.id = id;
        def.title = title;
        def.keybind = "None";
        def.defaultOn = false;
        def.desc = desc;
        def.kind = "inbuilt";
        return def;
    }

    private static ModuleDef nativePack(String id, String title, String desc) {
        ModuleDef def = new ModuleDef();
        def.id = id;
        def.title = title;
        def.keybind = "None";
        def.defaultOn = true;
        def.desc = desc;
        def.kind = "native";
        def.nativeId = id;
        return def;
    }

    private static final class ModuleDef {
        String id;
        String title;
        String keybind;
        String kind;
        String desc;
        String nativeId;
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
