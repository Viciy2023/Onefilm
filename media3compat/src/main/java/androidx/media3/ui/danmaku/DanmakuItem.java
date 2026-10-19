package androidx.media3.ui.danmaku;

import android.graphics.Canvas;
import android.graphics.Paint;

/** A single danmaku comment with measured layout. */
public final class DanmakuItem {

    public static final int MODE_SCROLL = 1;
    public static final int MODE_BOTTOM = 4;
    public static final int MODE_TOP = 5;

    public final long timeMs;
    public final int mode;
    public final int color;
    public final float sizeSp;
    public final String text;

    float width;
    float height;
    int lane;
    long durationMs = 8000L;

    public DanmakuItem(long timeMs, int mode, int color, float sizeSp, String text) {
        this.timeMs = timeMs;
        this.mode = mode;
        this.color = color;
        this.sizeSp = sizeSp;
        this.text = text;
    }

    void measure(Paint paint) {
        width = paint.measureText(text);
        height = paint.getFontMetrics(null) * -1f;
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
