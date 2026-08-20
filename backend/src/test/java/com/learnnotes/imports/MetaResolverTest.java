package com.learnnotes.imports;

import com.learnnotes.markdown.FrontMatterParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导入元数据解析测试 —— 覆盖计划卡 T08 的 8 个用例。
 */
class MetaResolverTest {

    private static MetaResolver.ResolvedMeta resolve(String filename, String content, String hintCat, String hintTopic) {
        return MetaResolver.resolve(filename, content, FrontMatterParser.parse(content).getMeta(), hintCat, hintTopic);
    }

    /** 1. 完整 front-matter */
    @Test
    void completeFrontMatter() {
        String content = "---\ncategory: Java\ncategory_slug: java\ntopic: 函数\ntopic_slug: function\ntitle: Lambda 基础\nslug: lambda-basics\n---\n\n# Lambda 基础\n\n正文";
        var meta = resolve("anything.md", content, null, null);
        assertEquals(MetaResolver.Source.FRONT_MATTER, meta.getSource());
        assertEquals("Java", meta.getCategoryName());
        assertEquals("java", meta.getCategorySlug());
        assertEquals("函数", meta.getTopicName());
        assertEquals("function", meta.getTopicSlug());
        assertEquals("lambda-basics", meta.getDocSlug());
        assertEquals("Lambda 基础", meta.getTitle());
    }

    /** 2. front-matter 只有 category/topic 没给 slug（中文名匹配走 name，slug 由后端生成） */
    @Test
    void frontMatterWithoutSlugs() {
        String content = "---\ncategory: Java\ntopic: 函数\ntitle: 方法基础\n---\n\n# 方法基础\n\n正文";
        var meta = resolve("x.md", content, null, null);
        assertEquals(MetaResolver.Source.FRONT_MATTER, meta.getSource());
        assertEquals("Java", meta.getCategoryName());
        assertNull(meta.getCategorySlug());
        assertEquals("函数", meta.getTopicName());
        assertNull(meta.getTopicSlug());
        assertEquals("方法基础", meta.getTitle());
        assertNull(meta.getDocSlug());
    }

    /** 3. 无 front-matter 但文件名三段 */
    @Test
    void filenameThreeSegments() {
        var meta = resolve("java__函数__lambda-basics.md", "# 正文", null, null);
        assertEquals(MetaResolver.Source.FILENAME, meta.getSource());
        assertEquals("java", meta.getCategoryName());
        assertEquals("函数", meta.getTopicName());
        assertEquals("lambda-basics", meta.getDocSlug());
    }

    /** 4. 文件名两段：小方向取"未归类" */
    @Test
    void filenameTwoSegments() {
        var meta = resolve("java__方法基础.md", "# 正文", null, null);
        assertEquals(MetaResolver.Source.FILENAME, meta.getSource());
        assertEquals("java", meta.getCategoryName());
        assertEquals("未归类", meta.getTopicName());
        assertEquals("uncategorized", meta.getTopicSlug());
        assertEquals("方法基础", meta.getDocSlug());
    }

    /** 5. 文件名一段：进 INBOX */
    @Test
    void filenameOneSegmentGoesInbox() {
        var meta = resolve("随手记.md", "# 随手记", null, null);
        assertEquals(MetaResolver.Source.INBOX, meta.getSource());
        assertEquals("INBOX", meta.getCategoryName());
        assertEquals("inbox", meta.getCategorySlug());
        assertEquals("未归类", meta.getTopicName());
    }

    /** 6. hint 覆盖 front-matter */
    @Test
    void hintOverridesFrontMatter() {
        String content = "---\ncategory: Java\ntopic: 函数\ntitle: Lambda 基础\nslug: lambda-basics\n---\n\n正文";
        var meta = resolve("x.md", content, "Vue", "组件");
        assertEquals(MetaResolver.Source.HINT, meta.getSource());
        assertEquals("Vue", meta.getCategoryName());
        assertEquals("组件", meta.getTopicName());
        // 标题与 slug 仍来自 front-matter
        assertEquals("lambda-basics", meta.getDocSlug());
        assertEquals("Lambda 基础", meta.getTitle());
    }

    /** 7. 无任何元数据 → INBOX（title/slug 由正文或文件名兜底） */
    @Test
    void noMetaGoesInbox() {
        var meta = resolve("随手记.md", "# 随手记\n\n随便写点什么", null, null);
        assertEquals(MetaResolver.Source.INBOX, meta.getSource());
    }

    /** 8. slug 含 ../ 必须被后续校验拦截（此处验证解析结果原样保留，由 ImportService 校验） */
    @Test
    void dangerousSlugDetectedByImportLayer() {
        String content = "---\ncategory: Java\ntopic: 函数\nslug: ../../etc/passwd\n---\n\n正文";
        var meta = resolve("x.md", content, null, null);
        assertEquals("../../etc/passwd", meta.getDocSlug());
        // 实际校验在 DocStorage/ImportService，这里确认 MetaResolver 不吞掉它
        assertTrue(meta.getDocSlug().contains(".."));
    }

    /** 文件名 .markdown 扩展名可解析 */
    @Test
    void markdownExtension() {
        var meta = resolve("vue__组件__props-basics.markdown", "# 正文", null, null);
        assertEquals(MetaResolver.Source.FILENAME, meta.getSource());
        assertEquals("props-basics", meta.getDocSlug());
    }

    /** tags 数组解析 */
    @Test
    void tagsParsed() {
        String content = "---\ncategory: Java\ntopic: 函数\ntags: [基础, lambda]\n---\n\n正文";
        var meta = resolve("x.md", content, null, null);
        assertEquals(java.util.List.of("基础", "lambda"), meta.getTags());
    }
}
