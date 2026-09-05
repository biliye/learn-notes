package com.learnnotes.imports.service;

import com.learnnotes.common.BizException;
import com.learnnotes.imports.dto.ZipImportResult;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.FrontMatterParser;
import com.learnnotes.markdown.MarkdownBlockParser;
import com.learnnotes.markdown.ParsedDoc;
import com.learnnotes.uploads.UploadResult;
import com.learnnotes.uploads.service.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 压缩包导入 —— 编辑器草稿流（§5.4）：解压 .zip（可含 .md + 图片），把正文
 * （剥离 front-matter、相对路径图片引用重写为 /uploads/...）返回给前端填入编辑器，
 * **不入库、不建分类**，由用户核对后手动保存（§5.3 POST /api/docs）。
 *
 * <p>图片复用 D11 的 ImageStorageService（magic number / 哈希去重 / 大小上限），
 * 只上传正文真正引用到的图片；未被引用的图片不导入。
 */
@Slf4j
@Service
public class ZipImportService {

    private static final long MAX_ZIP_BYTES = 50L * 1024 * 1024;        // 压缩包 ≤ 50MB
    private static final long MAX_UNCOMPRESSED_TOTAL = 100L * 1024 * 1024; // 解压总量 ≤ 100MB（防 zip bomb）
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;      // 单条目解压 ≤ 20MB（防单条 zip bomb）
    private static final int MAX_ENTRIES = 500;
    private static final long MAX_MD_BYTES = 2L * 1024 * 1024;          // 单 md ≤ 2MB
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");

    /** Markdown 图片引用：![alt](target) */
    private static final Pattern IMG_REF = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");

    private final ImageStorageService imageStorage;

    public ZipImportService(ImageStorageService imageStorage) {
        this.imageStorage = imageStorage;
    }

