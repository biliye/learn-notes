package com.learnnotes.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * slug 生成规则 —— 严格实现设计规格 §6 的 7 步算法，前后端必须一致。
 *
 * <pre>
 * slugify(text):
 *  1. 转小写、trim；
 *  2. 保留 [a-z0-9]，把 . _ 空格 及其他分隔符转为 -；
 *  3. 非 ASCII 字符（中文等）整段替换为 -；
 *  4. 折叠连续 -、去首尾 -；
 *  5. 若结果为空或只剩 -：使用 doc- + sha1(原文).substring(0,8)（分类节点用 node- 前缀）；
 *  6. 截断至 60 字符；
 *  7. 入库前若在同一父节点/小方向下已存在且不是同一实体，追加 -2、-3…（导入路径按 onConflict 处理，不追加后缀）。
 * </pre>
 */
public final class SlugUtil {

    private static final int MAX_LEN = 60;

    private SlugUtil() {
    }

    public static String slugify(String text) {
        return slugify(text, "doc");
    }

    /**
     * @param fallbackPrefix 兜底哈希前缀，分类节点用 "node"，文档用 "doc"
     */
    public static String slugify(String text, String fallbackPrefix) {
        String src = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < src.length(); ) {
            int cp = src.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')) {
                sb.append((char) cp);
            } else if (Character.isWhitespace(cp) || cp == '.' || cp == '_' || cp == '-') {
                sb.append('-');
            } else {
                // 非 ASCII（中文等）整段替换为 -
                sb.append('-');
            }
        }
        String slug = sb.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (slug.isEmpty() || slug.equals("-")) {
            slug = fallbackPrefix + "-" + sha1Hex(src).substring(0, 8);
        }
        if (slug.length() > MAX_LEN) {
            slug = slug.substring(0, MAX_LEN).replaceAll("-+$", "");
        }
        return slug;
    }

    /**
     * 校验用户输入的 slug 可安全用作 URL 段与导出 zip 条目路径：
     * 拒绝 ..、路径分隔符、前导点、盘符（防目录穿越 / zip-slip）。null/空白放行（由调用方决定生成策略）。
     */
    public static void validateSafeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        if (slug.contains("..") || slug.contains("/") || slug.contains("\\")
                || slug.startsWith(".") || slug.matches("^[a-zA-Z]:.*")) {
            throw BizException.badRequest("slug 含非法字符，已拒绝：" + slug);
        }
    }

    /**
     * sha1 hex 小写。
     */
    public static String sha1Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }
}
