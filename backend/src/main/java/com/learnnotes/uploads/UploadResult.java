package com.learnnotes.uploads;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传响应（§5.6）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {

    /** 站内访问路径，如 /uploads/2026/08/3f7a91c4d5e6b208.png */
    private String url;
    private int width;
    private int height;
    private long bytes;
    /** true = 内容已存在，直接复用既有路径（天然去重） */
    private boolean dedup;
}
