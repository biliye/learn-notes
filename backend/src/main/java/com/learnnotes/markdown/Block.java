package com.learnnotes.markdown;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个正文块（D3）：块 = commonmark Document 的直接子节点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Block {

    /** 块序号，0 起 */
    private int index;
    /** 锚点 = "b" + index + "-" + hash8（D5） */
    private String anchor;
    /** heading | paragraph | code | list | table | quote | thematic_break | html | other */
    private String type;
    /** 仅 heading 有值（1-6） */
    private Integer level;
    /** 仅 code 有值；缺省 text */
    private String lang;
    /** 该块的原始 Markdown 片段（代码块含前后围栏行） */
    private String raw;
}
