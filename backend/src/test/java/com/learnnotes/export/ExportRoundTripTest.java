package com.learnnotes.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnnotes.annotation.dto.AnnotationDto;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.annotation.service.AnnotationService;
import com.learnnotes.catalog.dto.CatalogNodeDto;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.config.AppProperties;
import com.learnnotes.doc.dto.DocDetailDto;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.service.DocService;
import com.learnnotes.export.service.ExportService;
import com.learnnotes.export.service.InsightImportService;
import com.learnnotes.imports.service.ImportService;
import com.learnnotes.markdown.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导出/回灌往返测试（计划卡 T19 核心价值）：
 * 造数据 → 导出 zip → 清空全部业务表 → 用导入接口 + 见解回灌还原 → 断言计数与内容完全一致。
 * 需要本地 MySQL（application.yml 默认 127.0.0.1:3306；测试环境用环境变量指向 3307）。
 */
@SpringBootTest
class ExportRoundTripTest {

    @Autowired
    CatalogService catalogService;
    @Autowired
    DocService docService;
    @Autowired
    AnnotationService annotationService;
    @Autowired
    ImportService importService;
    @Autowired
    InsightImportService insightImportService;
    @Autowired
    ExportService exportService;
    @Autowired
    DocAnnotationMapper annotationMapper;
    @Autowired
    DocMapper docMapper;
    @Autowired
    AppProperties props;

    @BeforeEach
    void clean() {
        // 清空业务表，保留 Flyway 种子（INBOX/未归类）
        annotationMapper.deleteAll();
        docMapper.deleteAllVersions(); docMapper.deleteAllDocs();
        catalogMapperDeleteExceptSeed();
    }

    private void catalogMapperDeleteExceptSeed() {
        // 通过 service 删除需要走 409 校验，直接操作数据源更直接
        org.springframework.jdbc.core.JdbcTemplate jdbc = jdbc();
        jdbc.update("DELETE FROM catalog_node WHERE slug NOT IN ('inbox','uncategorized')");
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:mysql://127.0.0.1:3307/learn_notes?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                        "root", ""));
    }

