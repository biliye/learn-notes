package com.learnnotes.markdown;

import com.learnnotes.common.SlugUtil;

/**
 * 锚点算法 —— 严格实现设计规格 D5，实现不得改动（改动即改契约，历史见解全部漂移）。
 *
 * <pre>
 * norm(block):
 *   1. \r\n → \n（lone \r 一并处理）
 *   2. 逐行去掉行尾空白
 *   3. 去掉首尾空行
 *   4. 仅对非代码块：把行内连续空白（空格 / Tab）折叠为单个空格（代码块必须保留缩进）
 *
 * hash8  = sha1(norm(block)).hex().toLowerCase().substring(0, 8)
 * anchor = "b" + index + "-" + hash8        // index 为 0 起的块序号
 * </pre>
 */
public final class AnchorUtil {

    private AnchorUtil() {
    }

    public static String normalize(String raw, boolean isCode) {
        // 1. 统一换行
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');

        // 2. 逐行去掉行尾空白
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!isCode) {
                // 4. 非代码块：折叠行内连续空白为单个空格（保留行结构）
                line = line.replaceAll("[ \\t]+", " ");
            }
            sb.append(line.replaceAll("[ \\t]+$", ""));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }

        // 3. 去掉首尾空行
        String out = sb.toString();
        out = out.replaceAll("(^\\n+)|(\\n+$)", "");
        return out;
    }

    public static String hash8(String normalized) {
        return SlugUtil.sha1Hex(normalized).substring(0, 8);
    }

    public static String anchor(int index, String hash8) {
        return "b" + index + "-" + hash8;
    }

    /**
     * 从 anchor 反解块序号（T09 创建/重挂时用）。
     *
     * @return 0 起的块序号；格式非法返回 -1
     */
    public static int parseIndex(String anchor) {
        if (anchor == null || !anchor.startsWith("b")) {
            return -1;
        }
        int dash = anchor.indexOf('-');
        if (dash <= 1) {
            return -1;
        }
        try {
            return Integer.parseInt(anchor.substring(1, dash));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 从 anchor 反解 hash8；格式非法返回 null。
     */
    public static String parseHash(String anchor) {
        if (anchor == null || !anchor.startsWith("b")) {
            return null;
        }
        int dash = anchor.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String hash = anchor.substring(dash + 1);
        return hash.length() == 8 ? hash : null;
    }
}
