package androidx.media3.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;

/**
 * OneFilm 兼容层：media3 扩展类型的占位实现。
 * Hosts a DefaultTimeBar and keeps it bound to the current player.
 */
public class PlayerSeekView extends FrameLayout {

    private static final long REFRESH_INTERVAL_MS = 500L;

    private final DefaultTimeBar timeBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scrubbing;

    @Nullable
    private Player player;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
            updateProgress();
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (player != null) handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        timeBar = new DefaultTimeBar(context, attrs);
        timeBar.setId(R.id.exo_progress);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                scrubbing = true;
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                scrubbing = true;
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbing = false;
            }
        });
        addView(timeBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player != null) {
            this.player.removeListener(playerListener);
        }
        this.player = player;
        handler.removeCallbacks(refreshTask);
        if (player == null) {
            timeBar.setDuration(0);
            timeBar.setPosition(0);
            timeBar.setBufferedPosition(0);
            return;
        }
        player.addListener(playerListener);
        updateProgress();
        handler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (player != null) {
            handler.removeCallbacks(refreshTask);
            handler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(refreshTask);
        super.onDetachedFromWindow();
    }

    private void updateProgress() {
        if (scrubbing) return;
        Player current = player;
        if (current == null) return;
        long duration = current.getDuration();
        long position = current.getCurrentPosition();
        long buffered = current.getBufferedPosition();
        timeBar.setDuration(duration == C.TIME_UNSET ? 0 : Math.max(0, duration));
        timeBar.setPosition(Math.max(0, position));
        timeBar.setBufferedPosition(Math.max(0, buffered));
    }
}
