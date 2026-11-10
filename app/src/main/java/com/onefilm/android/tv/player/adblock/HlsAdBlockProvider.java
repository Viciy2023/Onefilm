package com.onefilm.android.tv.player.adblock;

import android.util.Log;

import androidx.media3.exoplayer.hls.playlist.HlsAdFilter;

import com.onefilm.android.tv.api.config.RuleConfig;
import com.onefilm.android.tv.bean.Rule;
import com.onefilm.android.tv.core.adblock.AdBlockRule;
import com.onefilm.android.tv.core.adblock.AdFilterNotifier;
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
    private static final M3u8AdFilter.Limits LIMITS = new M3u8AdFilter.Limits(0.20, 90.0, 3, 3000, 16);
    private static final M3u8AdFilter.PtsProbe PROBE = new HlsPtsProbe();

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
                    M3u8AdFilter.INSTANCE.filter(playlistUrl, playlistText, rules(), LIMITS, PROBE);
            if (!result.getChanged()) {
                // 未命中时输出一行摘要，便于定位「广告没被过滤」是识别问题还是规则问题。
                Log.d(TAG, "pass " + describe(playlistUrl, playlistText));
                return null;
            }
            String hit = result.getRule() != null
                    ? result.getRule()
                    : (result.getPts() ? "pts" : "heuristic");
            Log.d(TAG, "hit=" + hit
                    + " removed=" + result.getRemovedSegments()
                    + " seconds=" + String.format(Locale.US, "%.2f", result.getRemovedSeconds())
                    + " url=" + playlistUrl);
            AdFilterNotifier.INSTANCE.notifyFiltered(result.getRemovedSeconds());
            return result.getText();
        } catch (Throwable t) {
            Log.w(TAG, "filter failed", t);
            return null;
        }
    }

    /** 未命中时的诊断摘要：主机、分片数、主/媒体列表、ENDLIST、断点、前若干分片时长。 */
    private static String describe(String playlistUrl, String text) {
        String host = "";
        try {
            String u = playlistUrl == null ? "" : playlistUrl;
            int start = u.indexOf("://");
            if (start > 0) {
                int end = u.indexOf('/', start + 3);
                host = end > 0 ? u.substring(start + 3, end) : u.substring(start + 3);
            } else {
                host = u;
            }
        } catch (Throwable ignored) {
        }
        int segments = 0;
        StringBuilder durations = new StringBuilder();
        int from = 0;
        while (true) {
            int idx = text.indexOf("#EXTINF:", from);
            if (idx < 0) break;
            int comma = text.indexOf(',', idx);
            if (comma < 0) break;
            String value = text.substring(idx + 8, comma).trim();
            segments++;
            if (segments <= 12) {
                try {
                    durations.append(String.format(Locale.US, "%.1f", Double.parseDouble(value))).append(',');
                } catch (Throwable ignored) {
                    durations.append("?,");
                }
            }
            from = comma + 1;
        }
        return "url=" + playlistUrl
                + " host=" + host
                + " segs=" + segments
                + " master=" + text.contains("#EXT-X-STREAM-INF")
                + " endlist=" + text.contains("#EXT-X-ENDLIST")
                + " disc=" + countOf(text, "#EXT-X-DISCONTINUITY")
                + " key=" + countOf(text, "#EXT-X-KEY")
                + " map=" + countOf(text, "#EXT-X-MAP")
                + " range=" + countOf(text, "#EXT-X-BYTERANGE")
                + " first=" + durations;
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) return count;
            count++;
            from = idx + needle.length();
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
