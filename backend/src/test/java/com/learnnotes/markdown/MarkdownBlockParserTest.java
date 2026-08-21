package com.learnnotes.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 块切分测试 —— 覆盖计划卡 T05 的全部必测用例。
 */
class MarkdownBlockParserTest {

    @Test
    void pureChineseParagraph() {
        String md = "这是一段纯中文的正文，用于验证锚点算法对中文的处理。";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(1, blocks.size());
        assertEquals("paragraph", blocks.get(0).getType());
        assertEquals("b0-", blocks.get(0).getAnchor().substring(0, 3));
        assertEquals(8, blocks.get(0).getAnchor().split("-")[1].length());
        // 锚点只依赖内容与序号，稳定可复现
        List<Block> again = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(again.get(0).getAnchor(), blocks.get(0).getAnchor());
    }

    @Test
    void chineseInsideCodeBlock() {
        String md = "```java\n// 注释：中文\nString s = \"你好\";\n```";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(1, blocks.size());
        assertEquals("code", blocks.get(0).getType());
        assertEquals("java", blocks.get(0).getLang());
        assertTrue(blocks.get(0).getRaw().startsWith("```"));
    }

    @Test
    void codeBlockPreservesIndentation() {
        String md = "```java\npublic int add(int a, int b) {\n    return a + b;\n}\n```";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(1, blocks.size());
        String raw = blocks.get(0).getRaw();
        assertTrue(raw.contains("    return a + b;"), "缩进必须保留：" + raw);
        // 归一化后缩进仍保留（与正文块不同）
        String norm = AnchorUtil.normalize(raw, true);
        assertTrue(norm.contains("    return a + b;"), "代码块归一化不得折叠缩进：" + norm);
        // 若按正文折叠则会破坏
        String collapsed = AnchorUtil.normalize(raw, false);
        assertFalse(collapsed.contains("    return a + b;"), "代码块不允许按正文规则折叠");
    }

    /**
     * 回归：raw 必须保留换行（历史 bug：span 逐段拼接丢换行，代码块/列表/表格挤成一行）。
     */
    @Test
    void rawPreservesNewlines() {
        String md = "# 标题\n\n第一段。\n\n```java\nint a;\n\nint b;\n```\n\n- 甲\n- 乙\n\n| 列A | 列B |\n|---|---|\n| 1 | 2 |";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();

        Block code = blocks.stream().filter(b -> "code".equals(b.getType())).findFirst().orElseThrow();
        assertEquals("```java\nint a;\n\nint b;\n```", code.getRaw(), "代码块 raw 应逐字保留（含空行）");

        Block list = blocks.stream().filter(b -> "list".equals(b.getType())).findFirst().orElseThrow();
        assertEquals("- 甲\n- 乙", list.getRaw(), "列表 raw 应保留换行");

        Block table = blocks.stream().filter(b -> "table".equals(b.getType())).findFirst().orElseThrow();
        assertEquals("| 列A | 列B |\n|---|---|\n| 1 | 2 |", table.getRaw(), "表格 raw 应保留换行");
    }

    @Test
    void twoIdenticalParagraphsSameHashDifferentAnchor() {
        String md = "相同段落内容\n\n相同段落内容\n";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(2, blocks.size());
        String h0 = blocks.get(0).getAnchor().split("-")[1];
        String h1 = blocks.get(1).getAnchor().split("-")[1];
        assertEquals(h0, h1, "相同内容 hash8 相同");
        assertNotEquals(blocks.get(0).getAnchor(), blocks.get(1).getAnchor(), "anchor 因序号不同而不同");
        assertEquals("b0-" + h0, blocks.get(0).getAnchor());
        assertEquals("b1-" + h0, blocks.get(1).getAnchor());
    }

    @Test
    void gfmTableIsSingleBlock() {
        String md = "| 列A | 列B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(1, blocks.size());
        assertEquals("table", blocks.get(0).getType());
    }

    @Test
    void orderedAndUnorderedList() {
        String md = "- 甲\n- 乙\n\n1. 一\n2. 二";
        List<Block> blocks = MarkdownBlockParser.parseBody(md).getBlocks();
        assertEquals(2, blocks.size());
        assertEquals("list", blocks.get(0).getType());
        assertEquals("list", blocks.get(1).getType());
        assertTrue(blocks.get(0).getRaw().startsWith("- 甲"));
        assertTrue(blocks.get(1).getRaw().startsWith("1. 一"));
    }

    @Test
    void withAndWithoutFrontMatter() {
        String withFm = "---\ncategory: Java\ntopic: 函数\ntitle: 标题\n---\n\n# 标题\n\n正文";
        String withoutFm = "# 标题\n\n正文";
        List<Block> b1 = MarkdownBlockParser.parse(withFm).getBlocks();
        List<Block> b2 = MarkdownBlockParser.parse(withoutFm).getBlocks();
        assertEquals(b2.size(), b1.size());
        // front-matter 不产生块
        assertEquals("heading", b1.get(0).getType());
        assertEquals(b1.get(0).getAnchor(), b2.get(0).getAnchor());
        // 元数据解析
        ParsedDoc doc = FrontMatterParser.parse(withFm);
        assertTrue(doc.isHasFrontMatter());
        assertEquals("Java", doc.getMeta().get("category"));
        assertEquals("函数", doc.getMeta().get("topic"));
    }

