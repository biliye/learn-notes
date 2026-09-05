package com.learnnotes.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.config.AppProperties;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.markdown.FrontMatterParser;
import com.learnnotes.markdown.ParsedDoc;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 全量导出（R31–R32、D12）：人可读 md 文件树 + 见解旁挂 + 图片 + manifest.json。
 * V4 起目录结构为任意深度：zip 内路径 = 完整目录 slug 链（大类 → … → 叶目录）/ 文档 slug，
 * 每个导出 md 的 front-matter 带 <code>path</code>（目录名链）与 <code>slugs</code>（目录 slug 链），
 * 保证备份可无损还原到多级目录。目录结构与 .insights.json 字段是恢复路径的输入格式，改动须回评审。
 * V3 起：管理员导出全部；普通用户只导出自己的文档与分类。
 */
@Service
public class ExportService {

    /** 正文里站内图片引用 */
    private static final Pattern IMAGE_REF = Pattern.compile("/uploads/[0-9a-zA-Z/._-]+");

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final DocMapper docMapper;
    private final CatalogNodeMapper catalogMapper;
    private final DocAnnotationMapper annotationMapper;
    private final AppProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExportService(DocMapper docMapper, CatalogNodeMapper catalogMapper,
                         DocAnnotationMapper annotationMapper, AppProperties props) {
        this.docMapper = docMapper;
        this.catalogMapper = catalogMapper;
        this.annotationMapper = annotationMapper;
        this.props = props;
    }

    public String zipFileName() {
        return "learn-notes-export-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip";
    }

    /**
     * 流式写出 zip（HTTP 路径由 Controller 包装为 StreamingResponseBody，禁止内存拼整包）。
     */
    public void writeZip(OutputStream out, CurrentUser user) throws IOException {
        // 顶层大类（INBOX 是 Flyway 种子的常驻兜底路径，不参与恢复 manifest）
        List<CatalogNode> roots = catalogMapper.selectByParent(user.userId(), 0).stream()
                .filter(c -> !"inbox".equals(c.getSlug()))
                .toList();
        // 管理员导出全部用户数据；普通用户只导出自己的
        List<Doc> docs = user.isAdmin() ? docMapper.selectAll() : docMapper.selectByOwner(user.userId());

        // 图片引用集合（去掉 /uploads/ 前缀，得到相对 uploadDir 的路径）
        Set<String> referencedImages = new HashSet<>();
        for (Doc doc : docs) {
            Matcher m = IMAGE_REF.matcher(doc.getContentMd());
            while (m.find()) {
                referencedImages.add(m.group().substring("/uploads/".length()));
            }
        }

        int annotations = 0;
        for (Doc doc : docs) {
            annotations += annotationMapper.selectByDoc(doc.getId()).size();
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("exportedAt", LocalDateTime.now().toString());
        manifest.put("specVersion", "v2");
        manifest.put("zipName", zipFileName());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("categories", roots.size());
        counts.put("docs", docs.size());
        counts.put("annotations", annotations);
        counts.put("images", referencedImages.size());
        int dirs = 0;
        for (CatalogNode r : roots) {
            dirs += countSubtreeDirs(r);
        }
        counts.put("dirs", dirs);
        manifest.put("counts", counts);

        List<Map<String, Object>> categoryMeta = new ArrayList<>();
        for (CatalogNode root : roots) {
            categoryMeta.add(nodeMeta(root));
        }
        manifest.put("categories", categoryMeta);

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // manifest.json
            writeEntry(zos, "manifest.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));

            // 文档 + 见解（路径 = 完整目录 slug 链 / 文档 slug）
            for (Doc doc : docs) {
                List<CatalogNode> chain = pathFromRoot(doc.getTopicId());
                if (chain.isEmpty()) {
                    continue;
                }
                String dirPath = chain.stream().map(CatalogNode::getSlug).collect(Collectors.joining("/"));
                String docPath = dirPath + "/" + doc.getSlug();

                String md = mdWithFrontMatter(doc, chain);
                writeEntry(zos, docPath + ".md", md);

                List<DocAnnotation> anns = annotationMapper.selectByDoc(doc.getId());
                List<Map<String, Object>> annList = new ArrayList<>();
                for (DocAnnotation ann : anns) {
                    Map<String, Object> am = new LinkedHashMap<>();
                    am.put("anchor", "b" + ann.getAnchorIndex() + "-" + ann.getAnchorHash());
                    am.put("anchorIndex", ann.getAnchorIndex());
                    am.put("blockSnippet", ann.getBlockSnippet());
                    am.put("contentMd", ann.getContentMd());
                    am.put("status", ann.getStatus());
                    am.put("docVersionAtCreate", ann.getDocVersionAtCreate());
                    am.put("createdAt", ann.getCreatedAt() == null ? null : ann.getCreatedAt().toString());
                    annList.add(am);
                }
                writeEntry(zos, docPath + ".insights.json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(annList));
            }

            // 图片（只打包被正文引用到的；孤儿图只报数）
            // 安全：正文里的 /uploads/ 引用是用户可写内容，必须归一化后校验仍在 uploadDir 内，否则 ../ 可逃出上传目录读取任意文件
            Path uploadRoot = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
            for (String rel : referencedImages) {
                Path src = resolveWithinUpload(uploadRoot, rel);
                if (src != null && Files.exists(src)) {
                    writeEntry(zos, "uploads/" + rel, Files.readAllBytes(src));
                }
            }
        }
    }

    /**
     * 导出 md：front-matter 规范化为 path（目录名链）/slugs（目录 slug 链）以保证按原位置还原；
     * 其余 front-matter 键（title/slug/summary/tags/order…）原样保留，正文一字不改。
     */
    String mdWithFrontMatter(Doc doc, List<CatalogNode> chain) {
        ParsedDoc parsed = FrontMatterParser.parse(doc.getContentMd());
        List<String> names = chain.stream().map(CatalogNode::getName).toList();
        List<String> slugs = chain.stream().map(CatalogNode::getSlug).toList();

        LinkedHashMap<String, Object> fm = new LinkedHashMap<>();
        fm.put("path", names);
        fm.put("slugs", slugs);
        if (parsed.isHasFrontMatter()) {
            // 旧 category/topic(+_slug)/path 由新 path/slugs 取代，其余键保留
            for (Map.Entry<String, Object> e : parsed.getMeta().entrySet()) {
                String k = e.getKey();
                if (k.equals("path") || k.equals("slugs") || k.equals("category")
                        || k.equals("category_slug") || k.equals("topic") || k.equals("topic_slug")) {
                    continue;
                }
                fm.put(k, e.getValue());
            }
        } else {
            fm.put("title", doc.getTitle());
            fm.put("slug", doc.getSlug());
            if (doc.getSummary() != null && !doc.getSummary().isBlank()) {
                fm.put("summary", doc.getSummary());
            }
            if (doc.getTags() != null && !doc.getTags().isBlank()) {
                fm.put("tags", List.of(doc.getTags().split(",")));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        for (Map.Entry<String, Object> e : fm.entrySet()) {
            sb.append(yamlLine(e.getKey(), e.getValue()));
        }
        sb.append("---\n\n");
        // 有 front-matter 时 body 已剥离 fm；无 fm 时 body 就是全文
        return sb + parsed.getBody();
    }

    // ---------- YAML 序列化（配合 MiniYaml 的扁平键值 + [a, b] 行内数组） ----------

    private String yamlLine(String key, Object value) {
        String k = SAFE_KEY.matcher(key).matches() ? key : jsonQuote(key);
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return k + ": []\n";
            }
            StringBuilder sb = new StringBuilder(k).append(": [");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(yamlScalar(list.get(i)));
            }
            return sb.append("]\n").toString();
        }
        return k + ": " + yamlScalar(value) + "\n";
    }

    private String yamlScalar(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Number) {
            return v.toString();
        }
        return jsonQuote(String.valueOf(v));
    }

