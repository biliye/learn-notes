package com.learnnotes.annotation;

import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锚点重挂测试 —— 覆盖计划卡 T09 的 7 个必测用例。
 */
class ReanchorServiceTest {

    private static final String DOC = "# Java 方法\n\n" +
            "第一段：方法的定义。\n\n" +
            "第二段：参数与返回值。\n\n" +
            "```java\nint add(int a, int b) { return a + b; }\n```\n\n" +
            "第三段：调用示例。";

    private static List<Block> parse(String md) {
        return MarkdownBlockParser.parseBody(md).getBlocks();
    }

    private static String hash8(String anchor) {
        return anchor.substring(anchor.indexOf('-') + 1);
    }

    /** 改动一个中间段落：其他块见解全部 ACTIVE 且 index 正确 */
    @Test
    void changeMiddleParagraphKeepsOthersActive() {
        List<Block> oldBlocks = parse(DOC);
        // 见解挂在第 0 块（heading）和第 2 块（第二段）
        String h0 = hash8(oldBlocks.get(0).getAnchor());
        String h2 = hash8(oldBlocks.get(2).getAnchor());

        String modified = DOC.replace("第二段：参数与返回值。", "第二段：参数与返回值的详细说明。");
        List<Block> newBlocks = parse(modified);

        var m0 = ReanchorService.findMatch(h0, 0, oldBlocks, newBlocks);
        assertEquals(0, m0.getIndex());
        assertEquals(ReanchorService.BlockStatus.ACTIVE, m0.getStatus());

        var m2 = ReanchorService.findMatch(h2, 2, oldBlocks, newBlocks);
        assertEquals(ReanchorService.BlockStatus.ORPHAN, m2.getStatus(), "被改动的块无法靠 hash 命中");

        // 未改动块（code）仍在原位
        String hCode = hash8(oldBlocks.get(3).getAnchor());
        var mCode = ReanchorService.findMatch(hCode, 3, oldBlocks, newBlocks);
        assertEquals(3, mCode.getIndex());
        assertEquals(ReanchorService.BlockStatus.ACTIVE, mCode.getStatus());
    }

    /** 在文档开头插入一段：全部见解 index +1 且仍 ACTIVE */
    @Test
    void insertAtTopShiftsAllIndexes() {
        List<Block> oldBlocks = parse(DOC);
        String h0 = hash8(oldBlocks.get(0).getAnchor());
        String h2 = hash8(oldBlocks.get(2).getAnchor());

        String modified = "新插入的引言段落。\n\n" + DOC;
        List<Block> newBlocks = parse(modified);

        var m0 = ReanchorService.findMatch(h0, 0, oldBlocks, newBlocks);
        assertEquals(1, m0.getIndex());
        assertEquals(ReanchorService.BlockStatus.ACTIVE, m0.getStatus());

        var m2 = ReanchorService.findMatch(h2, 2, oldBlocks, newBlocks);
        assertEquals(3, m2.getIndex());
        assertEquals(ReanchorService.BlockStatus.ACTIVE, m2.getStatus());
    }

    /** 小改被批注的那段 → STALE（hash 失配但 trigram 相似度 ≥ 0.6） */
    @Test
    void minorEditToAnnotatedBlockBecomesStale() {
        List<Block> oldBlocks = parse(DOC);
        String h2 = hash8(oldBlocks.get(2).getAnchor());

        // 只加 3 个字的轻微改动：Jaccard ≈ 9/13 ≈ 0.69 ≥ 0.6 → STALE
        String modified = DOC.replace("第二段：参数与返回值。", "第二段：参数与返回值的说明。");
        List<Block> newBlocks = parse(modified);

        var m2 = ReanchorService.findMatch(h2, 2, oldBlocks, newBlocks);
        assertEquals(ReanchorService.BlockStatus.STALE, m2.getStatus());
        assertEquals(2, m2.getIndex());
    }

