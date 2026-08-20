package com.learnnotes.imports;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导入元数据解析（D8）：优先级 hint > front-matter > 文件名 > INBOX 兜底。
 * 文件名规则见 AGENT-DOC-SPEC §1（`__` 分隔：3 段=大类+小方向+标识；2 段=大类+标识；1 段=INBOX）。
 */
public final class MetaResolver {

    public enum Source {
        HINT, FRONT_MATTER, FILENAME, INBOX
    }

    private MetaResolver() {
    }

    @Data
    public static class ResolvedMeta {
        private Source source;
        private String categoryName;
        private String categorySlug;
        private String topicName;
        private String topicSlug;
        /** 文档 slug；可能为空（由 title 生成） */
        private String docSlug;
        private String title;
        private List<String> tags = new ArrayList<>();
        private String summary;
        private Integer order;
        private List<String> warnings = new ArrayList<>();
    }

    public static ResolvedMeta resolve(String filename, String content,
                                       Map<String, Object> frontMatter,
                                       String categoryHint, String topicHint) {
        ResolvedMeta meta = new ResolvedMeta();
        String baseName = stripExt(filename);

        boolean hasHint = notBlank(categoryHint) || notBlank(topicHint);
        boolean hasFm = frontMatter != null
                && notBlank(asStr(frontMatter.get("category")))
                && notBlank(asStr(frontMatter.get("topic")));

        if (hasHint) {
            meta.setSource(Source.HINT);
            meta.setCategoryName(asStr(categoryHint));
            meta.setCategorySlug(blankToNull(asStr(frontMatter.get("category_slug"))));
            meta.setTopicName(asStr(topicHint));
            meta.setTopicSlug(blankToNull(asStr(frontMatter.get("topic_slug"))));
            fillDocFields(meta, frontMatter, baseName);
        } else if (hasFm) {
            meta.setSource(Source.FRONT_MATTER);
            meta.setCategoryName(asStr(frontMatter.get("category")));
            meta.setCategorySlug(blankToNull(asStr(frontMatter.get("category_slug"))));
            meta.setTopicName(asStr(frontMatter.get("topic")));
            meta.setTopicSlug(blankToNull(asStr(frontMatter.get("topic_slug"))));
            fillDocFields(meta, frontMatter, baseName);
        } else {
            String[] parts = baseName.split("__");
            if (parts.length >= 3) {
                meta.setSource(Source.FILENAME);
                meta.setCategoryName(parts[0].trim());
                meta.setTopicName(parts[1].trim());
                meta.setDocSlug(parts[2].trim());
            } else if (parts.length == 2) {
                meta.setSource(Source.FILENAME);
                meta.setCategoryName(parts[0].trim());
                meta.setTopicName("未归类");
                meta.setTopicSlug("uncategorized");
                meta.setDocSlug(parts[1].trim());
            } else {
                meta.setSource(Source.INBOX);
                meta.setCategoryName("INBOX");
                meta.setCategorySlug("inbox");
                meta.setTopicName("未归类");
                meta.setTopicSlug("uncategorized");
                meta.setDocSlug(baseName);
            }
            fillDocFields(meta, frontMatter, baseName);
            if (meta.getSource() == Source.FILENAME && (meta.getCategoryName().isEmpty() || meta.getTopicName().isEmpty())) {
                meta.setSource(Source.INBOX);
                meta.setCategoryName("INBOX");
                meta.setCategorySlug("inbox");
                meta.setTopicName("未归类");
                meta.setTopicSlug("uncategorized");
                meta.getWarnings().add("文件名解析出的大类/小方向为空，已回落 INBOX");
            }
        }
        return meta;
    }

    private static void fillDocFields(ResolvedMeta meta, Map<String, Object> fm, String baseName) {
        if (fm != null) {
            meta.setTitle(blankToNull(asStr(fm.get("title"))));
            meta.setDocSlug(blankToNull(asStr(fm.get("slug"))));
            meta.setSummary(blankToNull(asStr(fm.get("summary"))));
            meta.setTags(asStringList(fm.get("tags")));
            Object order = fm.get("order");
            if (order instanceof Number n) {
                meta.setOrder(n.intValue());
            }
        }
        // slug 兜底：文件名第三段
        if (meta.getDocSlug() == null || meta.getDocSlug().isBlank()) {
            String[] parts = baseName.split("__");
            if (parts.length >= 2) {
                meta.setDocSlug(parts[parts.length - 1].trim());
            }
        }
        if (meta.getDocSlug() != null && meta.getDocSlug().isBlank()) {
            meta.setDocSlug(null);
        }
    }

    // ---------- 小工具 ----------

    static String stripExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            return (List<String>) (List<?>) list;
        }
        if (o != null) {
            return List.of(String.valueOf(o));
        }
        return new ArrayList<>();
    }
}
