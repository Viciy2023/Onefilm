package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;

/**
 * OneFilm compatibility shim. Placeholder for FongMi's media3 fork type.
 * Hosts a DefaultTimeBar so existing layouts and scrub listeners keep working.
 */
public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;

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
        addView(timeBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setPlayer(@Nullable Player player) {
        // Phase 1: seek view is display-only; binding happens through PlaybackActivity.
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }
}
