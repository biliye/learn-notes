package com.learnnotes.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锚点算法测试 —— 测试即契约（计划卡 T05：CRLF 与 LF 输入下 hash8 必须一致）。
 */
class AnchorUtilTest {

    @Test
    void normalizeConvertsCrlfToLf() {
        String crlf = "第一行\r\n第二行\r\n";
        String lf = "第一行\n第二行\n";
        assertEquals(AnchorUtil.normalize(lf, false), AnchorUtil.normalize(crlf, false));
    }

    @Test
    void normalizeCollapsesInlineWhitespaceForNonCode() {
        String raw = "Java 的   Lambda\t表达式";
        assertEquals("Java 的 Lambda 表达式", AnchorUtil.normalize(raw, false));
    }

    @Test
    void normalizePreservesIndentationForCode() {
        String raw = "    int a = 1;\n        return a;\n";
        // 代码块：缩进保留，只去行尾空白
        assertEquals("    int a = 1;\n        return a;", AnchorUtil.normalize(raw, true));
    }

    @Test
    void normalizeStripsTrailingWhitespacePerLine() {
        String raw = "a  \nb \nc";
        assertEquals("a\nb\nc", AnchorUtil.normalize(raw, false));
    }

    @Test
    void normalizeStripsLeadingTrailingBlankLines() {
        String raw = "\n\n正文\n\n";
        assertEquals("正文", AnchorUtil.normalize(raw, false));
    }

    @Test
    void crlfAndLfProduceSameHash8() {
        String lf = "# 标题\n\n第一段\n";
        String crlf = "# 标题\r\n\r\n第一段\r\n";
        String h1 = AnchorUtil.hash8(AnchorUtil.normalize(lf, false));
        String h2 = AnchorUtil.hash8(AnchorUtil.normalize(crlf, false));
        assertEquals(h1, h2);
        assertEquals(8, h1.length());
    }

    @Test
    void anchorFormat() {
        String hash = AnchorUtil.hash8("abc");
        assertEquals("b3-" + hash, AnchorUtil.anchor(3, hash));
    }

    @Test
    void parseIndexAndHash() {
        assertEquals(3, AnchorUtil.parseIndex("b3-9f2a1c04"));
        assertEquals("9f2a1c04", AnchorUtil.parseHash("b3-9f2a1c04"));
        assertEquals(-1, AnchorUtil.parseIndex("x3-abc"));
        assertEquals(-1, AnchorUtil.parseIndex("b-abc"));
        assertEquals(-1, AnchorUtil.parseIndex(null));
    }

    @Test
    void normalizeDoesNotCollapseInsideCodeLine() {
        // 代码块行内的多个空格必须保留（语义）
        assertEquals("a  b", AnchorUtil.normalize("a  b", true));
    }
}
