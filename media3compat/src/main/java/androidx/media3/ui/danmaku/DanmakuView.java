package androidx.media3.ui.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;

import java.util.ArrayList;
import java.util.List;

/** Canvas danmaku overlay: scrolling, top, bottom lanes with stroke/shadow/projection styles. */
public final class DanmakuView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DanmakuItem> items = new ArrayList<>();
    private final List<DanmakuItem> local = new ArrayList<>();

    private DanmakuConfig config = DanmakuConfig.DEFAULT;
    private Player player;
    private boolean enabled = true;
    private long lastLayoutMs = -1;

    public DanmakuView(Context context) {
        this(context, null);
    }

    public DanmakuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        setWillNotDraw(false);
    }

    public void setPlayer(@Nullable Player player) {
        this.player = player;
    }

    public void setConfig(DanmakuConfig config) {
        this.config = config == null ? DanmakuConfig.DEFAULT : config;
        paint.setTypeface(this.config.typeface == null ? Typeface.DEFAULT_BOLD : this.config.typeface);
        requestLayout();
        invalidate();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        invalidate();
    }

    public void setItems(List<DanmakuItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        local.clear();
        lastLayoutMs = -1;
        invalidate();
    }

    public void addLocal(String text) {
        if (text == null || text.isEmpty()) return;
        long now = player == null ? 0 : player.getCurrentPosition();
        local.add(new DanmakuItem(now, DanmakuItem.MODE_SCROLL, 0xFFFFFFFF, 25f, text));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!enabled) return;
        if (getWidth() == 0 || getHeight() == 0) return;
        long positionMs = player == null ? 0 : player.getCurrentPosition();
        if (positionMs != lastLayoutMs) layoutLanes(positionMs);
        float scale = Math.max(0.5f, config.textScale);
        int alpha = (int) (255 * (1f - clamp(config.transparency)));
        paint.setTextSize(28f * scale);
        paint.setStyle(Paint.Style.FILL);
        drawItems(canvas, items, positionMs, scale, alpha);
        drawItems(canvas, local, positionMs, scale, alpha);
        if (player != null && player.isPlaying()) postInvalidateOnAnimation();
    }

    private void drawItems(Canvas canvas, List<DanmakuItem> list, long positionMs, float scale, int alpha) {
        float laneHeight = paint.getFontMetrics(null) * -1f + config.lineSpacing;
        for (DanmakuItem item : list) {
            long elapsed = positionMs - item.timeMs;
            if (elapsed < 0) continue;
            long duration = item.mode == DanmakuItem.MODE_SCROLL ? config.durationMs : config.fixedDurationMs;
            if (elapsed > duration) continue;
            item.measure(paint);
            float y = laneY(item, laneHeight);
            if (y < 0) continue;
            float x;
            if (item.mode == DanmakuItem.MODE_SCROLL) {
                float travel = getWidth() + item.width;
                x = getWidth() - travel * elapsed / (float) duration;
            } else {
                x = (getWidth() - item.width) / 2f;
            }
            int itemAlpha = config.transparency >= 1f ? 0 : alpha;
            if (config.styleMode == DanmakuConfig.STYLE_SHADOW) {
                paint.setShadowLayer(4f * scale, 2f, 2f, 0xAA000000);
            } else {
                paint.clearShadowLayer();
            }
            item.draw(canvas, paint, x, y, itemAlpha, config.styleMode, clamp(config.projectionTransparency));
            paint.clearShadowLayer();
        }
    }

    private float laneY(DanmakuItem item, float laneHeight) {
        float top = laneHeight * item.lane + laneHeight;
        if (item.mode == DanmakuItem.MODE_BOTTOM) return getHeight() - top;
        return top;
    }

    private void layoutLanes(long positionMs) {
        if (items.isEmpty() && local.isEmpty()) return;
        lastLayoutMs = positionMs;
        float scale = Math.max(0.5f, config.textScale);
        paint.setTextSize(28f * scale);
        int scrollLanes = Math.max(1, config.maxScrollLines);
        int topLanes = Math.max(1, config.maxTopLines);
        int bottomLanes = Math.max(1, config.maxBottomLines);
        long[] scrollFree = new long[scrollLanes];
        long[] topFree = new long[topLanes];
        long[] bottomFree = new long[bottomLanes];
        for (DanmakuItem item : items) {
            item.measure(paint);
            if (item.mode == DanmakuItem.MODE_SCROLL) {
                int lane = pick(scrollFree, item.timeMs);
                item.lane = lane;
                float travel = getWidth() + item.width;
                long visible = (long) (config.durationMs * Math.min(1f, travel / Math.max(1f, getWidth())));
                scrollFree[lane] = item.timeMs + Math.max(250, visible);
            } else if (item.mode == DanmakuItem.MODE_TOP) {
                int lane = pick(topFree, item.timeMs);
                item.lane = lane;
                topFree[lane] = item.timeMs + config.fixedDurationMs;
            } else {
                int lane = pick(bottomFree, item.timeMs);
                item.lane = lane;
                bottomFree[lane] = item.timeMs + config.fixedDurationMs;
            }
        }
        for (DanmakuItem item : local) {
            item.measure(paint);
            item.lane = 0;
        }
        if (config.showReverse) {
            // reverse mode mirrors scroll items right-to-left; handled by drawing order in future pass
        }
    }

    private static int pick(long[] lanes, long timeMs) {
        for (int i = 0; i < lanes.length; i++) if (lanes[i] <= timeMs) return i;
        int best = 0;
        for (int i = 1; i < lanes.length; i++) if (lanes[i] < lanes[best]) best = i;
        return best;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
