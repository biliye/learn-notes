package com.learnnotes.imports;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导入元数据解析（D8）：优先级 hint > front-matter(path/category+topic) > 文件名 > INBOX 兜底。
 * <p>V4 起支持多级目录：归类目标是一个"目录路径"（大类 → … → 存放文档的叶目录）。
 * <ul>
 *   <li>front-matter 用 <code>path: [大类, 中目录, …, 叶目录]</code> + 可选
 *       <code>slugs: [大类slug, …, 叶目录slug]</code>（旧 category/topic(+_slug) 仍兼容）；</li>
 *   <li>文件名 <code>__</code> 分隔：1 段=INBOX；2 段=大类+文档 slug（文档落该大类下"未归类"）；
 *       3 段=大类__目录__文档 slug（即现有两级）；≥4 段=目录链（根→叶）+文档 slug；</li>
 *   <li>单目录路径（只给了大类）统一落到该大类下"未归类"子目录（保持旧语义）。</li>
 * </ul>
 */
public final class MetaResolver {

    public static final String UNCATEGORIZED_NAME = "未归类";
    public static final String UNCATEGORIZED_SLUG = "uncategorized";

    public enum Source {
        HINT, FRONT_MATTER, FILENAME, INBOX
    }

    private MetaResolver() {
    }

    @Data
    public static class ResolvedMeta {
        private Source source;
        /** 目录路径（大类 → … → 叶目录）。仅 1 项时由导入端自动追加"未归类"子目录 */
        private List<String> pathNames = new ArrayList<>();
        /** 与 pathNames 对应的可选 slug（可为 null，表示由后端按名称生成） */
        private List<String> pathSlugs = new ArrayList<>();
        /** 兼容旧字段：大类名/slug = 路径第一段 */
        private String categoryName;
        private String categorySlug;
        /** 兼容旧字段：叶目录名/slug = 路径最后一段（单层路径时为 未归类/uncategorized） */
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
        List<String> fmPath = parsePathList(frontMatter == null ? null : frontMatter.get("path"));
        boolean hasFmPath = fmPath != null && !fmPath.isEmpty();
        boolean hasFmLegacy = frontMatter != null
                && notBlank(asStr(frontMatter.get("category")))
                && notBlank(asStr(frontMatter.get("topic")));

        if (hasHint) {
            meta.setSource(Source.HINT);
            if (notBlank(categoryHint)) {
                meta.getPathNames().add(asStr(categoryHint).trim());
                meta.getPathSlugs().add(blankToNull(asStr(frontMatter == null ? null : frontMatter.get("category_slug"))));
            }
            if (notBlank(topicHint)) {
                meta.getPathNames().add(asStr(topicHint).trim());
                meta.getPathSlugs().add(blankToNull(asStr(frontMatter == null ? null : frontMatter.get("topic_slug"))));
            }
            fillDocFields(meta, frontMatter, baseName);
        } else if (hasFmPath) {
            meta.setSource(Source.FRONT_MATTER);
            meta.setPathNames(fmPath);
            List<String> fmSlugs = parseStringList(frontMatter.get("slugs"));
            meta.setPathSlugs(fmSlugs == null ? nullSlugs(fmPath.size()) : fmSlugs);
            fillDocFields(meta, frontMatter, baseName);
        } else if (hasFmLegacy) {
            meta.setSource(Source.FRONT_MATTER);
            meta.getPathNames().add(asStr(frontMatter.get("category")).trim());
            meta.getPathNames().add(asStr(frontMatter.get("topic")).trim());
            meta.getPathSlugs().add(blankToNull(asStr(frontMatter.get("category_slug"))));
            meta.getPathSlugs().add(blankToNull(asStr(frontMatter.get("topic_slug"))));
            fillDocFields(meta, frontMatter, baseName);
        } else {
            String[] parts = baseName.split("__");
            if (parts.length >= 3) {
                // 目录链 = 前 N-1 段，文档 slug = 末段
                meta.setSource(Source.FILENAME);
                for (int i = 0; i < parts.length - 1; i++) {
                    meta.getPathNames().add(parts[i].trim());
                    meta.getPathSlugs().add(null);
                }
                meta.setDocSlug(parts[parts.length - 1].trim());
            } else if (parts.length == 2) {
                meta.setSource(Source.FILENAME);
                meta.getPathNames().add(parts[0].trim());
                meta.getPathSlugs().add(null);
                meta.setDocSlug(parts[1].trim());
            } else {
                meta.setSource(Source.INBOX);
                meta.getPathNames().add("INBOX");
                meta.getPathSlugs().add("inbox");
                meta.setDocSlug(baseName);
            }
            fillDocFields(meta, frontMatter, baseName);
            if (meta.getSource() == Source.FILENAME
                    && (meta.getPathNames().isEmpty() || meta.getPathNames().stream().anyMatch(String::isBlank))) {
                meta.setSource(Source.INBOX);
                meta.getPathNames().clear();
                meta.getPathSlugs().clear();
                meta.getPathNames().add("INBOX");
                meta.getPathSlugs().add("inbox");
                meta.getWarnings().add("文件名解析出的目录为空，已回落 INBOX");
            }
        }
        // slugs 与 names 对齐（缺失位按 null，由导入端按名称生成）
        while (meta.getPathSlugs().size() < meta.getPathNames().size()) {
            meta.getPathSlugs().add(null);
        }
        applyCompatibilityFields(meta);
        return meta;
    }

    /** 由路径推导 category/topic 兼容字段（单层路径→未归类，保持旧接口语义） */
    private static void applyCompatibilityFields(ResolvedMeta meta) {
        List<String> names = meta.getPathNames();
        List<String> slugs = meta.getPathSlugs();
        meta.setCategoryName(names.isEmpty() ? null : names.get(0));
        meta.setCategorySlug(slugs.isEmpty() ? null : slugs.get(0));
        if (names.size() >= 2) {
            meta.setTopicName(names.get(names.size() - 1));
            meta.setTopicSlug(slugs.get(slugs.size() - 1));
        } else if (names.size() == 1) {
            meta.setTopicName(UNCATEGORIZED_NAME);
            meta.setTopicSlug(UNCATEGORIZED_SLUG);
        }
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
        // slug 兜底：文件名末段
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

    /** front-matter path：允许 YAML 列表（[A, B]）或单个字符串 */
    static List<String> parsePathList(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String s = item == null ? null : String.valueOf(item).trim();
                if (s != null && !s.isEmpty()) {
                    out.add(s);
                }
            }
            return out.isEmpty() ? null : out;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : List.of(s);
    }

    static String stripExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static List<String> parseStringList(Object o) {
        if (!(o instanceof List<?> list)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String s = item == null ? null : String.valueOf(item).trim();
            out.add(s == null || s.isEmpty() ? null : s);
        }
        return out.isEmpty() ? null : out;
    }

    private static List<String> nullSlugs(int size) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(null);
        }
        return list;
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