    @Test
    void crlfInputSameBlocksAsLf() {
        String lf = "# 标题\n\n正文一段\n\n```java\nint a;\n```\n";
        String crlf = lf.replace("\n", "\r\n");
        List<Block> b1 = MarkdownBlockParser.parseBody(lf).getBlocks();
        List<Block> b2 = MarkdownBlockParser.parseBody(crlf).getBlocks();
        assertEquals(b1.size(), b2.size());
        for (int i = 0; i < b1.size(); i++) {
            assertEquals(b1.get(i).getAnchor(), b2.get(i).getAnchor(), "CRLF 与 LF 下第 " + i + " 块锚点一致");
        }
    }

    @Test
    void emptyDocument() {
        List<Block> blocks = MarkdownBlockParser.parseBody("").getBlocks();
        assertTrue(blocks.isEmpty());
        blocks = MarkdownBlockParser.parseBody("   \n\n  ").getBlocks();
        assertTrue(blocks.isEmpty());
    }

    @Test
    void singleH1Document() {
        List<Block> blocks = MarkdownBlockParser.parseBody("# 只有一个标题").getBlocks();
        assertEquals(1, blocks.size());
        assertEquals("heading", blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getLevel());
    }

    @Test
    void headingLevels() {
        List<Block> blocks = MarkdownBlockParser.parseBody("## 二级\n\n### 三级").getBlocks();
        assertEquals(2, blocks.size());
        assertEquals(2, blocks.get(0).getLevel());
        assertEquals(3, blocks.get(1).getLevel());
    }

    @Test
    void quoteAndThematicBreak() {
        List<Block> blocks = MarkdownBlockParser.parseBody("> 引用内容\n\n---").getBlocks();
        assertEquals(2, blocks.size());
        assertEquals("quote", blocks.get(0).getType());
        assertEquals("thematic_break", blocks.get(1).getType());
    }

    @Test
    void codeWithoutLanguageGetsWarningAndTextLang() {
        MarkdownBlockParser.ParseResult r = MarkdownBlockParser.parseBody("```\nint a = 1;\n```");
        Block code = r.getBlocks().get(0);
        assertEquals("code", code.getType());
        assertEquals("text", code.getLang());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("缺少语言标签")));
    }

    @Test
    void indentedCodeBlockWarning() {
        MarkdownBlockParser.ParseResult r = MarkdownBlockParser.parseBody("    缩进代码");
        Block code = r.getBlocks().get(0);
        assertEquals("code", code.getType());
        assertEquals("text", code.getLang());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("缩进式代码块")));
    }

    @Test
    void referenceLinkAndFootnoteWarnings() {
        String md = "看这里 [ref] 的说明\n\n[ref]: https://example.com\n\n脚注[^1]内容\n\n[^1]: 注释";
        MarkdownBlockParser.ParseResult r = MarkdownBlockParser.parseBody(md);
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("引用式链接")));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("脚注")));
    }

    @Test
    void htmlBlockWarning() {
        MarkdownBlockParser.ParseResult r = MarkdownBlockParser.parseBody("正文\n\n<div>盒子</div>");
        assertTrue(r.getBlocks().stream().anyMatch(b -> "html".equals(b.getType())));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("HTML")));
    }

    @Test
    void frontMatterTagsArray() {
        String md = "---\ncategory: Java\ntopic: 函数\ntags: [基础, lambda]\n---\n\n正文";
        ParsedDoc doc = FrontMatterParser.parse(md);
        assertEquals(List.of("基础", "lambda"), doc.getMeta().get("tags"));
    }

    @Test
    void frontMatterTagsListForm() {
        String md = "---\ncategory: Vue\ntags:\n  - 组件\n  - props\n---\n\n正文";
        ParsedDoc doc = FrontMatterParser.parse(md);
        assertEquals(List.of("组件", "props"), doc.getMeta().get("tags"));
    }

    @Test
    void slugChineseMappingViaFrontMatter() {
        String md = "---\ncategory: Java\ncategory_slug: java\ntopic: 函数\ntopic_slug: function\ntitle: Lambda 基础\nslug: lambda-basics\n---\n\n# Lambda 基础\n\n正文";
        ParsedDoc doc = FrontMatterParser.parse(md);
        Map<String, Object> meta = doc.getMeta();
        assertEquals("java", meta.get("category_slug"));
        assertEquals("function", meta.get("topic_slug"));
        assertEquals("lambda-basics", meta.get("slug"));
        assertEquals("Lambda 基础", meta.get("title"));
    }

    @Test
    void rawSliceMatchesBlocksTypeSequence() {
        String md = "# 标题\n\n第一段。\n\n```java\nSystem.out.println(1);\n```\n\n- a\n- b\n\n> 引用";
        List<String> types = MarkdownBlockParser.parseBody(md).getBlocks().stream()
                .map(Block::getType)
                .collect(Collectors.toList());
        assertEquals(List.of("heading", "paragraph", "code", "list", "quote"), types);
    }
}
