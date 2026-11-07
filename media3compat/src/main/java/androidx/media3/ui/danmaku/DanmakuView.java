package androidx.media3.ui.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Canvas danmaku overlay: scrolling, top, bottom lanes with stroke/shadow/projection styles. */
public final class DanmakuView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final List<DanmakuItem> items = new ArrayList<>();
    private final List<DanmakuItem> activeItems = new ArrayList<>();

    private DanmakuConfig config = DanmakuConfig.DEFAULT;
    private Player player;
    private boolean enabled = true;
    private Long testClock;
    private PlayerView host;

    // 内部平滑时钟：以播放器位置为锚点，按真实时间推进，避免依赖外部轮询。
    private long anchorPos;
    private long anchorTime;
    private long renderPos;
    private long lastPlayerPos;
    private boolean clockValid;

    // 时间轴游标：每帧只消费新到时间的条目，绘制只遍历活动列表。
    private int nextIndex;
    private boolean needsTimelineReset = true;
    private long[] scrollFree = new long[0];
    private long[] topFree = new long[0];
    private long[] bottomFree = new long[0];

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            invalidate();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            invalidate();
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
            needsTimelineReset = true;
            invalidate();
        }
    };

    public DanmakuView(Context context) {
        this(context, null);
    }

    public DanmakuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        setWillNotDraw(false);
    }

    /** Debug/test clock override; null follows the player. Also acts as a seek anchor. */
    public void setClock(@Nullable Long positionMs) {
        this.testClock = positionMs;
        long pos = positionMs == null ? 0L : positionMs;
        long previous = renderPos;
        this.anchorPos = pos;
        this.anchorTime = SystemClock.uptimeMillis();
        this.renderPos = pos;
        this.clockValid = true;
        // 测试时钟每帧调用 setClock，不能每次重建时间轴；真实 seek 才重置。
        if (host != null || player != null || Math.abs(pos - previous) > 1000L) {
            needsTimelineReset = true;
        }
        invalidate();
    }

    public void setHost(PlayerView host) {
        this.host = host;
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player != null) this.player.removeListener(playerListener);
        this.player = player;
        if (player != null) player.addListener(playerListener);
        needsTimelineReset = true;
        invalidate();
    }

    public void setConfig(DanmakuConfig config) {
        this.config = config == null ? DanmakuConfig.DEFAULT : config;
        paint.setTypeface(this.config.typeface == null ? Typeface.DEFAULT_BOLD : this.config.typeface);
        needsTimelineReset = true;
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
        for (int i = 0; i < activeItems.size(); i++) {
            activeItems.get(i).recycleCache();
        }
        activeItems.clear();
        nextIndex = 0;
        needsTimelineReset = true;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        needsTimelineReset = true;
    }

    public void addLocal(String text) {
        if (text == null || text.isEmpty()) return;
        long now = host != null && host.getPlayer() != null
                ? host.getPlayer().getCurrentPosition()
                : (player == null ? 0 : player.getCurrentPosition());
        DanmakuItem item = new DanmakuItem(now, DanmakuItem.MODE_SCROLL, 0xFFFFFFFF, 25f, text);
        paint.setTextSize(28f * Math.max(0.5f, config.textScale));
        item.measure(paint);
        item.lane = 0;
        activeItems.add(item);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!enabled) return;
        if (getWidth() == 0 || getHeight() == 0) return;
        Player active = host != null ? host.getPlayer() : player;
        long positionMs = resolvePositionMs(active) + config.timeOffsetMs;
        if (needsTimelineReset) {
            resetTimeline(positionMs);
            needsTimelineReset = false;
        }
        advanceTo(positionMs);

        float scale = Math.max(0.5f, config.textScale);
        int alpha = (int) (255 * (1f - clamp(config.transparency)));
        paint.setTextSize(28f * scale);
        paint.setStyle(Paint.Style.FILL);
        drawActive(canvas, positionMs, scale, alpha);
        if (active != null && active.isPlaying()) postInvalidateOnAnimation();
    }

    /** 平滑时钟：播放器位置只做锚点/校正，帧间按真实时间推进。 */
    private long resolvePositionMs(@Nullable Player active) {
        if (active == null) {
            return testClock != null ? testClock : 0L;
        }
        long now = SystemClock.uptimeMillis();
        long playerPos = active.getCurrentPosition();
        if (!active.isPlaying()) {
            renderPos = playerPos;
            clockValid = false;
            lastPlayerPos = playerPos;
            return renderPos;
        }
        if (!clockValid || Math.abs(playerPos - renderPos) > 300L) {
            if (clockValid) needsTimelineReset = true;
            anchorPos = playerPos;
            anchorTime = now;
            renderPos = playerPos;
            clockValid = true;
        } else {
            long extrapolated = anchorPos + (now - anchorTime);
            // 播放器采样有 16~100ms 粒度；偏差超过 40ms 才重新锚定，避免微抖。
            if (playerPos != lastPlayerPos && Math.abs(playerPos - extrapolated) > 40L) {
                anchorPos = playerPos;
                anchorTime = now;
                extrapolated = playerPos;
            }
            renderPos = extrapolated;
        }
        lastPlayerPos = playerPos;
        return renderPos;
    }

    /** 重置时间轴：seek / 换集 / 尺寸或配置变化后，从当前位置重建活动列表。 */
    private void resetTimeline(long positionMs) {
        for (int i = 0; i < activeItems.size(); i++) {
            activeItems.get(i).recycleCache();
        }
        activeItems.clear();
        nextIndex = 0;
        ensureLanes();
        int start = lowerBound(positionMs - maxDuration());
        nextIndex = start;
        while (nextIndex < items.size() && items.get(nextIndex).timeMs <= positionMs) {
            addActive(items.get(nextIndex), positionMs);
            nextIndex++;
        }
    }

    private void advanceTo(long positionMs) {
        while (nextIndex < items.size() && items.get(nextIndex).timeMs <= positionMs) {
            addActive(items.get(nextIndex), positionMs);
            nextIndex++;
        }
        for (int i = activeItems.size() - 1; i >= 0; i--) {
            DanmakuItem item = activeItems.get(i);
            if (positionMs - item.timeMs > durationOf(item)) {
                item.recycleCache();
                int last = activeItems.size() - 1;
                activeItems.set(i, activeItems.get(last));
                activeItems.remove(last);
            }
        }
    }

    private void addActive(DanmakuItem item, long positionMs) {
        if (item.mode == DanmakuItem.MODE_SCROLL && !config.showScroll) return;
        if (item.mode == DanmakuItem.MODE_TOP && !config.showTop) return;
        if (item.mode == DanmakuItem.MODE_BOTTOM && !config.showBottom) return;
        if (item.mode != DanmakuItem.MODE_SCROLL
                && item.mode != DanmakuItem.MODE_TOP
                && item.mode != DanmakuItem.MODE_BOTTOM) return;
        int maxOnScreen = config.maxOnScreen <= 0 ? Integer.MAX_VALUE : config.maxOnScreen;
        if (activeItems.size() >= maxOnScreen) return;

        ensureLanes();
        paint.setTextSize(28f * Math.max(0.5f, config.textScale));
        item.measure(paint);
        if (item.mode == DanmakuItem.MODE_SCROLL) {
            int lane = pick(scrollFree, item.timeMs);
            item.lane = lane;
            scrollFree[lane] = item.timeMs + Math.max(250, visibleDuration(item));
        } else if (item.mode == DanmakuItem.MODE_TOP) {
            int lane = pick(topFree, item.timeMs);
            item.lane = lane;
            topFree[lane] = item.timeMs + config.fixedDurationMs;
        } else {
            int lane = pick(bottomFree, item.timeMs);
            item.lane = lane;
            bottomFree[lane] = item.timeMs + config.fixedDurationMs;
        }
        activeItems.add(item);
    }

    private void drawActive(Canvas canvas, long positionMs, float scale, int alpha) {
        paint.getFontMetrics(fontMetrics);
        float laneHeight = (fontMetrics.descent - fontMetrics.ascent) + config.lineSpacing;
        float textSize = 28f * scale;
        float projectionAlpha = clamp(config.projectionTransparency);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < activeItems.size(); i++) {
            DanmakuItem item = activeItems.get(i);
            long elapsed = positionMs - item.timeMs;
            if (elapsed < 0) continue;
            long duration = durationOf(item);
            if (elapsed > duration) continue;
            float y = laneY(item, laneHeight);
            if (y < 0) continue;
            float x;
            if (item.mode == DanmakuItem.MODE_SCROLL) {
                float travel = getWidth() + item.width;
                x = getWidth() - travel * elapsed / (float) duration;
            } else {
                x = (getWidth() - item.width) / 2f;
            }
            if (item.cacheBitmap == null) {
                item.ensureCache(textSize, config.styleMode, scale, projectionAlpha, fontMetrics);
            }
            if (item.cacheBitmap == null) continue;
            int itemAlpha = config.transparency >= 1f ? 0 : alpha;
            paint.setAlpha(itemAlpha);
            canvas.drawBitmap(item.cacheBitmap, x - item.cachePad, y - item.cacheBaseline, paint);
        }
        paint.setAlpha(255);
    }

    private float laneY(DanmakuItem item, float laneHeight) {
        float top = laneHeight * item.lane + laneHeight;
        if (item.mode == DanmakuItem.MODE_BOTTOM) return getHeight() - top;
        return top;
    }

    private long durationOf(DanmakuItem item) {
        return item.mode == DanmakuItem.MODE_SCROLL ? config.durationMs : config.fixedDurationMs;
    }

    private long maxDuration() {
        return Math.max(config.durationMs, config.fixedDurationMs);
    }

    private long visibleDuration(DanmakuItem item) {
        float travel = getWidth() + item.width;
        return (long) (config.durationMs * Math.min(1f, travel / Math.max(1f, getWidth())));
    }

    private void ensureLanes() {
        int scrollLanes = Math.max(1, config.maxScrollLines);
        int topLanes = Math.max(1, config.maxTopLines);
        int bottomLanes = Math.max(1, config.maxBottomLines);
        if (scrollFree.length != scrollLanes) {
            scrollFree = new long[scrollLanes];
            resetLanes(scrollFree);
        }
        if (topFree.length != topLanes) {
            topFree = new long[topLanes];
            resetLanes(topFree);
        }
        if (bottomFree.length != bottomLanes) {
            bottomFree = new long[bottomLanes];
            resetLanes(bottomFree);
        }
    }

    private static void resetLanes(long[] lanes) {
        Arrays.fill(lanes, Long.MIN_VALUE);
    }

    private int lowerBound(long timeMs) {
        int lo = 0;
        int hi = items.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (items.get(mid).timeMs < timeMs) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
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
