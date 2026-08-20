package com.learnnotes.uploads;

import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import com.learnnotes.uploads.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片上传测试 —— 覆盖计划卡 T18 的 5 个用例。
 */
class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setUploadDir(tempDir.resolve("uploads").toString());
        props.setMaxImageMb(5);
        service = new ImageStorageService(props);
    }

    private MockMultipartFile image(String ext, String filename) throws Exception {
        BufferedImage img = new BufferedImage(128, 72, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String format = "webp".equals(ext) ? "png" : ext; // ImageIO 无 webp 写入器，用 png 字节替代场景
        ImageIO.write(img, format, out);
        return new MockMultipartFile("file", filename, "image/" + ext, out.toByteArray());
    }

    /** 正常 png / jpg 上传 */
    @Test
    void uploadValidImages() throws Exception {
        UploadResult png = service.save(image("png", "shot.png"));
        assertTrue(png.getUrl().startsWith("/uploads/"));
        assertTrue(png.getUrl().endsWith(".png"));
        assertEquals(128, png.getWidth());
        assertEquals(72, png.getHeight());
        assertFalse(png.isDedup());
        assertTrue(Files.exists(tempDir.resolve("uploads").resolve("2026/08")));

        UploadResult jpg = service.save(image("jpg", "photo.jpg"));
        assertTrue(jpg.getUrl().endsWith(".jpg"));
    }

    /** 同图二次上传返回 dedup:true 且磁盘只有一份 */
    @Test
    void duplicateUploadDedup() throws Exception {
        MockMultipartFile f = image("png", "shot.png");
        UploadResult first = service.save(f);
        UploadResult second = service.save(f);
        assertTrue(second.isDedup());
        assertEquals(first.getUrl(), second.getUrl());
        long count;
        try (Stream<Path> files = Files.walk(tempDir.resolve("uploads").resolve("2026/08"))) {
            count = files.filter(p -> p.toString().endsWith(".png")).count();
        }
        assertEquals(1, count);
    }

    /** 把 .txt 改名成 .png 上传 → 400 */
    @Test
    void fakeExtensionRejected() {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
                "this is not a png".getBytes());
        BizException e = assertThrows(BizException.class, () -> service.save(fake));
        assertEquals(400, e.getHttpStatus());
    }

    /** 超 5 MB → 400 */
    @Test
    void oversizedRejected() throws Exception {
        BufferedImage img = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] big = out.toByteArray();
        // 用随机字节制造 >5MB 的"图"（文件头校验会在大小校验之后才跑，大小校验先行）
        byte[] huge = new byte[6 * 1024 * 1024];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, 0, huge, 0, 4);
        MockMultipartFile f = new MockMultipartFile("file", "huge.png", "image/png", huge);
        BizException e = assertThrows(BizException.class, () -> service.save(f));
        assertEquals(400, e.getHttpStatus());
        assertTrue(e.getMessage().contains("5MB"));
    }

    /** 客户端文件名含 ../ → 服务端按哈希重命名，落点仍在 uploads 目录内 */
    @Test
    void traversalFilenameStillSafe() throws Exception {
        MockMultipartFile f = image("png", "../../evil.png");
        UploadResult result = service.save(f);
        // URL 只含哈希文件名，不含客户端文件名
        assertFalse(result.getUrl().contains("evil"));
        assertFalse(result.getUrl().contains(".."));
        // 目录穿越未发生
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("2026/08").resolve("evil.png")));
        assertTrue(Files.exists(tempDir.resolve("uploads")));
    }
}
