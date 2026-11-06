package com.onefilm.android.tv.player.adblock;

import android.util.Log;

import androidx.media3.exoplayer.hls.playlist.HlsAdFilter;

import com.onefilm.android.tv.api.config.RuleConfig;
import com.onefilm.android.tv.bean.Rule;
import com.onefilm.android.tv.core.adblock.AdBlockRule;
import com.onefilm.android.tv.core.adblock.M3u8AdFilter;
import com.onefilm.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把配置里的广告规则接到 media3 的 HLS 过滤钩子上。
 *
 * <p>规则来自 {@link RuleConfig}（VOD + Live 合并），开关来自 {@link Setting#isAdblock()}。
 * 规则集变化时才重新编译正则，避免每次播放都做一次 Pattern.compile。
 */
public final class HlsAdBlockProvider implements HlsAdFilter.Provider {

    private static final String TAG = "HlsAdBlock";
    private static final M3u8AdFilter.Limits LIMITS = new M3u8AdFilter.Limits(0.20, 90.0, 3, 400);

    private volatile List<Rule> cachedSource = List.of();
    private volatile List<AdBlockRule> cachedRules = List.of();

    public static void install() {
        HlsAdFilter.setProvider(new HlsAdBlockProvider());
        Log.d(TAG, "installed");
    }

    @Override
    public String filter(String playlistUrl, String playlistText) {
        if (!Setting.isAdblock()) return null;
        if (playlistText == null || playlistText.isEmpty()) return null;
        try {
            M3u8AdFilter.Result result =
                    M3u8AdFilter.INSTANCE.filter(playlistUrl, playlistText, rules(), LIMITS);
            if (!result.getChanged()) return null;
            Log.d(TAG, "hit=" + (result.getRule() == null ? "heuristic" : result.getRule())
                    + " removed=" + result.getRemovedSegments()
                    + " seconds=" + String.format(Locale.US, "%.2f", result.getRemovedSeconds())
                    + " url=" + playlistUrl);
            return result.getText();
        } catch (Throwable t) {
            Log.w(TAG, "filter failed", t);
            return null;
        }
    }

    private List<AdBlockRule> rules() {
        List<Rule> source;
        try {
            source = RuleConfig.get().getRules();
        } catch (Throwable t) {
            source = List.of();
        }
        if (source != cachedSource) {
            List<AdBlockRule> rules = new ArrayList<>();
            for (Rule rule : source) {
                if (rule.getHosts().isEmpty() || rule.getRegex().isEmpty()) continue;
                rules.add(new AdBlockRule(rule.getName(), rule.getHosts(), rule.getRegex()));
            }
            cachedSource = source;
            cachedRules = rules;
            Log.d(TAG, "rules loaded: " + rules.size());
        }
        return cachedRules;
    }
}
