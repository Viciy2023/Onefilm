package com.onefilm.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.onefilm.android.tv.R;
import com.onefilm.android.tv.bean.Danmaku;
import com.onefilm.android.tv.impl.ApiCallback;
import com.onefilm.android.tv.setting.DanmakuSetting;
import com.onefilm.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final AtomicInteger REQUEST_ID = new AtomicInteger();
    /** 冷缓存搜索实测 37~55 秒，不能用默认 30 秒客户端。 */
    private static final long SEARCH_TIMEOUT_MS = 60_000L;

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
    }

    public static void cancel() {
        REQUEST_ID.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        search(name, episode, items -> items.stream().findFirst().ifPresent(found), error -> {});
    }

    public static void search(String name, String episode, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        int requestId = begin();
        newCall(name, episode).enqueue(new ApiCallback<>(requestId, REQUEST_ID, response -> Danmaku.arrayFrom(read(response)), success, error));
    }

    private static int begin() {
        cancel();
        return REQUEST_ID.get();
    }

    private static Call newCall(String name, String episode) {
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        String url = DanmakuSetting.getEffectiveApiUrl();
        return url.contains("{name}") || url.contains("{episode}") ? getCall(url, name, episode) : postCall(url, name, episode);
    }

    private static Call getCall(String url, String name, String episode) {
        return OkHttp.newCall(OkHttp.client(SEARCH_TIMEOUT_MS), url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
    }

    private static Call postCall(String url, String name, String episode) {
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("name", name);
        params.put("episode", episode);
        Request request = new Request.Builder()
                .url(url)
                .post(OkHttp.toBody(params))
                .tag(TAG)
                .build();
        return OkHttp.client(SEARCH_TIMEOUT_MS).newCall(request);
    }

    private static String read(Response response) throws IOException {
        String text = response.body().string();
        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
        if (TextUtils.isEmpty(text)) throw new IOException(ResUtil.getString(R.string.error_empty));
        return text;
    }
}
