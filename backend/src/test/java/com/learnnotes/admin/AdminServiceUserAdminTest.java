package com.learnnotes.admin;

import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.AuthService;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.auth.SysUser;
import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.ErrorCode;
import com.learnnotes.config.AppProperties;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.mapper.DocVersionMapper;
import com.learnnotes.uploads.service.ImageStorageService;
import com.learnnotes.uploads.service.UploadCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员改密/删号：
 * - 只有 ADMIN 能调用，且目标只能是普通用户账号（管理员账号不可被改密或删除，顺带堵住自锁）
 * - 删号按 版本 → 批注 → 文档 → 分类树 → 账号 的顺序级联清理，不留孤儿行
 * - 图片：本人 u{id}/ 目录整目录清；老式共享图只有"再无他人引用"时才删
 *   （图片清理走**真实**的 UploadCleanupService + 临时目录，只有数据库 mapper 是 mock）
 */
class AdminServiceUserAdminTest {

    @TempDir
    Path tempDir;

    private DocMapper docMapper;
    private SysUserMapper userMapper;
    private CatalogNodeMapper catalogMapper;
    private DocVersionMapper docVersionMapper;
    private DocAnnotationMapper docAnnotationMapper;
    private ImageStorageService imageStorage;
    private UploadCleanupService uploadCleanup;
    private AuthService authService;
    private AdminService adminService;

    private static final CurrentUser ADMIN = new CurrentUser(1L, "biliye", SysUser.ROLE_ADMIN);
    private static final CurrentUser MEMBER = new CurrentUser(2L, "newbie", SysUser.ROLE_USER);

    @BeforeEach
    void setUp() {
        docMapper = mock(DocMapper.class);
        userMapper = mock(SysUserMapper.class);
        catalogMapper = mock(CatalogNodeMapper.class);
        docVersionMapper = mock(DocVersionMapper.class);
        docAnnotationMapper = mock(DocAnnotationMapper.class);
        authService = mock(AuthService.class);

        AppProperties props = new AppProperties();
        props.setUploadDir(tempDir.resolve("uploads").toString());
        imageStorage = new ImageStorageService(props);
        uploadCleanup = new UploadCleanupService(imageStorage, docMapper, docAnnotationMapper);

        adminService = new AdminService(docMapper, userMapper, catalogMapper, authService,
                docVersionMapper, docAnnotationMapper, uploadCleanup);

        when(userMapper.selectById(2L)).thenReturn(user(2L, "newbie", SysUser.ROLE_USER));
        when(userMapper.selectById(1L)).thenReturn(user(1L, "biliye", SysUser.ROLE_ADMIN));
        when(authService.resetPassword(any(SysUser.class), any()))
                .thenReturn(Map.of("userId", 2L, "username", "newbie", "nickname", "新同学"));
    }

