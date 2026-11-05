package com.github.catvod.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;

import java.util.Locale;

/**
 * 界面语言管理（TV 与手机共用）。
 *
 * <p>支持：跟随系统（空串）、简体中文、繁體中文、English、Tiếng Việt、ไทย。
 * 采用「每个 Activity 包装 baseContext」的方式生效，不依赖 AppCompat 的
 * per-app language API，因此对 ComponentActivity / 原生 Activity 同样有效。
 */
public final class LocaleManager {

    public static final String SYSTEM = "";
    public static final String ZH_CN = "zh-CN";
    public static final String ZH_TW = "zh-TW";
    public static final String EN = "en";
    public static final String VI = "vi";
    public static final String TH = "th";

    /** 设置页展示顺序。 */
    public static final String[] TAGS = {SYSTEM, ZH_CN, ZH_TW, EN, VI, TH};

    private static final String KEY = "ui_language";

    private LocaleManager() {
    }

    public static String get() {
        String value = Prefers.getString(KEY);
        return value == null ? SYSTEM : value;
    }

    public static void set(String tag) {
        Prefers.put(KEY, tag == null ? SYSTEM : tag);
    }

    /** 每个 Activity 在 attachBaseContext 中调用。 */
    public static Context wrap(Context context) {
        String tag = get();
        if (TextUtils.isEmpty(tag)) return context;
        Locale locale = Locale.forLanguageTag(tag);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    /** 当前语言在设置页的展示名（用该语言自身书写，便于识别）。 */
    public static String label(String tag) {
        if (tag == null) tag = SYSTEM;
        switch (tag) {
            case ZH_CN:
                return "简体中文";
            case ZH_TW:
                return "繁體中文";
            case EN:
                return "English";
            case VI:
                return "Tiếng Việt";
            case TH:
                return "ไทย";
            default:
                return "跟随系统 / System";
        }
    }
}
