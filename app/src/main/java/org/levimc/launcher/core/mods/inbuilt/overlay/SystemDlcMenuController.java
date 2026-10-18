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
    private static final int ACCENT = 0xFF3358D4;
    private static final int TEXT = 0xFFF2F4F8;
    private static final int MUTED = 0xFF9AA3B5;
    private static final int DIM = 0xFF5C6370;

    private final Activity activity;
    private final View root;
    private final SharedPreferences prefs;
    private final LinearLayout moduleHost;
    private final TextView topCategory;
    private final List<TextView> categoryViews = new ArrayList<>();
    private final String[] categoryNames = {
            "All", "Combat", "Movement", "Player", "Render", "Visual", "Client"
    };
    private final Map<String, List<ModuleDef>> modulesByCategory = new LinkedHashMap<>();
    private String expandedId = null;
    private String activeCategory = "Combat";

    SystemDlcMenuController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.moduleHost = root.findViewById(R.id.sys_module_host);
        this.topCategory = root.findViewById(R.id.sys_top_category);
        seedModules();
    }

    void attach() {
        int[] catIds = {
                R.id.sys_cat_all, R.id.sys_cat_combat, R.id.sys_cat_movement,
                R.id.sys_cat_player, R.id.sys_cat_render, R.id.sys_cat_visual, R.id.sys_cat_client
        };
        for (int i = 0; i < catIds.length; i++) {
            TextView tv = root.findViewById(catIds[i]);
            if (tv == null) continue;
            categoryViews.add(tv);
            final int idx = i;
            tv.setOnClickListener(v -> selectCategory(categoryNames[idx]));
        }

        wireNav(R.id.sys_nav_configs, "Configs");
        wireNav(R.id.sys_nav_friends, "Friends");
        wireNav(R.id.sys_nav_waypoints, "Waypoints");
        wireNav(R.id.sys_nav_settings, "Settings");
        wireNav(R.id.sys_nav_search, "Search");

        EditText search = root.findViewById(R.id.sys_search);
        if (search != null) {
            search.addTextChangedListener(new SimpleTextWatcher(s -> rebuildGrid(s)));
        }

        selectCategory("Combat");
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
            TextView tv = categoryViews.get(i);
            boolean sel = categoryNames[i].equals(name);
            tv.setSelected(sel);
            tv.setTextColor(sel ? TEXT : MUTED);
            tv.setTypeface(Typeface.create(Typeface.DEFAULT, sel ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (topCategory != null) topCategory.setText(name);
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
        if (!q.isEmpty()) {
            List<ModuleDef> filtered = new ArrayList<>();
            for (ModuleDef m : list) {
                if (m.title.toLowerCase(Locale.US).contains(q)) filtered.add(m);
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
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        boolean expanded = mod.id.equals(expandedId);
        card.setBackgroundResource(expanded
                ? R.drawable.bg_sys_module_card_expanded
                : R.drawable.bg_sys_module_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Header row
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText(mod.title);
        title.setTextColor(TEXT);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);

        TextView badge = new TextView(activity);
        badge.setText(mod.keybind);
        badge.setTextColor(MUTED);
        badge.setTextSize(10);
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        badge.setBackgroundResource(R.drawable.bg_sys_badge);

        TextView star = new TextView(activity);
        star.setText(prefs.getBoolean(mod.id + "_fav", false) ? "★" : "☆");
        star.setTextColor(prefs.getBoolean(mod.id + "_fav", false) ? ACCENT : MUTED);
        star.setTextSize(16);
        star.setPadding(dp(8), 0, dp(4), 0);
        star.setOnClickListener(v -> {
            boolean fav = !prefs.getBoolean(mod.id + "_fav", false);
            prefs.edit().putBoolean(mod.id + "_fav", fav).apply();
            star.setText(fav ? "★" : "☆");
            star.setTextColor(fav ? ACCENT : MUTED);
        });

        Switch toggle = new Switch(activity);
        toggle.setChecked(prefs.getBoolean(mod.id + "_on", mod.defaultOn));
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(mod.id + "_on", checked).apply();
            Toast.makeText(activity, mod.title + (checked ? " enabled" : " disabled"), Toast.LENGTH_SHORT).show();
        });

        header.addView(title);
        header.addView(badge);
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
        int[] colors = {0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF3358D4, 0xFF8E24AA};
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
