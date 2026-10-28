package com.onefilm.android.tv.bean;

import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;
import com.onefilm.android.tv.App;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多仓（仓库）状态。
 *
 * <p>TVBox 的多仓配置形如 {@code {"urls":[{"name":"xx线路","url":"..."}]}}：
 * 主配置本身不是一个可用的站点集合，而是"仓库"，里面的每一项才是一条真正的线路。
 *
 * <p>这里只保存三样东西（都是字符串，放 Prefers）：
 * <ul>
 *   <li>当前仓库的名字和地址；</li>
 *   <li>当前仓库包含的线路列表；</li>
 *   <li>当前选中的线路地址。</li>
 * </ul>
 */
public class DepotStore {

    private static final String KEY_DEPOT = "depot_current";
    private static final String KEY_LINES = "depot_lines_";
    private static final String KEY_LINE = "depot_line";

    public static class Entry {

        private String name;
        private String url;

        public Entry() {
        }

        public Entry(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name == null ? "" : name;
        }

        public String getUrl() {
            return url == null ? "" : url;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /** 选中一个仓库：记录仓库本身和它包含的线路。 */
    public static void selectDepot(String name, String url, List<Depot> lines) {
        if (url == null || url.isEmpty()) return;
        List<Entry> entries = new ArrayList<>();
        for (Depot depot : lines) {
            if (depot.getUrl().isEmpty()) continue;
            entries.add(new Entry(depot.getName(), depot.getUrl()));
        }
        Prefers.put(KEY_DEPOT, App.gson().toJson(new Entry(name, url)));
        Prefers.put(KEY_LINES + Crypto.md5(url), App.gson().toJson(entries));
    }

    /** 当前仓库名（没有多仓时返回空串）。 */
    public static String currentName() {
        Entry entry = current();
        return entry == null ? "" : entry.getName();
    }

    /** 当前仓库地址。 */
    public static String currentUrl() {
        Entry entry = current();
        return entry == null ? "" : entry.getUrl();
    }

    /** 当前仓库包含的线路；没有多仓时返回空列表。 */
    public static List<Entry> lines() {
        String url = currentUrl();
        if (url.isEmpty()) return Collections.emptyList();
        String json = Prefers.getString(KEY_LINES + Crypto.md5(url), "");
        if (json.isEmpty()) return Collections.emptyList();
        try {
            Type type = TypeToken.getParameterized(List.class, Entry.class).getType();
            List<Entry> items = App.gson().fromJson(json, type);
            return items == null ? Collections.emptyList() : items;
        } catch (Throwable e) {
            return Collections.emptyList();
        }
    }

    /** 当前选中的线路地址。 */
    public static String selectedLine() {
        return Prefers.getString(KEY_LINE, "");
    }

    public static void selectLine(String url) {
        Prefers.put(KEY_LINE, url == null ? "" : url);
    }

    /** 这个地址是否是当前仓库里的某条线路。 */
    public static boolean isLine(String url) {
        if (url == null || url.isEmpty()) return false;
        for (Entry entry : lines()) if (url.equals(entry.getUrl())) return true;
        return false;
    }

    /** 如果这个地址属于当前仓库的某条线路，就把它记为"当前线路"。 */
    public static void markLineIfKnown(String url) {
        if (url == null || url.isEmpty()) return;
        for (Entry entry : lines()) {
            if (url.equals(entry.getUrl())) {
                selectLine(url);
                return;
            }
        }
    }

    private static Entry current() {
        String json = Prefers.getString(KEY_DEPOT, "");
        if (json.isEmpty()) return null;
        try {
            return App.gson().fromJson(json, Entry.class);
        } catch (Throwable e) {
            return null;
        }
    }
}
