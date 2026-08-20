package com.learnnotes.imports.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入单篇响应（§5.4）。
 */
@Data
public class ImportResult {

    private Long docId;
    private boolean created;
    private int version;
    private String title;
    private String slug;
    /** 原始文件名（多文件上传用） */
    private String filename;
    private NodeInfo category;
    private NodeInfo topic;
    /** HINT | FRONT_MATTER | FILENAME | INBOX */
    private String resolvedBy;
    private String storedPath;
    private Reanchor reanchor;
    private List<String> warnings = new ArrayList<>();
    /** 多文件上传中单文件失败时的错误信息 */
    private String error;

    @Data
    public static class NodeInfo {
        private Long id;
        private String name;
        private Boolean autoCreated;
    }

    @Data
    public static class Reanchor {
        private int active;
        private int stale;
        private int orphan;
    }
}
