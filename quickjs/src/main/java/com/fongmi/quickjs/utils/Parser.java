package com.fongmi.quickjs.utils;

import android.text.TextUtils;

import com.fongmi.quickjs.bean.Cache;
import com.fongmi.quickjs.bean.Info;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * drpy / TVBox 规则解析器：实现 JS 里的 pdfh / pdfa / pdfl。
 *
 * <p>盒子上分发的 JS 库（drpy.min.js、drpy2.min.js、alist.min.js）按 TVBox 式宿主编写，
 * 初始化时会直接调用全局 {@code pdfh}/{@code pdfa}/{@code pdfl}。宿主不提供这三个函数时，
 * 每个 JS 站点都会初始化失败，进而反复重建 QuickJS 上下文，native 内存暴涨。
 *
 * <p>实现与欧歌保持一致：jsoup 解析 + 按 HTML 文本缓存 {@link Document}。
 */
public class Parser {

    /** pdfl 返回值的分隔符：名称 + "$" + 地址，与 TVBox 一致。 */
    private static final String SEPARATOR = "$";

    private final Pattern URL = Pattern.compile("url\\((.*?)\\)", Pattern.DOTALL);
    private final Pattern NO_ADD = Pattern.compile(":eq|:lt|:gt|:first|:last|:not|:even|:odd|:has|:contains|:matches|:empty|^body$|^#");
    private final Pattern JOIN_URL = Pattern.compile("(url|src|href|-original|-src|-play|-url|style)$|^(data-|url-|src-)", Pattern.CASE_INSENSITIVE);
    private final Pattern SPEC_URL = Pattern.compile("^(ftp|magnet|thunder|ws):", Pattern.CASE_INSENSITIVE);

    private final Cache cache = new Cache();

    private Info getParseInfo(String rule) {
        return new Info(rule);
    }

    /**
     * 把 TVBox 风格规则转成 jsoup 可用的选择器（补 :eq(0)）。
     */
    private String parseHikerToJq(String rule, boolean first) {
        if (!rule.contains("&&")) {
            String[] splits = rule.split(" ");
            String last = splits[splits.length - 1];
            return (NO_ADD.matcher(last).find() || !first) ? rule : rule + ":eq(0)";
        }
        String[] splits = rule.split("&&");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < splits.length; i++) {
            String[] sub = splits[i].split(" ");
            String last = sub[sub.length - 1];
            if (NO_ADD.matcher(last).find()) out.add(splits[i]);
            else if (first || i < splits.length - 1) out.add(splits[i] + ":eq(0)");
            else out.add(splits[i]);
        }
        return TextUtils.join(" ", out);
    }

    private Elements parseOneRule(Document doc, String rule, Elements elements) {
        Info info = getParseInfo(rule);
        Elements selected;
        try {
            selected = (elements == null || elements.isEmpty()) ? doc.select(info.rule) : elements.select(info.rule);
        } catch (Throwable e) {
            return new Elements();
        }
        if (rule.contains(":eq")) {
            int index = info.index;
            selected = index < 0 ? selected.eq(selected.size() + index) : selected.eq(index);
        }
        if (info.excludes != null && !selected.isEmpty()) {
            Elements copy = selected.clone();
            for (String exclude : info.excludes) copy.select(exclude).remove();
            selected = copy;
        }
        return selected;
    }

    public List<String> parseDomForArray(String html, String rule) {
        Document doc = cache.getPdfa(html);
        String[] rules = parseHikerToJq(rule, false).split(" ");
        Elements selected = new Elements();
        for (String item : rules) {
            selected = parseOneRule(doc, item, selected);
            if (selected.isEmpty()) return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Element element : selected) out.add(element.outerHtml());
        return out;
    }

    public List<String> parseDomForList(String html, String rule, String list, String url, String text) {
        String[] rules = parseHikerToJq(rule, false).split(" ");
        Elements selected = new Elements();
        for (String item : rules) {
            selected = parseOneRule(cache.getPdfa(html), item, selected);
            if (selected.isEmpty()) return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Element element : selected) {
            String outer = element.outerHtml();
            out.add(parseDomForUrl(outer, list, "").trim() + SEPARATOR + parseDomForUrl(outer, url, text));
        }
        return out;
    }

    public String parseDomForUrl(String html, String rule, String baseUrl) {
        Document doc = cache.getPdfh(html);
        if ("body&&Text".equals(rule) || "Text".equals(rule)) return doc.text();
        if ("body&&Html".equals(rule) || "Html".equals(rule)) return doc.html();
        String attribute = "";
        if (rule != null && rule.contains("&&")) {
            String[] parts = rule.split("&&");
            attribute = parts[parts.length - 1];
            rule = TextUtils.join("&&", Arrays.asList(parts).subList(0, parts.length - 1));
        }
        String[] rules = parseHikerToJq(rule, true).split(" ");
        Elements selected = new Elements();
        for (String item : rules) {
            selected = parseOneRule(doc, item, selected);
            if (selected.isEmpty()) return "";
        }
        if (TextUtils.isEmpty(attribute)) return selected.outerHtml();
        if ("Text".equals(attribute)) return selected.text();
        if ("Html".equals(attribute)) return selected.html();
        String result = "";
        for (String attr : attribute.split("\\|\\|")) {
            String value = selected.attr(attr);
            if (attr.toLowerCase().contains("style") && value.contains("url(")) {
                Matcher matcher = URL.matcher(value);
                if (matcher.find()) value = matcher.group(1);
                value = value.replaceAll("^['|\"](.*)['|\"]$", "$1");
            }
            result = (value.isEmpty() || TextUtils.isEmpty(baseUrl) || !JOIN_URL.matcher(attr).find() || SPEC_URL.matcher(value).find())
                    ? value
                    : value.contains("http") ? value.substring(value.indexOf("http")) : resolve(baseUrl, value);
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    private String resolve(String base, String relative) {
        try {
            return new URL(new URL(base), relative).toString();
        } catch (Throwable e) {
            return relative;
        }
    }
}
