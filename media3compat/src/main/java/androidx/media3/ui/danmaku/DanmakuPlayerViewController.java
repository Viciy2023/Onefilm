package androidx.media3.ui.danmaku;

import android.net.Uri;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Real Canvas danmaku controller: fetches XML, parses and renders above PlayerView. */
public final class DanmakuPlayerViewController {

    private static final OkHttpClient DEFAULT_CLIENT = new OkHttpClient();

    private OkHttpClient client = DEFAULT_CLIENT;
    private DanmakuConfig config = DanmakuConfig.DEFAULT;
    private boolean enabled = true;
    private Uri source;

    @Nullable
    private PlayerView playerView;
    @Nullable
    private DanmakuView danmakuView;
    @Nullable
    private Call pending;

    public void bind(PlayerView playerView) {
        if (playerView == null) return;
        if (this.playerView == playerView && danmakuView != null) return;
        close();
        this.playerView = playerView;
        DanmakuView view = new DanmakuView(playerView.getContext());
        view.setConfig(config);
        view.setEnabled(enabled);
        view.setPlayer(playerView.getPlayer());
        PlayerView.LayoutParams params = new PlayerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playerView.addView(view, params);
        danmakuView = view;
        if (source != null) setDataSource(source);
    }

    public void setOkHttpClient(OkHttpClient client) {
        if (client != null) this.client = client;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (danmakuView != null) danmakuView.setEnabled(enabled);
    }

    public void setConfig(DanmakuConfig config) {
        this.config = config == null ? DanmakuConfig.DEFAULT : config;
        if (danmakuView != null) danmakuView.setConfig(this.config);
    }

    public void setDataSource(@Nullable Uri uri) {
        this.source = uri;
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        if (danmakuView == null) return;
        if (uri == null) {
            danmakuView.setItems(null);
            return;
        }
        Request request = new Request.Builder().url(uri.toString()).build();
        Call call = client.newCall(request);
        pending = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@Nullable Call call, @Nullable IOException e) {
                pending = null;
            }

            @Override
            public void onResponse(@Nullable Call call, @Nullable Response response) {
                pending = null;
                if (response == null) return;
                try (Response closeable = response) {
                    if (!closeable.isSuccessful() || closeable.body() == null) return;
                    String xml = closeable.body().string();
                    List<DanmakuItem> items = DanmakuParser.parseXml(xml);
                    DanmakuView view = danmakuView;
                    if (view != null) view.post(() -> view.setItems(items));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void sendNow(String text) {
        if (danmakuView != null) danmakuView.addLocal(text);
    }

    public void close() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        if (playerView != null && danmakuView != null) playerView.removeView(danmakuView);
        danmakuView = null;
        playerView = null;
    }
}
