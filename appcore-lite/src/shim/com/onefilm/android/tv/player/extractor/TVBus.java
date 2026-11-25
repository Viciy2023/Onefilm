package com.onefilm.android.tv.player.extractor;

import android.net.Uri;

/**
 * LITE 版投屏/直播内核解析器（替代 `:app` 的 TVBus）。
 *
 * <p>投屏内核（`tvbus-release.aar`）声明 minSdk 24，LITE 不打包。
 * `match()` 恒为 false，`tvbus://` 链接会交给后续解析器，最终由播放器报不支持。
 */
public class TVBus implements Source.Extractor {

    @Override
    public String fetch(String url) {
        return url;
    }

    @Override
    public boolean match(Uri uri) {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
