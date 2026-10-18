package org.levimc.launcher.ui.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A "liquid glass" surface, inspired by dashersw/liquid-glass-js: a tinted translucent body,
 * a soft inner sheen, a specular highlight near the top edge and a diagonal rim light that is
 * brightest at opposite corners (top-left / bottom-right), like light catching a glass edge.
 *
 * Everything is plain Canvas drawing (no blur or shaders that need a newer API), so it works on
 * every supported Android version. It reacts to the pressed and disabled states.
 */
public class LiquidGlassDrawable extends Drawable {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final RectF spec = new RectF();

    private final float radius;
    private final float rimWidth;
    private final float density;

    private boolean pressed;
    private boolean enabled = true;
    private int alpha = 255;

    /**
     * @param bodyColor tint of the glass body, e.g. 0xD9000000 for smoked black glass
     * @param radiusPx  corner radius in pixels
     * @param density   display density (dp to px)
     */
    public LiquidGlassDrawable(int bodyColor, float radiusPx, float density) {
        this.radius = radiusPx;
        this.density = density;
        this.rimWidth = 1.5f * density;

        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(bodyColor);

        sheenPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);

        specPaint.setStyle(Paint.Style.FILL);
        specPaint.setColor(0xFFFFFFFF);

        pressPaint.setStyle(Paint.Style.FILL);
        pressPaint.setColor(0x2EFFFFFF);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect b) {
        super.onBoundsChange(b);
        float half = rimWidth / 2f;
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);

        // top sheen: bright at the very top fading out by ~55% of the height
        sheenPaint.setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.55f,
                new int[]{0x3DFFFFFF, 0x0FFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        // bottom glow: a faint lift where light would pool at the lower edge
        glowPaint.setShader(new LinearGradient(0, rect.bottom, 0, rect.bottom - rect.height() * 0.45f,
                new int[]{0x26FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        // rim light: strong at top-left and bottom-right corners, dim along the middle
        rimPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xE6FFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x33FFFFFF, 0xA6FFFFFF},
                new float[]{0f, 0.28f, 0.5f, 0.72f, 1f}, Shader.TileMode.CLAMP));

        // specular highlight: a short, thin, soft capsule hugging the top edge
        float inset = Math.max(radius * 0.9f, 10f * density);
        float h = 1.6f * density;
        spec.set(rect.left + inset, rect.top + rimWidth + 1.5f * density,
                rect.left + inset + rect.width() * 0.34f, rect.top + rimWidth + 1.5f * density + h);
        specPaint.setShader(new LinearGradient(spec.left, 0, spec.right, 0,
                new int[]{0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (rect.isEmpty()) return;
        float a = (enabled ? 1f : 0.45f) * (alpha / 255f);
        int save = canvas.saveLayerAlpha(rect.left - rimWidth, rect.top - rimWidth,
                rect.right + rimWidth, rect.bottom + rimWidth, Math.round(255 * a));

        canvas.drawRoundRect(rect, radius, radius, bodyPaint);

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect, sheenPaint);
        canvas.drawRect(rect, glowPaint);
        canvas.drawRoundRect(spec, spec.height() / 2f, spec.height() / 2f, specPaint);
        if (pressed) canvas.drawRoundRect(rect, radius, radius, pressPaint);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rimPaint);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean p = false;
        boolean en = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) p = true;
            if (s == android.R.attr.state_enabled) en = true;
        }
        if (p != pressed || en != enabled) {
            pressed = p;
            enabled = en;
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(0f);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bodyPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