    public ZipImportResult importZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw BizException.badRequest("zip 文件不能为空");
        }
        if (zipFile.getSize() > MAX_ZIP_BYTES) {
            throw BizException.badRequest("压缩包不能超过 50MB");
        }
        String zipName = zipFile.getOriginalFilename() == null ? "archive.zip" : zipFile.getOriginalFilename();

        // 1. 解压：收集 md 与图片（按条目路径）
        Map<String, String> mdFiles = new LinkedHashMap<>();
        Map<String, byte[]> images = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        long totalUncompressed = 0;
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entries > MAX_ENTRIES) {
                    throw BizException.badRequest("压缩包内文件过多（> " + MAX_ENTRIES + " 个）");
                }
                String name = entry.getName();
                String path = normalizeEntryPath(name);
                if (path == null) {
                    warnings.add("跳过非法路径条目：" + name);
                    continue;
                }
                // 边读边计数，单条目与解压总量超限立即中断，杜绝先整条读入内存的 OOM 窗口
                byte[] bytes = readEntryFully(zis, Math.min(MAX_ENTRY_BYTES, MAX_UNCOMPRESSED_TOTAL - totalUncompressed));
                totalUncompressed += bytes.length;
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
                    if (bytes.length > MAX_MD_BYTES) {
                        warnings.add("跳过超过 2MB 的 md 文件：" + name);
                        continue;
                    }
                    mdFiles.put(path, new String(bytes, StandardCharsets.UTF_8));
                } else if (IMAGE_EXT.contains(extOf(path))) {
                    images.put(path, bytes);
                }
                // 其余文件忽略
            }
        } catch (IOException e) {
            throw BizException.badRequest("压缩包读取失败：" + e.getMessage());
        }
        if (mdFiles.isEmpty()) {
            throw BizException.badRequest("压缩包内没有找到 .md / .markdown 文件");
        }

        // 2. 多个 md 只取路径排序第一个
        String mainPath = mdFiles.keySet().stream().sorted().findFirst().orElseThrow();
        if (mdFiles.size() > 1) {
            warnings.add("压缩包内有 " + mdFiles.size() + " 个 md 文件，仅导入第一个：" + mainPath);
        }
        String raw = mdFiles.get(mainPath);
        String fileName = mainPath.contains("/") ? mainPath.substring(mainPath.lastIndexOf('/') + 1) : mainPath;
        String mdDir = mainPath.contains("/") ? mainPath.substring(0, mainPath.lastIndexOf('/')) : "";

        // 3. front-matter → title/slug/summary/tags；正文剥离 front-matter
        ParsedDoc parsed = FrontMatterParser.parse(raw);
        Map<String, Object> fm = parsed.getMeta();
        String title = fm == null ? null : asStr(fm.get("title"));
        String slug = fm == null ? null : asStr(fm.get("slug"));
        if (title == null || title.isBlank()) {
            title = firstH1(parsed.getBody());
        }
        if (title == null || title.isBlank()) {
            title = stripExt(fileName);
        }
        List<String> tags = fm == null ? new ArrayList<>() : asStringList(fm.get("tags"));
        String summary = fm == null ? null : asStr(fm.get("summary"));

        // 4. 图片引用重写（只上传被正文引用的图）
        ZipImportResult result = new ZipImportResult();
        result.setFilename(zipName);
        result.setTitle(title);
        result.setSlug(blankToNull(slug));
        result.setSummary(blankToNull(summary));
        result.setTags(tags);
        result.setContentMd(rewriteImages(parsed.getBody(), mdDir, images, result, warnings));
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 重写正文里的相对路径图片引用为 /uploads/...；返回新正文。
     * 外链（http/https）、data:、#锚点、已是 /uploads/... 的引用原样保留。
     */
    private String rewriteImages(String body, String mdDir, Map<String, byte[]> images,
                                 ZipImportResult result, List<String> warnings) {
        List<ImgRef> refs = new ArrayList<>();
        Matcher m = IMG_REF.matcher(body);
        while (m.find()) {
            refs.add(new ImgRef(m.start(), m.end(), m.group(1), m.group(2)));
        }
        Set<String> referenced = new HashSet<>();
        int imported = 0;
        int missing = 0;
        StringBuilder sb = new StringBuilder(body.length());
        int cursor = 0;
        for (ImgRef ref : refs) {
            sb.append(body, cursor, ref.start());
            cursor = ref.end();
            String rewritten = resolveImageRef(ref.target(), mdDir, images, referenced, warnings);
            if (rewritten == null) {
                missing++;
                sb.append(body, ref.start(), ref.end()); // 未找到：保留原引用
            } else {
                sb.append("![").append(ref.alt()).append("](").append(rewritten).append(")");
                if (!rewritten.equals(ref.target())) {
                    imported++;
                }
            }
        }
        sb.append(body, cursor, body.length());
        result.setImportedImages(imported);
        if (missing > 0) {
            warnings.add(missing + " 张图片引用未在压缩包内找到，已保留原引用（预览可能无法显示）");
        }
        int unused = (int) images.keySet().stream().filter(p -> !referenced.contains(p)).count();
        if (unused > 0) {
            result.setSkippedImages(unused);
            warnings.add(unused + " 张图片未被正文引用，未导入");
        }
        return sb.toString();
    }

    /** 解析单条图片引用：返回重写后的目标；无需处理的外链原样返回 target；未找到返回 null */
    private String resolveImageRef(String target, String mdDir, Map<String, byte[]> images,
                                   Set<String> referenced, List<String> warnings) {
        if (target.startsWith("http://") || target.startsWith("https://")
                || target.startsWith("data:") || target.startsWith("#")
                || target.startsWith("/uploads/")) {
            return target;
        }
        String resolved = resolveRelative(mdDir, target);
        if (resolved == null) {
            warnings.add("图片引用路径越出压缩包根目录，已保留：" + target);
            return null;
        }
        byte[] bytes = images.get(resolved);
        if (bytes == null) {
            return null;
        }
        referenced.add(resolved);
        String ext = extOf(resolved);
        try {
            UploadResult ur = imageStorage.saveBytes(bytes, "img." + ext);
            return ur.getUrl();
        } catch (BizException e) {
            warnings.add("图片「" + resolved + "」上传失败：" + e.getMessage());
            return null;
        }
    }

    // ---------- 小工具 ----------

    /** 读完整条目；解压后大小超过 maxBytes（防 zip bomb）立即拒绝，不在内存里无限累积 */
    private static byte[] readEntryFully(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > maxBytes) {
                throw BizException.badRequest("压缩包解压后内容过大");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 规范化 zip 条目路径：拒绝绝对路径 / 盘符 / 路径穿越；统一用 `/` 分隔 */
    static String normalizeEntryPath(String name) {
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.matches("^[a-zA-Z]:.*")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String seg : n.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                return null;
            }
            parts.add(seg);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    /** 把相对引用按 md 所在目录解析为 zip 内路径；`..` 越出 zip 根返回 null */
    static String resolveRelative(String dir, String target) {
        String joined = dir.isEmpty() ? target : dir + "/" + target;
        List<String> parts = new ArrayList<>();
        for (String seg : joined.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static String extOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String firstH1(String body) {
        for (Block b : MarkdownBlockParser.parseBody(body).getBlocks()) {
            if ("heading".equals(b.getType()) && b.getLevel() != null && b.getLevel() == 1) {
                return b.getRaw().replaceAll("^#+\\s*", "").replaceAll("\\s*#+$", "").trim();
            }
        }
        return null;
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            return (List<String>) (List<?>) list;
        }
        if (o != null) {
            return List.of(String.valueOf(o));
        }
        return new ArrayList<>();
    }

    private record ImgRef(int start, int end, String alt, String target) {
    }
}
