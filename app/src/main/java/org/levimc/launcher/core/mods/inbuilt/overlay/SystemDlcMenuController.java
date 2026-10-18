package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.R;

/**
 * Binds the SystemDLC-style mod menu shell (categories + Function/Preview/Elements).
 * Placeholder behaviour — categories are tappable; settings are interactive UI only for now.
 */
final class SystemDlcMenuController {
    private static final int ACCENT = 0xFF3358D4;
    private static final int[] ACCENT_COLORS = {
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
            0xFF00ACC1, 0xFF3358D4, 0xFF8E24AA
    };

    private final View root;
    private final Activity activity;
    private final TextView[] categoryViews;
    private final String[] categoryNames;
    private TextView topCategory;

    SystemDlcMenuController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        categoryNames = new String[]{
                "All", "Combat", "Movement", "Player", "Render", "Visual", "Client"
        };
        int[] ids = {
                R.id.sys_cat_all, R.id.sys_cat_combat, R.id.sys_cat_movement,
                R.id.sys_cat_player, R.id.sys_cat_render, R.id.sys_cat_visual, R.id.sys_cat_client
        };
        categoryViews = new TextView[ids.length];
        for (int i = 0; i < ids.length; i++) {
            categoryViews[i] = root.findViewById(ids[i]);
        }
        topCategory = root.findViewById(R.id.sys_top_category);
    }

    void attach() {
        for (int i = 0; i < categoryViews.length; i++) {
            final int index = i;
            TextView tv = categoryViews[i];
            if (tv == null) continue;
            tv.setOnClickListener(v -> selectCategory(index));
        }

        wireSettingsNav(R.id.sys_nav_configs, "Configs");
        wireSettingsNav(R.id.sys_nav_friends, "Friends");
        wireSettingsNav(R.id.sys_nav_waypoints, "Waypoints");
        wireSettingsNav(R.id.sys_nav_settings, "Settings");
        wireSettingsNav(R.id.sys_nav_search, "Search");

        SeekBar range = root.findViewById(R.id.sys_range_seek);
        TextView rangeLabel = root.findViewById(R.id.sys_range_label);
        if (range != null && rangeLabel != null) {
            range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    rangeLabel.setText(String.format(java.util.Locale.US, "%.1f blocks", progress / 10f));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        fillAccentRow(R.id.sys_accent_row);
        fillAccentRow(R.id.sys_elements_accent_row);

        View normal = root.findViewById(R.id.sys_mode_normal);
        View transparent = root.findViewById(R.id.sys_mode_transparent);
        if (normal != null && transparent != null) {
            normal.setOnClickListener(v -> {
                normal.setBackgroundResource(R.drawable.bg_sys_chip);
                transparent.setBackgroundResource(R.drawable.bg_sys_input);
            });
            transparent.setOnClickListener(v -> {
                transparent.setBackgroundResource(R.drawable.bg_sys_chip);
                normal.setBackgroundResource(R.drawable.bg_sys_input);
            });
        }

        View reset = root.findViewById(R.id.sys_reset_positions);
        if (reset != null) {
            reset.setOnClickListener(v ->
                    Toast.makeText(activity, "Positions reset", Toast.LENGTH_SHORT).show());
        }

        // Default: Combat selected
        selectCategory(1);
    }

    private void wireSettingsNav(int id, String name) {
        View v = root.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(view -> {
            if (topCategory != null) topCategory.setText(name);
            Toast.makeText(activity, name + " (UI ready — bind later)", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectCategory(int index) {
        for (int i = 0; i < categoryViews.length; i++) {
            TextView tv = categoryViews[i];
            if (tv == null) continue;
            boolean sel = i == index;
            tv.setSelected(sel);
            tv.setTextColor(sel ? 0xFFF2F4F8 : 0xFF9AA3B5);
        }
        if (topCategory != null && index >= 0 && index < categoryNames.length) {
            topCategory.setText(categoryNames[index]);
        }
    }

    private void fillAccentRow(int rowId) {
        LinearLayout row = root.findViewById(rowId);
        if (row == null) return;
        row.removeAllViews();
        int size = dp(22);
        for (int color : ACCENT_COLORS) {
            View dot = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            dot.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            if (color == ACCENT) {
                gd.setStroke(dp(2), Color.WHITE);
            }
            dot.setBackground(gd);
            final int c = color;
            dot.setOnClickListener(v -> {
                TextView hex = root.findViewById(R.id.sys_accent_hex);
                if (hex != null) hex.setText(String.format("#%06X", c & 0xFFFFFF));
            });
            row.addView(dot);
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