    private String jsonQuote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ---------- 目录结构辅助 ----------

    private int countSubtreeDirs(CatalogNode node) {
        int count = 0;
        for (CatalogNode child : catalogMapper.selectByParent(node.getOwnerId(), node.getId())) {
            count += 1 + countSubtreeDirs(child);
        }
        return count;
    }

    private Map<String, Object> nodeMeta(CatalogNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", node.getName());
        m.put("slug", node.getSlug());
        m.put("remark", node.getRemark());
        m.put("sortOrder", node.getSortOrder());
        m.put("maxLevel", node.getMaxLevel());
        m.put("nodeLevel", node.getNodeLevel());
        List<Map<String, Object>> children = new ArrayList<>();
        List<CatalogNode> kids = catalogMapper.selectByParent(node.getOwnerId(), node.getId());
        for (CatalogNode child : kids) {
            children.add(nodeMeta(child));
        }
        m.put("children", children);
        return m;
    }

    /** 根→叶目录 完整链（任意深度） */
    private List<CatalogNode> pathFromRoot(Long nodeId) {
        LinkedList<CatalogNode> chain = new LinkedList<>();
        CatalogNode cur = nodeId == null ? null : catalogMapper.selectById(nodeId);
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur.getId())) {
            chain.addFirst(cur);
            if (cur.getParentId() == null || cur.getParentId() == 0) {
                break;
            }
            cur = catalogMapper.selectById(cur.getParentId());
        }
        return chain;
    }

    /**
     * 把正文里的 /uploads/ 相对引用解析为 uploadDir 内的绝对路径；不合法或越界返回 null。
     * 防任意文件读取：拒绝 ..、绝对路径、反斜杠、盘符，resolve+normalize 后必须仍在 uploadRoot 内。
     */
    private Path resolveWithinUpload(Path uploadRoot, String rel) {
        if (rel == null || rel.isEmpty() || rel.contains("..") || rel.contains("\\")
                || rel.startsWith("/") || rel.contains(":")) {
            return null;
        }
        Path p = uploadRoot.resolve(rel).normalize();
        return p.startsWith(uploadRoot) ? p : null;
    }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        writeEntry(zos, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }
}
