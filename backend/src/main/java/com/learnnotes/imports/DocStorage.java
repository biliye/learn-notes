package com.learnnotes.imports;

import com.learnnotes.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入原文落盘（R17，备份三层 L3）：写 storage/docs/&lt;username&gt;/&lt;目录链&gt;/&lt;slug&gt;.md。
 * V3 起按用户名分目录，防止不同用户的同名 slug 互相覆盖；V4 起目录链为任意深度（大类 → … → 叶目录）。
 * 路径必须做穿越校验：拒绝含 `..`、绝对路径、分隔符的 slug。
 */
@Component
public class DocStorage {

    private final AppProperties props;

    public DocStorage(AppProperties props) {
        this.props = props;
    }

    /**
     * @param slugPath 目录 slug 链（大类 → … → 叶目录）
     * @return 落盘后的完整路径字符串（用于响应 storedPath）
     * @throws IllegalArgumentException slug 含非法字符
     * @throws IOException 写入失败
     */
    public String write(String username, List<String> slugPath, String docSlug, String content)
            throws IOException, IllegalArgumentException {
        Path target = resolve(username, slugPath, docSlug);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target.toString();
    }

    public String pathFor(String username, List<String> slugPath, String docSlug) {
        return resolve(username, slugPath, docSlug).toString();
    }

    private Path resolve(String username, List<String> slugPath, String docSlug) {
        validateSegment(username, "用户名");
        if (slugPath == null || slugPath.isEmpty()) {
            throw new IllegalArgumentException("目录链为空，无法落盘");
        }
        for (String segment : slugPath) {
            validateSegment(segment, "目录 slug");
        }
        validateSegment(docSlug, "文档 slug");
        Path base = Paths.get(props.getStorageDir()).toAbsolutePath();
        List<String> segments = new ArrayList<>();
        segments.add(username);
        segments.addAll(slugPath);
        segments.add(docSlug + ".md");
        Path target = base;
        for (String segment : segments) {
            target = target.resolve(segment);
        }
        target = target.normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("存储路径越界，已拒绝");
        }
        return target;
    }

    private void validateSegment(String segment, String label) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException(label + " 为空，无法落盘");
        }
        if (segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.startsWith(".") || segment.matches("^[a-zA-Z]:.*")) {
            throw new IllegalArgumentException(label + " 含非法字符，已拒绝：" + segment);
        }
    }
}
