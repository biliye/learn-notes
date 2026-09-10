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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片上传测试 —— 覆盖计划卡 T18 的 5 个用例 + 用户目录隔离（图片归属改造）。
 */
class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ImageStorageService service;

    private static final long USER_A = 5L;
    private static final long USER_B = 9L;

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

    private Path monthDir(long ownerId) {
        return tempDir.resolve("uploads").resolve("u" + ownerId)
                .resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")));
    }

    private long countFiles(Path dir, String suffix) throws Exception {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(suffix)).count();
        }
    }

    /** 正常 png / jpg 上传，落在自己的用户目录下 */
    @Test
    void uploadValidImages() throws Exception {
        UploadResult png = service.save(USER_A, image("png", "shot.png"));
        assertTrue(png.getUrl().startsWith("/uploads/u5/"), png.getUrl());
        assertTrue(png.getUrl().endsWith(".png"));
        assertEquals(128, png.getWidth());
        assertEquals(72, png.getHeight());
        assertFalse(png.isDedup());
        assertTrue(Files.exists(monthDir(USER_A)));

        UploadResult jpg = service.save(USER_A, image("jpg", "photo.jpg"));
        assertTrue(jpg.getUrl().endsWith(".jpg"));
    }

    /** 同一用户二次上传同图 → dedup:true，磁盘只有一份 */
    @Test
    void duplicateUploadDedup() throws Exception {
        MockMultipartFile f = image("png", "shot.png");
        UploadResult first = service.save(USER_A, f);
        UploadResult second = service.save(USER_A, f);
        assertTrue(second.isDedup());
        assertEquals(first.getUrl(), second.getUrl());
        assertEquals(1, countFiles(tempDir.resolve("uploads"), ".png"));
    }

    /** 不同用户上传同一张图 → 各存一份，互不共享（这是"删一个人的图不影响别人"的前提） */
    @Test
    void sameImageDifferentUsersKeptSeparate() throws Exception {
        MockMultipartFile f = image("png", "shot.png");
        UploadResult a = service.save(USER_A, f);
        UploadResult b = service.save(USER_B, f);

        assertNotEquals(a.getUrl(), b.getUrl());
        assertTrue(a.getUrl().startsWith("/uploads/u5/"));
        assertTrue(b.getUrl().startsWith("/uploads/u9/"));
        assertFalse(b.isDedup(), "跨用户不该复用别人的文件");
        assertEquals(2, countFiles(tempDir.resolve("uploads"), ".png"));
    }

    /** 清某个用户的图片只动他自己的目录 */
    @Test
    void purgeUserOnlyTouchesOwnDir() throws Exception {
        service.save(USER_A, image("png", "a.png"));
        service.save(USER_A, image("jpg", "a.jpg"));
        service.save(USER_B, image("png", "b.png"));

        assertEquals(2, service.purgeUser(USER_A));
        assertFalse(Files.exists(service.ownerDir(USER_A)));
        assertTrue(Files.exists(service.ownerDir(USER_B)), "别人的目录必须原封不动");
        assertEquals(1, countFiles(tempDir.resolve("uploads"), ".png"));
    }

    /** 没有图片目录时清空是空操作，不报错 */
    @Test
    void purgeUserWithoutDirIsNoOp() {
        assertEquals(0, service.purgeUser(404L));
    }

    /** 把 .txt 改名成 .png 上传 → 400 */
    @Test
    void fakeExtensionRejected() {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
                "this is not a png".getBytes());
        BizException e = assertThrows(BizException.class, () -> service.save(USER_A, fake));
        assertEquals(400, e.getHttpStatus());
    }

    /** 超 5 MB → 400 */
    @Test
    void oversizedRejected() throws Exception {
        // 用随机字节制造 >5MB 的"图"（文件头校验会在大小校验之后才跑，大小校验先行）
        byte[] huge = new byte[6 * 1024 * 1024];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, 0, huge, 0, 4);
        MockMultipartFile f = new MockMultipartFile("file", "huge.png", "image/png", huge);
        BizException e = assertThrows(BizException.class, () -> service.save(USER_A, f));
        assertEquals(400, e.getHttpStatus());
        assertTrue(e.getMessage().contains("5MB"));
    }

    /** 客户端文件名含 ../ → 服务端按哈希重命名，落点仍在该用户目录内 */
    @Test
    void traversalFilenameStillSafe() throws Exception {
        MockMultipartFile f = image("png", "../../evil.png");
        UploadResult result = service.save(USER_A, f);
        // URL 只含哈希文件名，不含客户端文件名
        assertFalse(result.getUrl().contains("evil"));
        assertFalse(result.getUrl().contains(".."));
        assertFalse(Files.exists(monthDir(USER_A).resolve("evil.png")));
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("evil.png")));
    }
}
