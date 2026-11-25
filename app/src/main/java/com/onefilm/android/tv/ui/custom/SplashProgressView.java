package com.onefilm.android.tv.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * OneFilm: a compact, looping multi-colour loading bar for the launch screen.
 * Kept as a plain View so the splash does not depend on Compose or AppCompat.
 */
public class SplashProgressView extends View {

    private static final int[] COLORS = {
            0xFFFF5252, 0xFFFFB300, 0xFFFFEB3B,
            0xFF4CAF50, 0xFF29B6F6, 0xFF7C4DFF
    };

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float phase;

    public SplashProgressView(Context context) {
        this(context, null);
    }

    public SplashProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SplashProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setColor(0xFFEDEDED);
        barPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimationIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void startAnimationIfNeeded() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1250L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        final float radius = h / 2f;
        rect.set(0f, 0f, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        final float segment = w * 0.42f;
        final float start = -segment + (w + segment) * phase;
        rect.set(start, 0f, start + segment, h);
        clipPath.reset();
        clipPath.addRoundRect(new RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        barPaint.setShader(new LinearGradient(
                start, 0f, start + segment, 0f,
                COLORS, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, barPaint);
        canvas.restore();
        barPaint.setShader(null);
    }
}
