package androidx.media3.ui.danmaku;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/** A single danmaku comment with measured layout and a cached text bitmap. */
public final class DanmakuItem {

    public static final int MODE_SCROLL = 1;
    public static final int MODE_BOTTOM = 4;
    public static final int MODE_TOP = 5;

    private static final Paint CACHE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    Bitmap cacheBitmap;
    float cachePad;
    float cacheBaseline;
    private float cacheTextSize = -1f;
    private int cacheStyle = -1;

    public DanmakuItem(long timeMs, int mode, int color, float sizeSp, String text) {
        this.timeMs = timeMs;
        this.mode = mode;
        this.color = color;
        this.sizeSp = sizeSp;
        this.text = text;
    }

    void measure(Paint paint) {
        float size = paint.getTextSize();
        if (width > 0f && measuredSize == size) return;
        measuredSize = size;
        width = paint.measureText(text);
        paint.getFontMetrics(fontMetrics);
        height = fontMetrics.descent - fontMetrics.ascent;
    }

    /** 预渲染文字（含描边/阴影/投影），每帧只做一次位图平移。 */
    void ensureCache(float textSize, int style, float scale, float projectionAlpha, Paint.FontMetrics fm) {
        if (cacheBitmap != null && cacheTextSize == textSize && cacheStyle == style) return;
        recycleCache();
        if (width <= 0f || height <= 0f) return;

        float strokeWidth = Math.max(1f, height * 0.06f);
        float projDx = 0f;
        float projDy = 0f;
        if (style == DanmakuConfig.STYLE_PROJECTION && projectionAlpha > 0f) {
            projDx = height * 0.35f;
            projDy = height * 0.35f;
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
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(textSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float baseline = pad - fm.ascent;

        if (style == DanmakuConfig.STYLE_SHADOW) {
            paint.setShadowLayer(4f * scale, 2f, 2f, 0xAA000000);
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
            paint.setColor(color);
        }
        canvas.drawText(text, pad, baseline, paint);

        cacheBitmap = bitmap;
        cachePad = pad;
        cacheBaseline = baseline;
        cacheTextSize = textSize;
        cacheStyle = style;
    }

    void recycleCache() {
        if (cacheBitmap != null) {
            cacheBitmap.recycle();
            cacheBitmap = null;
        }
        cacheTextSize = -1f;
        cacheStyle = -1;
    }


    void draw(Canvas canvas, Paint paint, float x, float y, int alpha, int style, float projectionAlpha) {
        paint.setColor(color);
        paint.setAlpha(alpha);
        if (style == DanmakuConfig.STYLE_PROJECTION && projectionAlpha > 0) {
            paint.setAlpha((int) (alpha * projectionAlpha));
            canvas.drawText(text, x + height * 0.35f, y + height * 0.35f, paint);
            paint.setAlpha(alpha);
        }
        if (style == DanmakuConfig.STYLE_STROKE) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, height * 0.06f));
            paint.setColor(0xFF000000);
            canvas.drawText(text, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setAlpha(alpha);
        }
        canvas.drawText(text, x, y, paint);
    }
}