    @Test
    void roundTrip() throws Exception {
        // ============ 1. 造数据：2 大类 / 3 小方向 / 4 文档 / 6 见解（含 1 ORPHAN） ============
        CatalogNodeDto javaCat = catalogService.create(0L, "Java", "java", "后端主语言", null, 10);
        CatalogNodeDto func = catalogService.create(javaCat.getId(), "函数", "function", null, null, null);
        CatalogNodeDto cls = catalogService.create(javaCat.getId(), "类", "class", "内部类相关", null, null);
        CatalogNodeDto vue = catalogService.create(0L, "Vue", "vue", "前端框架", null, 20);
        CatalogNodeDto comp = catalogService.create(vue.getId(), "组件", "component", null, null, null);

        // 文档 1：含图片引用与多块
        String md1 = "# Lambda 基础\n\n第一段讲 Lambda 表达式。\n\n```java\nlist.forEach(x -> System.out.println(x));\n```\n\n![示意](/uploads/2026/08/abcdef1234567890.png)\n";
        var d1 = docService.create(func.getId(), "Lambda 基础", "lambda-basics", "Lambda 摘要",
                List.of("基础", "lambda"), md1, "lambda-basics.md");
        // 造一张被引用的图片文件，验证导出时被打包
        Path imgDir = Paths.get(props.getUploadDir()).resolve("2026/08");
        Files.createDirectories(imgDir);
        Files.write(imgDir.resolve("abcdef1234567890.png"), new byte[]{1, 2, 3, 4});
        String md2 = "# 内部类\n\n内部类分四种。\n";
        var d2 = docService.create(cls.getId(), "内部类", "inner-class", null, null, md2, "inner-class.md");
        String md3 = "# props 基础\n\n组件属性传参。\n";
        var d3 = docService.create(comp.getId(), "props 基础", "props-basics", null, null, md3, "props-basics.md");
        String md4 = "# B+ 树\n\n索引结构。\n";
        var d4 = docService.create(cls.getId(), "B+ 树", "b-plus-tree", null, null, md4, "b-plus-tree.md");

        // 见解：d1 上 4 条（3 ACTIVE + 1 手工 ORPHAN），d2 上 2 条 → 共 6 条
        List<Block> blocks1 = docService.detail(d1.getId()).getBlocks();
        var ann1 = annotationService.create(d1.getId(), blocks1.get(1).getAnchor(), "Lambda 的闭包语义要注意");
        var ann2 = annotationService.create(d1.getId(), blocks1.get(2).getAnchor(), "这里的 forEach 是函数式风格");
        var ann3 = annotationService.create(d1.getId(), blocks1.get(0).getAnchor(), "标题值得强调");
        List<Block> blocks2 = docService.detail(d2.getId()).getBlocks();
        var ann4 = annotationService.create(d2.getId(), blocks2.get(0).getAnchor(), "内部类记忆口诀");
        var ann5 = annotationService.create(d2.getId(), blocks2.get(0).getAnchor(), "匿名内部类特别注意");

        // 手工造一条 ORPHAN（直接插库）
        DocAnnotation orphan = new DocAnnotation();
        orphan.setDocId(d1.getId());
        orphan.setAnchorHash("deadbeef");
        orphan.setAnchorIndex(99);
        orphan.setBlockSnippet("已不存在的旧块内容");
        orphan.setContentMd("这条见解已经游离");
        orphan.setStatus(DocAnnotation.STATUS_ORPHAN);
        orphan.setDocVersionAtCreate(1);
        annotationMapper.insert(orphan);
        // d1 现在有 4 条（3 ACTIVE + 1 ORPHAN），d2 有 2 条 → 共 6 条

        // ============ 2. 导出 zip ============
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.writeZip(baos);
        byte[] zipBytes = baos.toByteArray();

        // 校验 zip 结构与 manifest
        Map<String, byte[]> entries = unzipAll(zipBytes);
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("java/function/lambda-basics.md"));
        assertTrue(entries.containsKey("java/function/lambda-basics.insights.json"));
        assertTrue(entries.containsKey("java/class/inner-class.insights.json"));
        assertTrue(entries.containsKey("vue/component/props-basics.md"));
        assertTrue(entries.containsKey("uploads/2026/08/abcdef1234567890.png"),
                "被引用的图片必须打进导出包");

        ObjectMapper om = new ObjectMapper();
        Map<?, ?> manifest = om.readValue(entries.get("manifest.json"), Map.class);
        Map<?, ?> counts = (Map<?, ?>) manifest.get("counts");
        assertEquals(2, ((Number) counts.get("categories")).intValue());
        assertEquals(3, ((Number) counts.get("topics")).intValue());
        assertEquals(4, ((Number) counts.get("docs")).intValue());
        assertEquals(6, ((Number) counts.get("annotations")).intValue());
        assertEquals(1, ((Number) counts.get("images")).intValue());

        // ============ 3. 清空业务表 ============
        annotationMapper.deleteAll();
        docMapper.deleteAllVersions(); docMapper.deleteAllDocs();
        catalogMapperDeleteExceptSeed();

        // ============ 4. 用导出物还原 ============
        // 4.1 先按 manifest 重建分类与 remark
        List<Map<String, Object>> categories = (List<Map<String, Object>>) manifest.get("categories");
        for (Map<String, Object> c : categories) {
            CatalogNodeDto created = catalogService.create(0L, (String) c.get("name"), (String) c.get("slug"),
                    null, null, (Integer) c.get("sortOrder"));
            List<Map<String, Object>> topics = (List<Map<String, Object>>) c.get("topics");
            for (Map<String, Object> t : topics) {
                catalogService.create(created.getId(), (String) t.get("name"), (String) t.get("slug"),
                        null, null, (Integer) t.get("sortOrder"));
            }
            // 恢复 remark
            catalogService.update(created.getId(), null, (String) c.get("remark"), null, null);
        }

