package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class ShatterCloseAnimator {

    private static final int COLS = 6;
    private static final int ROWS = 5;

    private ShatterCloseAnimator() {}

    static void playClose(Activity activity, ViewGroup host, View panel, Runnable onDone) {
        play(activity, host, panel, false, onDone);
    }

    static void playOpen(Activity activity, ViewGroup host, View panel, Runnable onDone) {
        play(activity, host, panel, true, onDone);
    }

    private static void play(Activity activity, ViewGroup host, View panel,
                             boolean assemble, Runnable onDone) {
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

        int[] panelLoc = new int[2];
        int[] hostLoc = new int[2];
        panel.getLocationOnScreen(panelLoc);
        host.getLocationOnScreen(hostLoc);
        final int originX = panelLoc[0] - hostLoc[0];
        final int originY = panelLoc[1] - hostLoc[1];

        panel.setVisibility(View.INVISIBLE);

        final FrameLayout layer = new FrameLayout(activity);
        layer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layer.setClickable(true);
        host.addView(layer);

        final int sw = pw / COLS;
        final int sh = ph / ROWS;
        final Random rng = new Random(0x51A77E);
        final List<Animator> anims = new ArrayList<>();
        final List<Bitmap> pieces = new ArrayList<>();

        float cx = originX + pw / 2f;
        float cy = originY + ph / 2f;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int left = col * sw;
                int top = row * sh;
                int w = (col == COLS - 1) ? (pw - left) : sw;
                int h = (row == ROWS - 1) ? (ph - top) : sh;
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
                layer.addView(iv);

                float shardCx = originX + left + w / 2f;
                float shardCy = originY + top + h / 2f;
                float dx = shardCx - cx;
                float dy = shardCy - cy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1f) len = 1f;
                float nx = dx / len;
                float ny = dy / len;
                float dist = 180f + rng.nextFloat() * 320f;
                float scatterX = nx * dist + (rng.nextFloat() - 0.5f) * 80f;
                float scatterY = ny * dist + (rng.nextFloat() - 0.5f) * 80f + 40f;
                float rot = (rng.nextFloat() - 0.5f) * 140f;

                long dur = 420 + rng.nextInt(220);
                long delay = rng.nextInt(70);

                ObjectAnimator ax, ay, ar, aa, asx, asy;
                if (assemble) {
                    iv.setTranslationX(scatterX);
                    iv.setTranslationY(scatterY);
                    iv.setRotation(rot);
                    iv.setAlpha(0f);
                    iv.setScaleX(0.75f);
                    iv.setScaleY(0.75f);
                    ax = ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, scatterX, 0f);
                    ay = ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, scatterY, 0f);
                    ar = ObjectAnimator.ofFloat(iv, View.ROTATION, rot, 0f);
                    aa = ObjectAnimator.ofFloat(iv, View.ALPHA, 0f, 1f);
                    asx = ObjectAnimator.ofFloat(iv, View.SCALE_X, 0.75f, 1f);
                    asy = ObjectAnimator.ofFloat(iv, View.SCALE_Y, 0.75f, 1f);
                    DecelerateInterpolator interp = new DecelerateInterpolator(1.35f);
                    for (ObjectAnimator a : new ObjectAnimator[]{ax, ay, ar, aa, asx, asy}) {
                        a.setDuration(dur);
                        a.setStartDelay(delay);
                        a.setInterpolator(interp);
                        anims.add(a);
                    }
                } else {
                    ax = ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, 0f, scatterX);
                    ay = ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, 0f, scatterY);
                    ar = ObjectAnimator.ofFloat(iv, View.ROTATION, 0f, rot);
                    aa = ObjectAnimator.ofFloat(iv, View.ALPHA, 1f, 0f);
                    asx = ObjectAnimator.ofFloat(iv, View.SCALE_X, 1f, 0.75f);
                    asy = ObjectAnimator.ofFloat(iv, View.SCALE_Y, 1f, 0.75f);
                    AccelerateInterpolator interp = new AccelerateInterpolator(1.2f);
                    for (ObjectAnimator a : new ObjectAnimator[]{ax, ay, ar, aa, asx, asy}) {
                        a.setDuration(dur);
                        a.setStartDelay(delay);
                        a.setInterpolator(interp);
                        anims.add(a);
                    }
                }
            }
        }
        full.recycle();

        if (anims.isEmpty()) {
            try { host.removeView(layer); } catch (Throwable ignored) {}
            for (Bitmap b : pieces) try { if (!b.isRecycled()) b.recycle(); } catch (Throwable ignored) {}
            panel.setVisibility(View.VISIBLE);
            if (onDone != null) onDone.run();
            return;
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(anims);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                try { host.removeView(layer); } catch (Throwable ignored) {}
                for (Bitmap b : pieces) {
                    try { if (!b.isRecycled()) b.recycle(); } catch (Throwable ignored) {}
                }
                if (assemble) {
                    panel.setVisibility(View.VISIBLE);
                }
                if (onDone != null) onDone.run();
            }
        });
        set.start();
    }
}