    /** 造一个真实存在的图片文件，返回其相对 uploads 的路径 */
    private void writeImage(String rel) throws Exception {
        Path target = tempDir.resolve("uploads").resolve(rel);
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{1, 2, 3});
    }

    private SysUser user(Long id, String username, String role) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        u.setRole(role);
        return u;
    }

    @Test
    void adminResetsMemberPassword() {
        Map<String, Object> result = adminService.resetUserPassword(ADMIN, 2L, "new-password-8");

        assertEquals(2L, result.get("userId"));
        verify(authService).resetPassword(any(SysUser.class), any());
    }

    @Test
    void normalUserCannotResetOthersPassword() {
        BizException e = assertThrows(BizException.class,
                () -> adminService.resetUserPassword(MEMBER, 2L, "new-password-8"));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
        verify(authService, never()).resetPassword(any(), any());
    }

    /** 管理员账号不可被改密：既防互相夺号，也挡住「把自己锁在门外」 */
    @Test
    void adminAccountIsNotManageable() {
        BizException e = assertThrows(BizException.class,
                () -> adminService.resetUserPassword(ADMIN, 1L, "new-password-8"));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
        assertEquals("只能管理普通用户账号", e.getMessage());
        verify(authService, never()).resetPassword(any(), any());
    }

    @Test
    void unknownUserIsNotFound() {
        BizException e = assertThrows(BizException.class,
                () -> adminService.resetUserPassword(ADMIN, 404L, "new-password-8"));

        assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    }

    @Test
    void shortPasswordIsRejectedBeforeReachingAuthService() {
        when(authService.resetPassword(any(SysUser.class), any()))
                .thenThrow(BizException.badRequest("新密码至少 8 位"));

        BizException e = assertThrows(BizException.class,
                () -> adminService.resetUserPassword(ADMIN, 2L, "short"));
        assertEquals(ErrorCode.BAD_REQUEST, e.getCode());
    }

    /** 删号：本人目录整目录清掉，没人再引用的老图也顺手清掉 */
    @Test
    void deleteUserClearsOwnImagesAndUnreferencedLegacyImage() throws Exception {
        writeImage("u2/2026/09/mine.png");
        writeImage("2026/08/shared.png");
        Doc doc = doc(11L);
        doc.setContentMd("![a](/uploads/u2/2026/09/mine.png)\n\n![b](/uploads/2026/08/shared.png)");
        when(docMapper.selectByOwner(2L)).thenReturn(List.of(doc));
        when(docAnnotationMapper.selectByDoc(11L)).thenReturn(List.of());
        // 其他用户都没引用这张老图
        when(docMapper.countRefsExcludingOwner(eq(2L), anyString())).thenReturn(0);
        when(docAnnotationMapper.countRefsExcludingOwner(eq(2L), anyString())).thenReturn(0);

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(1, result.get("deletedImages"));
        assertEquals(1, result.get("deletedLegacyImages"));
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("u2")), "本人目录应整目录清掉");
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("2026/08/shared.png")));
    }

    /** 关键隔离：老图还被别人引用时必须留着——这就是"删一个人不影响别人" */
    @Test
    void deleteUserKeepsLegacyImageStillReferencedByOthers() throws Exception {
        writeImage("2026/08/shared.png");
        Doc doc = doc(11L);
        doc.setContentMd("![b](/uploads/2026/08/shared.png)");
        when(docMapper.selectByOwner(2L)).thenReturn(List.of(doc));
        when(docAnnotationMapper.selectByDoc(11L)).thenReturn(List.of());
        // 别人的文档还在引用这张老图
        when(docMapper.countRefsExcludingOwner(eq(2L), anyString())).thenReturn(1);

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(0, result.get("deletedLegacyImages"));
        assertTrue(Files.exists(tempDir.resolve("uploads").resolve("2026/08/shared.png")),
                "别人还在引用的老图不能删");
    }

    /** 别人的见解正文引用了这张老图 → 同样不能删（这条曾因只扫 block_snippet 而漏判） */
    @Test
    void deleteUserKeepsLegacyImageReferencedByOtherInsightBody() throws Exception {
        writeImage("2026/08/in-insight.png");
        Doc doc = doc(11L);
        doc.setContentMd("![b](/uploads/2026/08/in-insight.png)");
        when(docMapper.selectByOwner(2L)).thenReturn(List.of(doc));
        when(docAnnotationMapper.selectByDoc(11L)).thenReturn(List.of());
        when(docMapper.countRefsExcludingOwner(eq(2L), anyString())).thenReturn(0);
        when(docAnnotationMapper.countRefsExcludingOwner(eq(2L), anyString())).thenReturn(1);

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(0, result.get("deletedLegacyImages"));
        assertTrue(Files.exists(tempDir.resolve("uploads").resolve("2026/08/in-insight.png")));
    }

    /** 见解正文里的图片也算该用户的引用，删号时要一并交给清理逻辑 */
    @Test
    void deleteUserCollectsRefsFromInsightBody() throws Exception {
        writeImage("u2/2026/09/in-insight.png");
        Doc plain = doc(11L);
        plain.setContentMd("正文没有图");
        when(docMapper.selectByOwner(2L)).thenReturn(List.of(plain));
        DocAnnotation ann = new DocAnnotation();
        ann.setId(77L);
        ann.setDocId(11L);
        ann.setContentMd("见解里放了图 ![x](/uploads/u2/2026/09/in-insight.png)");
        when(docAnnotationMapper.selectByDoc(11L)).thenReturn(List.of(ann));

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(1, result.get("deletedImages"));
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("u2/2026/09/in-insight.png")));
    }

    @Test
    void deleteUserCascadesDocsVersionsAnnotationsCatalogAndAccount() {
        Doc a = doc(11L);
        Doc b = doc(12L);
        when(docMapper.selectByOwner(2L)).thenReturn(List.of(a, b));
        when(catalogMapper.deleteByOwner(2L)).thenReturn(5);

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(2, result.get("deletedDocs"));
        assertEquals(5, result.get("deletedCatalogNodes"));
        assertEquals("newbie", result.get("username"));

        // 顺序：先清子表再删文档（文档一旦先删，批注/版本就失去定位依据）
        var ordered = inOrder(docVersionMapper, docAnnotationMapper, docMapper);
        ordered.verify(docVersionMapper).deleteByDoc(11L);
        ordered.verify(docAnnotationMapper).deleteByDoc(11L);
        ordered.verify(docMapper).deleteById(11L);
        ordered.verify(docVersionMapper).deleteByDoc(12L);
        ordered.verify(docAnnotationMapper).deleteByDoc(12L);
        ordered.verify(docMapper).deleteById(12L);

        verify(catalogMapper).deleteByOwner(2L);
        verify(userMapper).deleteById(2L);
    }

    @Test
    void deleteUserWithNoContentStillRemovesAccountAndCatalog() {
        when(docMapper.selectByOwner(2L)).thenReturn(List.of());
        when(catalogMapper.deleteByOwner(2L)).thenReturn(3);

        Map<String, Object> result = adminService.deleteUser(ADMIN, 2L);

        assertEquals(0, result.get("deletedDocs"));
        verify(docMapper, never()).deleteById(anyLong());
        verify(catalogMapper).deleteByOwner(2L);
        verify(userMapper).deleteById(2L);
    }

    @Test
    void cannotDeleteAdminAccountOrSelf() {
        BizException e = assertThrows(BizException.class, () -> adminService.deleteUser(ADMIN, 1L));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
        verify(userMapper, never()).deleteById(anyLong());
        verify(catalogMapper, never()).deleteByOwner(anyLong());
    }

    @Test
    void normalUserCannotDeleteAccounts() {
        BizException e = assertThrows(BizException.class, () -> adminService.deleteUser(MEMBER, 2L));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    void cannotDeleteUnknownUser() {
        BizException e = assertThrows(BizException.class, () -> adminService.deleteUser(ADMIN, 404L));

        assertEquals(ErrorCode.NOT_FOUND, e.getCode());
        verify(userMapper, never()).deleteById(anyLong());
    }

    private Doc doc(Long id) {
        Doc d = new Doc();
        d.setId(id);
        return d;
    }
}
