package com.learnnotes.markdown;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * front-matter 解析结果。
 */
@Data
public class ParsedDoc {

    private boolean hasFrontMatter;
    /** 扁平 YAML 字段（key → 值；tags 为逗号连接的字符串或 java.util.List） */
    private Map<String, Object> meta = new HashMap<>();
    /** 去掉 front-matter 之后的正文 */
    private String body;
}
