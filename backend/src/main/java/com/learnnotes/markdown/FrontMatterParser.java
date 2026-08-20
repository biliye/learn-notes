package com.learnnotes.markdown;

/**
 * front-matter 解析（D8）：必须位于文件最开头，以 `---` 单独一行开始、`---` 单独一行结束。
 * 字段为扁平 YAML（见 AGENT-DOC-SPEC §2），支持 tags 的 `[a, b]` 行内数组与 `- item` 列表。
 */
public final class FrontMatterParser {

    private FrontMatterParser() {
    }

    /**
     * 解析文档。若无 front-matter，body 为原文、meta 为空。
     */
    public static ParsedDoc parse(String markdown) {
        ParsedDoc out = new ParsedDoc();
        if (markdown == null) {
            out.setBody("");
            return out;
        }
        if (!startsWithFence(markdown)) {
            out.setBody(markdown);
            return out;
        }
        int newline = markdown.indexOf('\n');
        if (newline < 0) {
            out.setBody(markdown);
            return out;
        }
        int searchFrom = newline + 1;
        int end = findClosingFence(markdown, searchFrom);
        if (end < 0) {
            // 只有开头的 ---，没有闭合：按无 front-matter 处理
            out.setBody(markdown);
            return out;
        }
        String yamlBlock = markdown.substring(searchFrom, end);
        // 闭合 --- 之后可能跟空行，其余全部是正文
        String rest = markdown.substring(end + 3);
        String body = rest.replaceFirst("^[ \\t]*\\r?\\n", "");
        out.setHasFrontMatter(true);
        out.setMeta(new MiniYaml().parse(yamlBlock));
        out.setBody(body);
        return out;
    }

    private static boolean startsWithFence(String s) {
        // 文件必须从 --- 开头（允许其后紧跟换行或 CRLF）
        return s.startsWith("---") && (s.length() == 3 || s.charAt(3) == '\n' || s.charAt(3) == '\r');
    }

    private static int findClosingFence(String s, int from) {
        int pos = from;
        while (pos < s.length()) {
            int nl = s.indexOf('\n', pos);
            String line = nl < 0 ? s.substring(pos) : s.substring(pos, nl);
            String trimmed = line.trim();
            if (trimmed.equals("---") || trimmed.equals("...")) {
                // 返回该行在原文中的起始位置（不含行尾换行）
                return pos;
            }
            if (nl < 0) {
                return -1;
            }
            pos = nl + 1;
        }
        return -1;
    }

    /**
     * 最小 YAML 解析器：只支持扁平键值、`[a, b]` 行内数组、`- item` 列表、引号与裸标量。
     * 不支持的复杂结构（嵌套映射等）按字符串原样保留。
     */
    static final class MiniYaml {

        java.util.Map<String, Object> parse(String text) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            java.util.List<String> pendingList = new java.util.ArrayList<>();
            String pendingKey = null;
            for (String rawLine : text.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("- ")) {
                    // 列表项：属于 pendingKey（前一行是 `key:`）
                    if (pendingKey != null) {
                        pendingList.add(stripScalar(line.substring(2).trim()));
                    }
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.isEmpty()) {
                    // 可能后随列表
                    pendingKey = key;
                    pendingList = new java.util.ArrayList<>();
                    continue;
                }
                map.put(key, parseValue(value));
                pendingKey = null;
            }
            if (pendingKey != null && !pendingList.isEmpty()) {
                map.put(pendingKey, pendingList);
            }
            return map;
        }

        Object parseValue(String v) {
            v = stripScalar(v);
            if (v.startsWith("[") && v.endsWith("]")) {
                java.util.List<String> list = new java.util.ArrayList<>();
                String inner = v.substring(1, v.length() - 1);
                if (!inner.isBlank()) {
                    for (String item : inner.split(",")) {
                        list.add(stripScalar(item.trim()));
                    }
                }
                return list;
            }
            if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
                return Boolean.valueOf(v);
            }
            try {
                if (v.matches("-?\\d+")) {
                    return Long.parseLong(v);
                }
                if (v.matches("-?\\d+\\.\\d+")) {
                    return Double.parseDouble(v);
                }
            } catch (NumberFormatException ignore) {
                // fallthrough
            }
            return v;
        }

        String stripScalar(String v) {
            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                return v.substring(1, v.length() - 1);
            }
            return v;
        }
    }
}
