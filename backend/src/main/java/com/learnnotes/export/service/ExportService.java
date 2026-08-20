package com.learnnotes.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.SlugUtil;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 全量导出（R31–R32、D12）：人可读 md 文件树 + 见解旁挂 + 图片 + manifest.json。
 * 目录结构与 .insights.json 字段是恢复路径的输入格式，改动须回评审。
 */
@Service
public class ExportService {

    /** 正文里站内图片引用 */
    private static final Pattern IMAGE_REF = Pattern.compile("/uploads/[0-9a-zA-Z/._-]+");

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
    public void writeZip(OutputStream out) throws IOException {
        List<CatalogNode> categories = catalogMapper.selectByParent(0).stream()
                // INBOX 是 Flyway 种子的常驻兜底路径，不参与恢复计数
                .filter(c -> !"inbox".equals(c.getSlug()))
                .toList();
        Map<Long, List<CatalogNode>> childrenByCategory = new LinkedHashMap<>();
        for (CatalogNode c : categories) {
            childrenByCategory.put(c.getId(), catalogMapper.selectByParent(c.getId()).stream()
                    .filter(t -> !"uncategorized".equals(t.getSlug()))
                    .toList());
        }
        List<Doc> docs = docMapper.selectAll();

        // 图片引用集合
        Set<String> referencedImages = new HashSet<>();
        for (Doc doc : docs) {
            Matcher m = IMAGE_REF.matcher(doc.getContentMd());
            while (m.find()) {
                referencedImages.add(m.group().substring(1)); // 去掉前导 /
            }
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("exportedAt", LocalDateTime.now().toString());
        manifest.put("specVersion", "v1");
        manifest.put("zipName", zipFileName());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("categories", categories.size());
        int topics = 0;
        for (List<CatalogNode> list : childrenByCategory.values()) {
            topics += list.size();
        }
        counts.put("topics", topics);
        counts.put("docs", docs.size());
        int annotations = 0;
        for (Doc doc : docs) {
            annotations += annotationMapper.selectByDoc(doc.getId()).size();
        }
        counts.put("annotations", annotations);
        counts.put("images", referencedImages.size());
        manifest.put("counts", counts);

        List<Map<String, Object>> categoryMeta = new ArrayList<>();
        for (CatalogNode c : categories) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", c.getName());
            cm.put("slug", c.getSlug());
            cm.put("remark", c.getRemark());
            cm.put("sortOrder", c.getSortOrder());
            cm.put("topics", topicsOf(childrenByCategory.get(c.getId())));
            categoryMeta.add(cm);
        }
        manifest.put("categories", categoryMeta);

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // manifest.json
            writeEntry(zos, "manifest.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));

            // 文档 + 见解
            for (Doc doc : docs) {
                CatalogNode topic = catalogMapper.selectById(doc.getTopicId());
                if (topic == null) {
                    continue;
                }
                CatalogNode category = catalogMapper.selectById(topic.getParentId());
                String categorySlug = category == null ? "inbox" : category.getSlug();
                String topicSlug = topic.getSlug();

                String md = mdWithFrontMatter(doc, category, topic);
                writeEntry(zos, categorySlug + "/" + topicSlug + "/" + doc.getSlug() + ".md", md);

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
                writeEntry(zos, categorySlug + "/" + topicSlug + "/" + doc.getSlug() + ".insights.json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(annList));
            }

            // 图片（只打包被正文引用到的；孤儿图只报数）
            for (String rel : referencedImages) {
                Path src = Paths.get(props.getUploadDir()).resolve(rel);
                if (Files.exists(src)) {
                    writeEntry(zos, "uploads/" + rel, Files.readAllBytes(src));
                }
            }
        }
    }

    /** 导出 md：原文已带 front-matter 则原样；否则按当前分类信息补全生成（保证可被导入接口无歧义还原） */
    String mdWithFrontMatter(Doc doc, CatalogNode category, CatalogNode topic) {
        ParsedDoc parsed = FrontMatterParser.parse(doc.getContentMd());
        if (parsed.isHasFrontMatter()) {
            return doc.getContentMd();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        if (category != null) {
            sb.append("category: ").append(category.getName()).append('\n');
            sb.append("category_slug: ").append(category.getSlug()).append('\n');
        }
        if (topic != null) {
            sb.append("topic: ").append(topic.getName()).append('\n');
            sb.append("topic_slug: ").append(topic.getSlug()).append('\n');
        }
        sb.append("title: ").append(doc.getTitle()).append('\n');
        sb.append("slug: ").append(doc.getSlug()).append('\n');
        if (doc.getSummary() != null && !doc.getSummary().isBlank()) {
            sb.append("summary: ").append(doc.getSummary()).append('\n');
        }
        if (doc.getTags() != null && !doc.getTags().isBlank()) {
            sb.append("tags: [").append(doc.getTags()).append("]\n");
        }
        sb.append("---\n\n");
        return sb + doc.getContentMd();
    }

    private List<Map<String, Object>> topicsOf(List<CatalogNode> topics) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (topics == null) {
            return list;
        }
        for (CatalogNode t : topics) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("name", t.getName());
            tm.put("slug", t.getSlug());
            tm.put("remark", t.getRemark());
            tm.put("sortOrder", t.getSortOrder());
            list.add(tm);
        }
        return list;
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
