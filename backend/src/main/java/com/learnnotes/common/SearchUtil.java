package com.learnnotes.common;

/**
 * MySQL LIKE 通配符转义工具（D10：对 q 做 %/_/\ 转义）。
 */
public final class SearchUtil {

    private SearchUtil() {
    }

    /**
     * 转义 LIKE 通配符并包裹为 %q% 模式。
     * 注意：SQL 中需配合 ESCAPE '\\' 使用。
     */
    public static String escapeLike(String q) {
        String escaped = q
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
