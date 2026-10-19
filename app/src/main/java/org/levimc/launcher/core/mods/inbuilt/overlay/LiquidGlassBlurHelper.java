package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Liquid glass backdrop blur.
 * Primary: FLAG_BLUR_BEHIND + setBlurBehindRadius (API 31+) on the overlay window.
 * Fallback: one-shot downscaled decor capture + soft box blur underlay.
 */
final class LiquidGlassBlurHelper {

    private static final String PREFS = "system_dlc_menu";
    private static final String KEY = "panel_style";
    private static final int BLUR_RADIUS = 32;

    private LiquidGlassBlurHelper() {}

    static boolean isLiquidGlass(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return "liquid_glass".equals(p.getString(KEY, "simple"));
    }

    static void applyWindowBlurFlags(WindowManager.LayoutParams params, boolean enable) {
        if (params == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            if (enable) {
                params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                params.setBlurBehindRadius(BLUR_RADIUS);
            } else {
                params.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                params.setBlurBehindRadius(0);
            }
        } catch (Throwable ignored) {
        }
    }

    static void attachFallbackUnderlay(Activity activity, ViewGroup host) {
        if (activity == null || host == null || !isLiquidGlass(activity)) return;
        removeFallbackUnderlay(host);

        ImageView underlay = new ImageView(activity);
        underlay.setTag("liquid_glass_blur_underlay");
        underlay.setScaleType(ImageView.ScaleType.CENTER_CROP);
        underlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        underlay.setBackgroundColor(0x770A1A12);
        host.addView(underlay, 0);

        host.post(() -> {
            try {
                Window window = activity.getWindow();
                if (window == null) return;
                View decor = window.getDecorView();
                int w = decor.getWidth();
                int h = decor.getHeight();
                if (w < 8 || h < 8) return;

                int cw = Math.max(32, w / 6);
                int ch = Math.max(32, h / 6);
                Bitmap src = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(src);
                canvas.scale(cw / (float) w, ch / (float) h);
                decor.draw(canvas);

                Bitmap soft = boxBlur(src, 4);
                if (src != soft && !src.isRecycled()) src.recycle();

                // slight green tint filter
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                ColorMatrix cm = new ColorMatrix(new float[]{
                        0.75f, 0, 0, 0, 8,
                        0, 0.95f, 0, 0, 18,
                        0, 0, 0.72f, 0, 8,
                        0, 0, 0, 0.92f, 0
                });
                paint.setColorFilter(new ColorMatrixColorFilter(cm));
                Bitmap tinted = Bitmap.createBitmap(soft.getWidth(), soft.getHeight(), Bitmap.Config.ARGB_8888);
                new Canvas(tinted).drawBitmap(soft, 0, 0, paint);
                if (!soft.isRecycled()) soft.recycle();

                underlay.setImageBitmap(tinted);
                underlay.setBackgroundColor(0x330A1A12);
            } catch (Throwable ignored) {
            }
        });
    }

    static void removeFallbackUnderlay(ViewGroup host) {
        if (host == null) return;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View c = host.getChildAt(i);
            if (c != null && "liquid_glass_blur_underlay".equals(c.getTag())) {
                host.removeViewAt(i);
            }
        }
    }

    /** Separable box blur, small radius, fast. */
    private static Bitmap boxBlur(Bitmap src, int passes) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            // horizontal
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    int r = 0, g = 0, bl = 0, n = 0;
                    for (int k = -2; k <= 2; k++) {
                        int xx = x + k;
                        if (xx < 0 || xx >= w) continue;
                        int c = a[row + xx];
                        r += (c >> 16) & 0xff;
                        g += (c >> 8) & 0xff;
                        bl += c & 0xff;
                        n++;
                    }
                    b[row + x] = 0xff000000 | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
                }
            }
            // vertical
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int r = 0, g = 0, bl = 0, n = 0;
                    for (int k = -2; k <= 2; k++) {
                        int yy = y + k;
                        if (yy < 0 || yy >= h) continue;
                        int c = b[yy * w + x];
                        r += (c >> 16) & 0xff;
                        g += (c >> 8) & 0xff;
                        bl += c & 0xff;
                        n++;
                    }
                    a[y * w + x] = 0xff000000 | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
                }
            }
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }
}
