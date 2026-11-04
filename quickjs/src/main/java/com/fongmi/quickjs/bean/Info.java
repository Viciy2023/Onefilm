package com.fongmi.quickjs.bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一条解析规则的拆分结果。
 *
 * <p>规则语法（与 TVBox / 欧歌一致）：
 * <ul>
 *   <li>{@code div.item:eq(2)} → 选择第 3 个，{@link #index} = 2</li>
 *   <li>{@code div.item--script} → 选中后排除 script 子节点</li>
 * </ul>
 */
public class Info {

    public String rule = "";
    public int index = -1;
    public List<String> excludes;

    public Info(String str) {
        rule = str == null ? "" : str;
        if (rule.contains(":eq")) {
            String[] parts = rule.split(":");
            rule = parts[0];
            index = parseIndex(parts.length > 1 ? parts[1] : "");
        } else if (rule.contains("--")) {
            String[] parts = rule.split("--");
            rule = parts[0];
            excludes = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length));
        }
    }

    private int parseIndex(String text) {
        try {
            String digits = text.replaceAll("[^0-9-]", "");
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (Throwable e) {
            return -1;
        }
    }
}
