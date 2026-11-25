package androidx.media3.ui.danmaku;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

/** A single danmaku comment with measured layout and a cached text bitmap. */
public final class DanmakuItem {

    public static final int MODE_SCROLL = 1;
    public static final int MODE_BOTTOM = 4;
    public static final int MODE_TOP = 5;

    private static final Paint CACHE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFE8B45A, 0xFF7FD1C8, 0xFFFF8A80, 0xFF9FA8DA, 0xFFA5D6A7,
    };

    public final long timeMs;
    public final int mode;
    public final int color;
    public final float sizeSp;
    public final String text;

    float width;
    float height;
    int lane;
    long durationMs = 8000L;

    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private float measuredSize = -1f;
    private Typeface measuredTypeface;

    Bitmap cacheBitmap;
    float cachePad;
    float cacheBaseline;
    private float cacheTextSize = -1f;
    private int cacheStyle = -1;
    private int cacheColorMode = -1;

    public DanmakuItem(long timeMs, int mode, int color, float sizeSp, String text) {
        this.timeMs = timeMs;
        this.mode = mode;
        this.color = color;
        this.sizeSp = sizeSp;
        this.text = text;
    }

    void measure(Paint paint) {
        float size = paint.getTextSize();
        Typeface typeface = paint.getTypeface();
        if (width > 0f && measuredSize == size && measuredTypeface == typeface) return;
        measuredSize = size;
        measuredTypeface = typeface;
        width = paint.measureText(text);
        paint.getFontMetrics(fontMetrics);
        height = fontMetrics.descent - fontMetrics.ascent;
    }

    private int resolveColor(int colorMode) {
        switch (colorMode) {
            case DanmakuConfig.COLOR_MODE_OVERRIDE:
                return 0xFFFFFFFF;
            case DanmakuConfig.COLOR_MODE_RANDOM:
            case DanmakuConfig.COLOR_MODE_COLORFUL:
                return PALETTE[(text.hashCode() & 0x7fffffff) % PALETTE.length];
            default:
                return color;
        }
    }

    /** 预渲染文字（含描边/阴影/投影/渐变），每帧只做一次位图平移。 */
    void ensureCache(
            float textSize,
            int style,
            int colorMode,
            float scale,
            float projectionAlpha,
            float shadowTransparency,
            float strokeMultiplier,
            float projectionX,
            float projectionY,
            Typeface typeface,
            Paint.FontMetrics fm) {
        if (cacheBitmap != null
                && cacheTextSize == textSize
                && cacheStyle == style
                && cacheColorMode == colorMode) {
            return;
        }
        recycleCache();
        if (width <= 0f || height <= 0f) return;

        int resolvedColor = resolveColor(colorMode);
        float strokeWidth = Math.max(1f, height * 0.06f * Math.max(0.05f, strokeMultiplier));
        float projDx = 0f;
        float projDy = 0f;
        if (style == DanmakuConfig.STYLE_PROJECTION && projectionAlpha > 0f) {
            projDx = height * clampRatio(projectionX);
            projDy = height * clampRatio(projectionY);
        }
        float pad = 6f + Math.max(projDx, projDy);
        if (style == DanmakuConfig.STYLE_STROKE) pad += strokeWidth + 2f;
        if (style == DanmakuConfig.STYLE_SHADOW) pad += 8f;

        int w = (int) Math.ceil(width + pad * 2f);
        int h = (int) Math.ceil(height + pad * 2f);
        if (w <= 0 || h <= 0) return;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = CACHE_PAINT;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(typeface == null ? Typeface.DEFAULT_BOLD : typeface);
        paint.setTextSize(textSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(resolvedColor);
        float baseline = pad - fm.ascent;

        if (style == DanmakuConfig.STYLE_SHADOW) {
            int shadowAlpha = (int) (255f * clamp01(shadowTransparency));
            paint.setShadowLayer(4f * scale, 2f, 2f, shadowAlpha << 24);
        }
        if (style == DanmakuConfig.STYLE_PROJECTION && projectionAlpha > 0f) {
            paint.setAlpha((int) (255f * projectionAlpha));
            canvas.drawText(text, pad + projDx, baseline + projDy, paint);
            paint.setAlpha(255);
        }
        if (style == DanmakuConfig.STYLE_STROKE) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(Color.BLACK);
            canvas.drawText(text, pad, baseline, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(resolvedColor);
        }
        if (colorMode == DanmakuConfig.COLOR_MODE_GRADIENT) {
            paint.setShader(new LinearGradient(
                    pad, 0f, pad + width, 0f,
                    0xFFFF6B6B, 0xFF4D96FF, Shader.TileMode.CLAMP));
        } else {
            paint.setShader(null);
        }
        canvas.drawText(text, pad, baseline, paint);

        cacheBitmap = bitmap;
        cachePad = pad;
        cacheBaseline = baseline;
        cacheTextSize = textSize;
        cacheStyle = style;
        cacheColorMode = colorMode;
    }

    void recycleCache() {
        if (cacheBitmap != null) {
            cacheBitmap.recycle();
            cacheBitmap = null;
        }
        cacheTextSize = -1f;
        cacheStyle = -1;
        cacheColorMode = -1;
    }

    private static float clampRatio(float value) {
        return Math.max(0f, Math.min(0.5f, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
