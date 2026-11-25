package com.onefilm.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;

/**
 * LITE 版 Python 加载器（替代 `:app` 的 PyLoader）。
 *
 * <p>Chaquopy 运行库要求 minSdk 24，Android 5.x 无法加载，因此 LITE 不打包它。
 * 这里保持与 `:app` 完全相同的公开 API：Python 源统一返回空的 {@link SpiderNull}，
 * 表现为「该源无结果」，不会崩溃。
 *
 * <p>由 `tools/lite-compat/make-shims.ps1` 之外的独立文件维护（见 AGENTS.md 7.3）。
 */
public class PyLoader {

    public PyLoader() {
    }

    public void clear() {
    }

    public void setRecent(String recent) {
    }

    public Spider getSpider(String key, String api, String ext) {
        return new SpiderNull();
    }

    public Object[] proxy(Map<String, String> params) {
        return null;
    }
}
