package com.learnnotes.uploads.service;

import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import com.learnnotes.uploads.UploadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图片哈希落盘（D11、R30）：
 * - 存 ${app.uploadDir}/u&lt;ownerId&gt;/YYYY/MM/&lt;sha256前16位&gt;.&lt;ext&gt;，同一用户内同内容重复上传直接复用（幂等去重）
 * - 归属隔离：每个用户一个目录，跨用户各存一份（放弃跨用户去重，换取"删一个人不影响别人"）
 * - 安全硬要求：扩展名白名单 + magic number 校验 + 服务端按哈希重命名 + 大小上限 + ImageIO 可读
 * - 静态访问由 Nginx 直接托管（T16），后端不提供图片读取接口
 */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");
    /** 像素总量上限（40MP ≈ ARGB 160MB），防"小文件大尺寸"解码炸弹 OOM */
    private static final long MAX_PIXELS = 40_000_000L;

    private final AppProperties props;

    public ImageStorageService(AppProperties props) {
        this.props = props;
    }

    /** 用户图片子目录名（老图片没有这一段，见 ImageMigrationService 的迁移） */
    public static String ownerSegment(long ownerId) {
        return "u" + ownerId;
    }

    public UploadResult save(long ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("file 不能为空");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BizException.badRequest("读取上传文件失败");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return saveBytes(ownerId, bytes, originalName);
    }

    /**
     * 以字节数组保存（zip 导入解包出的图片也走这里，D11 规则完全一致）。
     */
    public UploadResult saveBytes(long ownerId, byte[] bytes, String originalName) {
        if (bytes == null || bytes.length == 0) {
            throw BizException.badRequest("图片内容为空");
        }
        double maxMb = props.getMaxImageMb() <= 0 ? 5 : props.getMaxImageMb();
        if (bytes.length > maxMb * 1024 * 1024) {
            throw BizException.badRequest("图片不能超过 " + (long) maxMb + "MB");
        }
        String name = originalName == null ? "" : originalName;
        String ext = extOf(name);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("仅支持 png/jpg/jpeg/gif/webp：" + name);
        }
        verifyMagicNumber(bytes, ext);

        // 只读图片头部拿宽高，不整图解码：超大尺寸直接拒绝，防解码炸弹
        int[] dims = readImageDimensions(bytes);
        int width = dims[0];
        int height = dims[1];

        String hash = sha256(bytes).substring(0, 16);
        String relDir = ownerSegment(ownerId) + "/" + LocalDate.now().format(YM);
        String filename = hash + "." + ext;
        Path target = Paths.get(props.getUploadDir()).resolve(relDir).resolve(filename);

        boolean dedup = Files.exists(target);
        if (!dedup) {
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException e) {
                throw new BizException(500, 500, "图片落盘失败");
            }
        }
        return new UploadResult(
                "/uploads/" + relDir + "/" + filename,
                width,
                height,
                bytes.length,
                dedup);
    }

    /** 上传根目录（绝对、已 normalize），清理与迁移共用 */
    public Path uploadRoot() {
        return Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
    }

    /** 某用户的图片目录；ownerId 是 long，不存在拼接注入面 */
    public Path ownerDir(long ownerId) {
        return uploadRoot().resolve(ownerSegment(ownerId));
    }

    /**
     * 删除某用户自己的图片目录，返回删掉的文件数。
     * 目录按 ownerId 独占，删它不会碰到任何其他用户的图片（这正是分目录的目的）。
     */
    public int purgeUser(long ownerId) {
        Path dir = ownerDir(ownerId);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        var log = org.slf4j.LoggerFactory.getLogger(ImageStorageService.class);
        List<Path> entries;
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            // 先深后浅，文件删完目录才能删掉
            entries = walk.sorted(java.util.Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            log.warn("遍历用户图片目录失败（忽略）：{} -> {}", dir, e.getMessage());
            return 0;
        }
        int deletedFiles = 0;
        for (Path p : entries) {
            try {
                boolean file = Files.isRegularFile(p);
                if (Files.deleteIfExists(p) && file) {
                    deletedFiles++;
                }
            } catch (IOException e) {
                log.warn("删除用户图片失败（忽略）：{} -> {}", p, e.getMessage());
            }
        }
        return deletedFiles;
    }

    /** 用 ImageReader 只读头部宽高（不解码像素），无效图片或超过 MAX_PIXELS 即拒绝 */
    private int[] readImageDimensions(byte[] bytes) {
        // 必须先包成 ImageInputStream 再喂给 getImageReaders：
        // 裸 InputStream 走 Object 重载在本 JDK 上拿不到任何 reader（ImageIO.read 反而可以）
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw BizException.badRequest("不是有效的图片文件");
            }
            ImageReader reader = readers.next();
            reader.setInput(in);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            reader.dispose();
            if (w <= 0 || h <= 0) {
                throw BizException.badRequest("不是有效的图片文件");
            }
            if ((long) w * h > MAX_PIXELS) {
                throw BizException.badRequest("图片尺寸过大（" + w + "x" + h + "）");
            }
            return new int[]{w, h};
        } catch (IOException e) {
            throw BizException.badRequest("不是有效的图片文件");
        }
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 校验真实文件头（magic number），头与扩展名不符则 400 */
    private void verifyMagicNumber(byte[] b, String ext) {
        if (b.length < 12) {
            throw BizException.badRequest("文件内容过短，不是有效图片");
        }
        boolean ok;
        switch (ext) {
            case "png" -> ok = startsWith(b, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> ok = startsWith(b, new int[]{0xFF, 0xD8, 0xFF});
            case "gif" -> ok = startsWith(b, new int[]{0x47, 0x49, 0x46, 0x38}) && (b[4] == '7' || b[4] == '9');
            case "webp" -> ok = startsWith(b, new int[]{'R', 'I', 'F', 'F'})
                    && b.length >= 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> ok = false;
        }
        if (!ok) {
            throw BizException.badRequest("文件头与扩展名不符，已拒绝");
        }
    }

    private boolean startsWith(byte[] data, int[] header) {
        if (data.length < header.length) {
            return false;
        }
        for (int i = 0; i < header.length; i++) {
            if ((data[i] & 0xFF) != header[i]) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
