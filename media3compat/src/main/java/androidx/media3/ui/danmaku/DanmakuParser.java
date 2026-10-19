package androidx.media3.ui.danmaku;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Bilibili-style XML danmaku documents. */
public final class DanmakuParser {

    private static final Pattern ITEM = Pattern.compile("<d p=\"([^\"]+)\"[^>]*>(.*?)</d>", Pattern.DOTALL);

    private DanmakuParser() {
    }

    public static List<DanmakuItem> parseXml(String xml) {
        List<DanmakuItem> items = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return items;
        Matcher matcher = ITEM.matcher(xml);
        while (matcher.find()) {
            try {
                String[] p = matcher.group(1).split(",");
                if (p.length < 4) continue;
                long timeMs = (long) (Float.parseFloat(p[0]) * 1000);
                int mode = Integer.parseInt(p[1]);
                float size = Float.parseFloat(p[2]);
                long colorValue = Long.parseLong(p[3]);
                int color = (int) (0xFF000000L | (colorValue & 0xFFFFFFL));
                String text = unescape(matcher.group(2));
                if (text.isEmpty()) continue;
                if (mode != DanmakuItem.MODE_SCROLL && mode != DanmakuItem.MODE_TOP && mode != DanmakuItem.MODE_BOTTOM) {
                    mode = DanmakuItem.MODE_SCROLL;
                }
                items.add(new DanmakuItem(timeMs, mode, color, size, text));
            } catch (RuntimeException ignored) {
            }
        }
        items.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return items;
    }

    private static String unescape(String text) {
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&apos;", "'");
    }
}
