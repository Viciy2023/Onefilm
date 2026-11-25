package com.onefilm.android.tv.player.extractor;

import android.net.Uri;

import com.onefilm.android.tv.bean.Episode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * LITE 版迅雷解析器（替代 `:app` 的 Thunder）。
 *
 * <p>迅雷 SDK（`thunder-release.aar`）声明 minSdk 24，LITE 不打包。
 * 这里保持 `Source` 需要的 API：`match()` 恒为 false，
 * 因此磁力/迅雷/ed2k 链接会保持原样交给播放器，由播放器明确报「不支持的格式」，
 * 而不是静默消失。
 */
public class Thunder implements Source.Extractor {

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

    public static class Parser {

        public static boolean match(String url) {
            return false;
        }

        public static Callable<List<Episode>> get(String url) {
            return () -> new ArrayList<>();
        }
    }
}
