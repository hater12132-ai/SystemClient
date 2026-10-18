package org.levimc.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Slowly drifting, soft monochrome light fields behind the UI, a lightweight native take on the
 * animated gradient backdrops of ruucm/shadergradient. Three large radial blobs move along
 * independent slow paths; the gradients are built once and only translated, so a frame is just a
 * few cheap draws. Redraws are throttled to ~30 fps and paused while the window is not visible
 * or when the user has disabled animations.
 */
public class AmbientGradientView extends View {

    private static final long FRAME_DELAY_MS = 33L;

    private final Paint[] paints = new Paint[3];
    private final float[] radius = new float[3];
    // per blob: anchor (fraction of w,h), drift amplitude (fraction), period (s), phase
    private static final float[][] BLOBS = {
            {0.18f, 0.22f, 0.16f, 0.12f, 46f, 0.0f},
            {0.82f, 0.80f, 0.14f, 0.16f, 61f, 2.1f},
            {0.62f, 0.14f, 0.18f, 0.10f, 53f, 4.2f},
    };
    private static final int[] COLORS = {0x3DFFFFFF, 0x2AB4B8C4, 0x1FFFFFFF};

    private boolean running;
    private long startTime;

    public AmbientGradientView(Context context) {
        super(context);
        init();
    }

    public AmbientGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AmbientGradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        for (int i = 0; i < paints.length; i++) paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        startTime = SystemClock.uptimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float base = Math.max(w, h);
        float[] scale = {0.62f, 0.58f, 0.40f};
        for (int i = 0; i < paints.length; i++) {
            radius[i] = Math.max(1f, base * scale[i]);
            paints[i].setShader(new RadialGradient(0f, 0f, radius[i],
                    new int[]{COLORS[i], (COLORS[i] & 0x00FFFFFF) | ((COLORS[i] >>> 24) / 3 << 24),
                            COLORS[i] & 0x00FFFFFF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        float t = animate ? (SystemClock.uptimeMillis() - startTime) / 1000f : 0f;

        for (int i = 0; i < paints.length; i++) {
            float[] b = BLOBS[i];
            double ang = (2 * Math.PI * t / b[4]) + b[5];
            float cx = w * (b[0] + b[2] * (float) Math.sin(ang));
            float cy = h * (b[1] + b[3] * (float) Math.cos(ang * 0.83));
            canvas.save();
            canvas.translate(cx, cy);
            canvas.drawCircle(0f, 0f, radius[i], paints[i]);
            canvas.restore();
        }
        if (running && animate) postInvalidateDelayed(FRAME_DELAY_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = getWindowVisibility() == VISIBLE;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        if (running) invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == VISIBLE && running) invalidate();
    }
}
