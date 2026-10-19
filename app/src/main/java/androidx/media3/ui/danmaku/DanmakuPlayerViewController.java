package androidx.media3.ui.danmaku;

import android.net.Uri;

import androidx.media3.ui.PlayerView;

import okhttp3.OkHttpClient;

/**
 * OneFilm compatibility shim. Placeholder for FongMi's media3 fork type.
 * Phase 1 baseline: accepts settings and data source but renders nothing.
 */
public final class DanmakuPlayerViewController {

    public void bind(PlayerView playerView) {
    }

    public void setOkHttpClient(OkHttpClient client) {
    }

    public void setEnabled(boolean enabled) {
    }

    public void setConfig(DanmakuConfig config) {
    }

    public void setDataSource(Uri uri) {
    }

    public void sendNow(String text) {
    }

    public void close() {
    }
}