    /** 整段替换被批注的段落 → ORPHAN */
    @Test
    void fullReplacementBecomesOrphan() {
        List<Block> oldBlocks = parse(DOC);
        String h2 = hash8(oldBlocks.get(2).getAnchor());

        String modified = DOC.replace("第二段：参数与返回值。", "完全不同的新段落内容，不再讨论参数。");
        List<Block> newBlocks = parse(modified);

        var m2 = ReanchorService.findMatch(h2, 2, oldBlocks, newBlocks);
        assertEquals(ReanchorService.BlockStatus.ORPHAN, m2.getStatus());
        assertEquals(-1, m2.getIndex());
    }

    /** 删除被批注的段落 → ORPHAN（绝不静默丢弃） */
    @Test
    void deletedBlockBecomesOrphanNotDropped() {
        List<Block> oldBlocks = parse(DOC);
        String h2 = hash8(oldBlocks.get(2).getAnchor());

        String modified = DOC.replace("第二段：参数与返回值。\n\n", "");
        List<Block> newBlocks = parse(modified);

        var m2 = ReanchorService.findMatch(h2, 2, oldBlocks, newBlocks);
        assertEquals(ReanchorService.BlockStatus.ORPHAN, m2.getStatus());
    }

    /** 文档内有两个相同段落时的最近匹配 */
    @Test
    void duplicateParagraphsPickNearest() {
        String doc = "# 标题\n\n重复段落。\n\n中间内容。\n\n重复段落。\n";
        List<Block> oldBlocks = parse(doc);
        String h = hash8(oldBlocks.get(1).getAnchor()); // 第一个"重复段落"
        assertEquals(hash8(oldBlocks.get(3).getAnchor()), h, "两个重复段落 hash 相同");

        List<Block> newBlocks = parse(doc);
        var m = ReanchorService.findMatch(h, 1, oldBlocks, newBlocks);
        assertEquals(1, m.getIndex(), "应取与原 index 距离最近者");
        assertEquals(ReanchorService.BlockStatus.ACTIVE, m.getStatus());
    }

    /** 代码块缩进改动导致 hash 变化 → STALE 或 ORPHAN，绝不静默丢弃 */
    @Test
    void codeIndentChangeNeverDrops() {
        String oldDoc = "说明\n\n```java\npublic int add(int a, int b) {\n    return a + b;\n}\n```\n";
        List<Block> oldBlocks = parse(oldDoc);
        String hCode = hash8(oldBlocks.get(1).getAnchor());

        // 缩进从 4 空格改 2 空格 → 代码块 hash 必然变化
        String newDoc = "说明\n\n```java\npublic int add(int a, int b) {\n  return a + b;\n}\n```\n";
        List<Block> newBlocks = parse(newDoc);

        var m = ReanchorService.findMatch(hCode, 1, oldBlocks, newBlocks);
        assertNotEquals(ReanchorService.BlockStatus.ACTIVE, m.getStatus(),
                "代码块内容变化必须被标记，不允许伪装成 ACTIVE");
        assertTrue(m.getStatus() == ReanchorService.BlockStatus.STALE
                        || m.getStatus() == ReanchorService.BlockStatus.ORPHAN,
                "结果必须是 STALE 或 ORPHAN，绝不能静默丢弃");
    }

    @Test
    void trigramJaccardBasics() {
        assertEquals(1.0, ReanchorService.trigramJaccard("完全相同的一段文字", "完全相同的一段文字"));
        // 只差一个字的六字串：共享 3/4 个 trigram → Jaccard = 3/5 = 0.6
        assertEquals(0.6, ReanchorService.trigramJaccard("甲乙丙丁戊己", "甲乙丙丁戊庚"));
        assertEquals(0.0, ReanchorService.trigramJaccard("abc", "xyz"));
        // 长度 < 3 退化为相等比较
        assertEquals(1.0, ReanchorService.trigramJaccard("ab", "ab"));
        assertEquals(0.0, ReanchorService.trigramJaccard("ab", "cd"));
    }
}
