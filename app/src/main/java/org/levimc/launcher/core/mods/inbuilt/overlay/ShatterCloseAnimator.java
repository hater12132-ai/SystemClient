package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shatter-style close: snapshot the menu panel, split into shards,
 * scatter + rotate + fade — matches "glass break" close UIs.
 */
final class ShatterCloseAnimator {

    private ShatterCloseAnimator() {}

    /**
     * @param host full-screen overlay root (gets the temporary shard layer)
     * @param panel the visual panel to shatter (e.g. mod_menu_container)
     * @param onDone called on UI thread after animation (or immediately on failure)
     */
    static void play(Activity activity, ViewGroup host, View panel, Runnable onDone) {
        if (activity == null || host == null || panel == null) {
            if (onDone != null) onDone.run();
            return;
        }
        int pw = panel.getWidth();
        int ph = panel.getHeight();
        if (pw < 16 || ph < 16) {
            if (onDone != null) onDone.run();
            return;
        }

        Bitmap full;
        try {
            full = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(full);
            panel.draw(c);
        } catch (Throwable t) {
            if (onDone != null) onDone.run();
            return;
        }

        // Panel location inside host
        int[] panelLoc = new int[2];
        int[] hostLoc = new int[2];
        panel.getLocationOnScreen(panelLoc);
        host.getLocationOnScreen(hostLoc);
        final int originX = panelLoc[0] - hostLoc[0];
        final int originY = panelLoc[1] - hostLoc[1];

        // Hide real panel while shards play
        panel.setVisibility(View.INVISIBLE);

        final FrameLayout layer = new FrameLayout(activity);
        layer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layer.setClickable(true);
        host.addView(layer);

        // Grid of shards (cols x rows). More = closer to the video, costlier.
        final int cols = 6;
        final int rows = 5;
        final int sw = pw / cols;
        final int sh = ph / rows;
        final Random rng = new Random(0x51A77ER);
        final List<Animator> anims = new ArrayList<>();
        final List<Bitmap> pieces = new ArrayList<>();

        float cx = originX + pw / 2f;
        float cy = originY + ph / 2f;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int left = col * sw;
                int top = row * sh;
                int w = (col == cols - 1) ? (pw - left) : sw;
                int h = (row == rows - 1) ? (ph - top) : sh;
                if (w <= 0 || h <= 0) continue;

                Bitmap piece;
                try {
                    piece = Bitmap.createBitmap(full, left, top, w, h);
                } catch (Throwable t) {
                    continue;
                }
                pieces.add(piece);

                ImageView iv = new ImageView(activity);
                iv.setImageBitmap(piece);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = originX + left;
                lp.topMargin = originY + top;
                iv.setLayoutParams(lp);
                // Slight random tilt seed
                iv.setRotation(rng.nextFloat() * 6f - 3f);
                layer.addView(iv);

                // Scatter away from panel center
                float shardCx = originX + left + w / 2f;
                float shardCy = originY + top + h / 2f;
                float dx = shardCx - cx;
                float dy = shardCy - cy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1f) len = 1f;
                // normalize + boost
                float nx = dx / len;
                float ny = dy / len;
                float dist = 180f + rng.nextFloat() * 320f;
                // bias a bit outward randomly
                float tx = nx * dist + (rng.nextFloat() - 0.5f) * 80f;
                float ty = ny * dist + (rng.nextFloat() - 0.5f) * 80f + 40f;
                float rot = (rng.nextFloat() - 0.5f) * 140f;

                ObjectAnimator ax = ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, 0f, tx);
                ObjectAnimator ay = ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, 0f, ty);
                ObjectAnimator ar = ObjectAnimator.ofFloat(iv, View.ROTATION, iv.getRotation(), iv.getRotation() + rot);
                ObjectAnimator aa = ObjectAnimator.ofFloat(iv, View.ALPHA, 1f, 0f);
                ObjectAnimator asx = ObjectAnimator.ofFloat(iv, View.SCALE_X, 1f, 0.75f);
                ObjectAnimator asy = ObjectAnimator.ofFloat(iv, View.SCALE_Y, 1f, 0.75f);

                long dur = 420 + rng.nextInt(220);
                long delay = rng.nextInt(60);
                AccelerateInterpolator interp = new AccelerateInterpolator(1.2f);
                for (ObjectAnimator a : new ObjectAnimator[]{ax, ay, ar, aa, asx, asy}) {
                    a.setDuration(dur);
                    a.setStartDelay(delay);
                    a.setInterpolator(interp);
                    anims.add(a);
                }
            }
        }
        full.recycle();

        if (anims.isEmpty()) {
            host.removeView(layer);
            for (Bitmap b : pieces) if (!b.isRecycled()) b.recycle();
            if (onDone != null) onDone.run();
            return;
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(anims);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                try {
                    host.removeView(layer);
                } catch (Throwable ignored) {}
                for (Bitmap b : pieces) {
                    try { if (!b.isRecycled()) b.recycle(); } catch (Throwable ignored) {}
                }
                if (onDone != null) onDone.run();
            }
        });
        set.start();
    }
}