        // 4.2 逐篇导入文档（从导出的 md）
        for (String key : List.of("java/function/lambda-basics.md", "java/class/inner-class.md",
                "vue/component/props-basics.md", "java/class/b-plus-tree.md")) {
            String md = new String(entries.get(key), StandardCharsets.UTF_8);
            var result = importService.importDoc(key.substring(key.lastIndexOf('/') + 1), md, null, null,
                    ImportService.ON_CONFLICT_NEW_VERSION);
            assertFalse(result.getError() != null && !result.getError().isEmpty());
        }

        // 4.3 回灌见解
        String[] insightsFiles = {"java/function/lambda-basics.insights.json", "java/class/inner-class.insights.json"};
        for (String key : insightsFiles) {
            List<Map<String, Object>> insights = om.readValue(entries.get(key),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
            String[] parts = key.split("/");
            Map<String, Object> r = insightImportService.importInsights(parts[0], parts[1],
                    parts[2].replace(".insights.json", ""), insights);
            if (key.contains("lambda-basics")) {
                assertEquals(3, (int) r.get("created"), "3 条 ACTIVE 原位重建");
                assertEquals(1, (int) r.get("orphan"), "1 条 ORPHAN 保持游离不丢失");
            } else {
                assertEquals(2, (int) r.get("created"));
            }
        }

        // ============ 5. 断言还原结果 ============
        List<CatalogNodeDto> tree = catalogService.tree();
        List<CatalogNodeDto> userCategories = tree.stream().filter(t -> !t.getSlug().equals("inbox")).toList();
        assertEquals(2, userCategories.size(), "大类数一致");
        assertEquals(3, userCategories.stream().mapToInt(c -> c.getChildren().size()).sum(), "小方向数一致");
        // remark 已恢复
        CatalogNodeDto javaAfter = userCategories.stream().filter(t -> t.getSlug().equals("java")).findFirst().orElseThrow();
        assertEquals("后端主语言", javaAfter.getRemark());

        // 文档与见解计数
        assertEquals(4, countDocs());
        assertEquals(6, countAnnotations());
        // 每篇文档的见解 anchor / contentMd 一致
        DocDetailDto d1After = docService.detail(docIdBySlug("lambda-basics"));
        List<AnnotationDto> anns = d1After.getAnnotations().stream()
                .map(o -> (AnnotationDto) o).toList();
        assertEquals(4, anns.size());
        assertTrue(anns.stream().anyMatch(a -> a.getContentMd().equals("Lambda 的闭包语义要注意")
                && a.getStatus().equals("ACTIVE")));
        assertTrue(anns.stream().anyMatch(a -> a.getStatus().equals("ORPHAN")
                && a.getContentMd().equals("这条见解已经游离")));
        DocDetailDto d2After = docService.detail(docIdBySlug("inner-class"));
        List<AnnotationDto> anns2 = d2After.getAnnotations().stream()
                .map(o -> (AnnotationDto) o).toList();
        assertEquals(2, anns2.size());
        assertTrue(anns2.stream().anyMatch(a -> a.getContentMd().equals("内部类记忆口诀")));

        // 幂等：再回灌一遍，全部 skipped
        Map<String, Object> r2 = insightImportService.importInsights("java", "function", "lambda-basics",
                om.readValue(entries.get("java/function/lambda-basics.insights.json"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                        }));
        assertEquals(4, (int) r2.get("skipped"));
    }

    private Long docIdBySlug(String slug) {
        return docMapper.selectAll().stream().filter(d -> d.getSlug().equals(slug)).findFirst()
                .orElseThrow().getId();
    }

    private long countDocs() {
        return docMapper.selectAll().size();
    }

    private long countAnnotations() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM doc_annotation", Long.class);
    }

    private Map<String, byte[]> unzipAll(byte[] zip) throws Exception {
        Map<String, byte[]> map = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                map.put(entry.getName(), bos.toByteArray());
            }
        }
        return map;
    }
}
