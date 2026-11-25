package com.fongmi.quickjs.bean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * HTML 解析缓存。
 *
 * <p>drpy 的 {@code pdfh}/{@code pdfa}/{@code pdfl} 会对同一段 HTML 反复取规则，
 * 每次重新 {@code Jsoup.parse} 既慢又会产生大量临时对象。这里按 HTML 文本缓存解析结果，
 * 保持同样的缓存行为。
 */
public class Cache {

    private String pdfhHtml;
    private String pdfaHtml;
    private Document pdfhDoc;
    private Document pdfaDoc;

    public Document getPdfh(String html) {
        if (html == null) html = "";
        if (html.equals(pdfhHtml) && pdfhDoc != null) return pdfhDoc;
        pdfhHtml = html;
        pdfhDoc = Jsoup.parse(html);
        return pdfhDoc;
    }

    public Document getPdfa(String html) {
        if (html == null) html = "";
        if (html.equals(pdfaHtml) && pdfaDoc != null) return pdfaDoc;
        pdfaHtml = html;
        pdfaDoc = Jsoup.parse(html);
        return pdfaDoc;
    }
}
