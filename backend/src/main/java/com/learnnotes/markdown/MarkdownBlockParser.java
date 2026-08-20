package com.learnnotes.markdown;

import lombok.Data;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.ThematicBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 块切分 —— 全项目唯一权威（D3）。
 *
 * <p>块 = commonmark Document 的**直接子节点**；`raw` 用 `IncludeSourceSpans.BLOCKS`
 * 从原文切片（禁止 renderer 反向生成，否则会改写原文导致哈希不稳）。
 *
 * <p>该包不碰数据库、不碰 Spring 容器，纯静态工具，便于单测。
 */
public final class MarkdownBlockParser {

    /** 引用式链接定义：`[ref]: url` */
    private static final Pattern REF_LINK_DEF = Pattern.compile("^\\[[^\\]]+]:\\s*\\S+", Pattern.MULTILINE);
    /** 脚注：`[^1]` */
    private static final Pattern FOOTNOTE = Pattern.compile("\\[\\^[^\\]]+\\]");

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();

    private MarkdownBlockParser() {
    }

    public static ParseResult parse(String markdown) {
        ParsedDoc parsed = FrontMatterParser.parse(markdown);
        return parseBody(parsed.getBody());
    }

    public static ParseResult parseBody(String body) {
        String source = body == null ? "" : body;
        List<String> warnings = new ArrayList<>();
        Node document = PARSER.parse(source);
        List<Block> blocks = new ArrayList<>();
        int index = 0;
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            Block b = new Block();
            b.setIndex(index);
            b.setRaw(sliceRaw(source, child));
            b.setType(mapType(child, b, warnings));
            if (child instanceof Heading heading) {
                b.setLevel(heading.getLevel());
            }
            b.setAnchor(AnchorUtil.anchor(index,
                    AnchorUtil.hash8(AnchorUtil.normalize(b.getRaw(), "code".equals(b.getType())))));
            blocks.add(b);
            index++;
        }
        collectWarnings(source, warnings);
        ParseResult r = new ParseResult();
        r.setBlocks(blocks);
        r.setWarnings(warnings);
        return r;
    }

    private static String mapType(Node node, Block b, List<String> warnings) {
        int blockNo = b.getIndex() + 1;
        if (node instanceof Heading) {
            return "heading";
        }
        if (node instanceof FencedCodeBlock fenced) {
            String info = fenced.getInfo() == null ? "" : fenced.getInfo().trim();
            if (info.isEmpty()) {
                b.setLang("text");
                warnings.add("代码块缺少语言标签，已按 text 渲染（第 " + blockNo + " 块）");
            } else {
                b.setLang(info.split("[ \\t]+")[0].trim());
            }
            return "code";
        }
        if (node instanceof IndentedCodeBlock) {
            b.setLang("text");
            warnings.add("缩进式代码块已按 text 渲染，建议改用三反引号围栏（第 " + blockNo + " 块）");
            return "code";
        }
        if (node instanceof BulletList || node instanceof OrderedList) {
            return "list";
        }
        if (node instanceof TableBlock) {
            return "table";
        }
        if (node instanceof BlockQuote) {
            return "quote";
        }
        if (node instanceof ThematicBreak) {
            return "thematic_break";
        }
        if (node instanceof HtmlBlock) {
            warnings.add("包含原始 HTML 块（第 " + blockNo + " 块），渲染时会被净化");
            return "html";
        }
        if (node instanceof Paragraph) {
            return "paragraph";
        }
        return "other";
    }

    private static String sliceRaw(String source, Node node) {
        List<SourceSpan> spans = node.getSourceSpans();
        if (spans == null || spans.isEmpty()) {
            return "";
        }
        // 行起始字符偏移表（columnIndex 以字符计）
        int[] lineOffsets = lineOffsets(source);
        StringBuilder sb = new StringBuilder();
        for (SourceSpan span : spans) {
            int line = span.getLineIndex();
            int start = (line >= 0 && line < lineOffsets.length ? lineOffsets[line] : 0) + span.getColumnIndex();
            int end = Math.min(start + span.getLength(), source.length());
            if (start >= 0 && start < source.length()) {
                sb.append(source, start, end);
            }
        }
        return sb.toString();
    }

    private static int[] lineOffsets(String source) {
        int[] offsets = new int[countLines(source)];
        offsets[0] = 0;
        int line = 1;
        for (int i = 0; i < source.length() && line < offsets.length; i++) {
            if (source.charAt(i) == '\n') {
                offsets[line++] = i + 1;
            }
        }
        return offsets;
    }

    private static int countLines(String source) {
        int n = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static void collectWarnings(String body, List<String> warnings) {
        if (body == null || body.isEmpty()) {
            return;
        }
        // 引用式链接与脚注：块定义常跨块出现，对整个正文做一次正则
        Matcher ref = REF_LINK_DEF.matcher(body);
        int refCount = 0;
        while (ref.find() && refCount < 5) {
            warnings.add("检测到引用式链接定义，已忽略：" + ref.group().trim());
            refCount++;
        }
        Matcher foot = FOOTNOTE.matcher(body);
        int footCount = 0;
        while (foot.find() && footCount < 5) {
            warnings.add("检测到脚注语法，渲染按块进行可能失效：" + foot.group());
            footCount++;
        }
    }

    @Data
    public static class ParseResult {
        private List<Block> blocks;
        private List<String> warnings;
    }
}
