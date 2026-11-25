package com.onefilm.android.tv.player.adblock;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.api.config.RuleConfig;
import com.onefilm.android.tv.bean.Rule;
import com.onefilm.android.tv.core.adblock.AdBlockHost;
import com.onefilm.android.tv.core.adblock.AdBlockRuleSource;
import com.onefilm.android.tv.setting.Setting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把 :app 里的「去广告开关 / 规则配置 / 落盘目录」适配成 :adblock 的宿主接口。
 *
 * <p>存在的意义是拆掉循环依赖：:adblock 不认识 Setting / RuleConfig / Rule，
 * 只认识 {@link AdBlockHost}，由这里翻译一次。
 *
 * <p>注意 {@link #ruleSources()} 必须保持「同一份配置返回同一个 List 实例」的语义：
 * :adblock 用引用比较判断规则是否变化，变了才重新编译正则。直接 new ArrayList 会让缓存永久失效。
 */
public final class AppAdBlockHost implements AdBlockHost {

    private volatile List<Rule> cachedSource;

    private volatile List<AdBlockRuleSource> cachedOut = Collections.emptyList();

    @Override
    public boolean isEnabled() {
        try {
            return Setting.isAdblock();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<AdBlockRuleSource> ruleSources() {
        List<Rule> source;
        try {
            source = RuleConfig.get().getRules();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (source == null || source.isEmpty()) return Collections.emptyList();
        // RuleConfig 只在配置变更时生成新 List，因此引用相同即内容相同
        if (source == cachedSource) return cachedOut;
        List<AdBlockRuleSource> out = new ArrayList<>(source.size());
        for (Rule rule : source) {
            if (rule == null) continue;
            out.add(new AdBlockRuleSource(rule.getName(), nullSafe(rule.getHosts()), nullSafe(rule.getRegex())));
        }
        cachedSource = source;
        cachedOut = out;
        return out;
    }

    @Override
    public File dumpDir() {
        try {
            return App.get().getExternalFilesDir("adblock");
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<String> nullSafe(List<String> input) {
        return input == null ? Collections.emptyList() : input;
    }
}
