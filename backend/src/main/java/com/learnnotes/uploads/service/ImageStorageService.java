package com.learnnotes.uploads.service;

import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import com.learnnotes.uploads.UploadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * 图片哈希落盘（D11、R30）：
 * - 存 ${app.uploadDir}/YYYY/MM/&lt;sha256前16位&gt;.&lt;ext&gt;，同内容重复上传直接复用（幂等去重）
 * - 安全硬要求：扩展名白名单 + magic number 校验 + 服务端按哈希重命名 + 大小上限 + ImageIO 可读
 * - 静态访问由 Nginx 直接托管（T16），后端不提供图片读取接口
 */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");

    private final AppProperties props;

    public ImageStorageService(AppProperties props) {
        this.props = props;
    }

    public UploadResult save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("file 不能为空");
        }
        double maxMb = props.getMaxImageMb() <= 0 ? 5 : props.getMaxImageMb();
        if (file.getSize() > maxMb * 1024 * 1024) {
            throw BizException.badRequest("图片不能超过 " + (long) maxMb + "MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BizException.badRequest("读取上传文件失败");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("仅支持 png/jpg/jpeg/gif/webp：" + originalName);
        }
        verifyMagicNumber(bytes, ext);

        // 读宽高：读不出说明不是有效图片
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw BizException.badRequest("不是有效的图片文件");
        }

        String hash = sha256(bytes).substring(0, 16);
        String monthDir = LocalDate.now().format(YM);
        String filename = hash + "." + ext;
        Path target = Paths.get(props.getUploadDir()).resolve(monthDir).resolve(filename);

        boolean dedup = Files.exists(target);
        if (!dedup) {
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException e) {
                throw new BizException(500, 500, "图片落盘失败：" + e.getMessage());
            }
        }
        return new UploadResult(
                "/uploads/" + monthDir + "/" + filename,
                image.getWidth(),
                image.getHeight(),
                bytes.length,
                dedup);
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
