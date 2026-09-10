package com.learnnotes.admin;

import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.AuthService;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.auth.SysUser;
import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.admin.dto.AdminDocRow;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SearchUtil;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.mapper.DocVersionMapper;
import com.learnnotes.uploads.service.UploadCleanupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员服务（V3）：查看所有用户的文档、用户列表，以及建号/改密/删号。仅 ADMIN 角色可调用。
 * 改密与删号只作用于普通用户账号（见 requireManagedUser）。
 */
@Service
public class AdminService {

    private final DocMapper docMapper;
    private final SysUserMapper userMapper;
    private final CatalogNodeMapper catalogMapper;
    private final AuthService authService;
    private final DocVersionMapper docVersionMapper;
    private final DocAnnotationMapper docAnnotationMapper;
    private final UploadCleanupService uploadCleanup;

    public AdminService(DocMapper docMapper, SysUserMapper userMapper, CatalogNodeMapper catalogMapper,
                        AuthService authService, DocVersionMapper docVersionMapper,
                        DocAnnotationMapper docAnnotationMapper, UploadCleanupService uploadCleanup) {
        this.docMapper = docMapper;
        this.userMapper = userMapper;
        this.catalogMapper = catalogMapper;
        this.authService = authService;
        this.docVersionMapper = docVersionMapper;
        this.docAnnotationMapper = docAnnotationMapper;
        this.uploadCleanup = uploadCleanup;
    }

    public Map<String, Object> listDocs(CurrentUser user, String keyword, int page, int size) {
        requireAdmin(user);
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        String kw = null;
        if (keyword != null && !keyword.isBlank()) {
            kw = "%" + SearchUtil.escapeLike(keyword.trim()) + "%";
        }
        List<AdminDocRow> rows = docMapper.selectAdminPage(kw, (page - 1) * size, size);
        fillCategoryPath(rows);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", docMapper.countAdminPage(kw));
        result.put("page", page);
        result.put("size", size);
        result.put("items", rows);
        return result;
    }

    /**
     * 补全分类完整路径：categoryName = 该目录 根→…→父目录 链（"A / B / C"），
     * topicName = 文档所在叶目录名。
     */
    private void fillCategoryPath(List<AdminDocRow> rows) {
        for (AdminDocRow row : rows) {
            if (row.getTopicId() == null) {
                continue;
            }
            LinkedList<String> names = new LinkedList<>();
            CatalogNode cur = catalogMapper.selectById(row.getTopicId());
            java.util.Set<Long> seen = new java.util.HashSet<>();
            while (cur != null && seen.add(cur.getId())) {
                names.addFirst(cur.getName());
                if (cur.getParentId() == null || cur.getParentId() == 0) {
                    break;
                }
                cur = catalogMapper.selectById(cur.getParentId());
            }
            if (names.size() >= 1) {
                row.setTopicName(names.removeLast());
            }
            row.setCategoryName(String.join(" / ", names));
        }
    }

    public Map<String, Object> listUsers(CurrentUser user) {
        requireAdmin(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", userMapper.selectAllWithDocCount());
        return result;
    }

    /** 建号（自助注册已关闭，这是唯一创建账号的入口） */
    public Map<String, Object> createUser(CurrentUser user, String username, String password,
                                          String nickname, String role) {
        requireAdmin(user);
        return authService.createUser(username, password, nickname, role);
    }

    /** 重置普通用户口令：不需要旧密码，供对方忘记密码时兜底 */
    public Map<String, Object> resetUserPassword(CurrentUser user, Long userId, String newPassword) {
        requireAdmin(user);
        return authService.resetPassword(requireManagedUser(userId), newPassword);
    }

    /**
     * 删除普通用户账号，连带清掉其全部数据（文档 → 版本/批注 → 分类树 → 账号本身 → 磁盘图片）。
     * 表间没有外键，漏删就会留下孤儿行，所以这里显式按序清理；整体事务，任一步失败全部回滚。
     * 图片：本人 u{id}/ 目录整目录清（目录独占，碰不到别人）；本人引用的**老式**共享图另有归属判定，
     * 只有全站再无他人引用时才删——这是"删一个人不影响别人"的落点。
     */
    @Transactional
    public Map<String, Object> deleteUser(CurrentUser user, Long userId) {
        requireAdmin(user);
        SysUser target = requireManagedUser(userId);

        // 图片引用必须在删行之前收集，删完就查不到了
        Set<String> uploadRefs = new LinkedHashSet<>();
        int docs = 0;
        for (Doc doc : docMapper.selectByOwner(userId)) {
            uploadRefs.addAll(uploadCleanup.collectRefs(doc.getContentMd()));
            for (DocAnnotation ann : docAnnotationMapper.selectByDoc(doc.getId())) {
                uploadRefs.addAll(uploadCleanup.collectRefs(ann.getBlockSnippet(), ann.getContentMd()));
            }
            docVersionMapper.deleteByDoc(doc.getId());
            docAnnotationMapper.deleteByDoc(doc.getId());
            docMapper.deleteById(doc.getId());
            docs++;
        }
        int nodes = catalogMapper.deleteByOwner(userId);
        userMapper.deleteById(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("username", target.getUsername());
        result.put("deletedDocs", docs);
        result.put("deletedCatalogNodes", nodes);
        result.put("deletedImages", 0);
        result.put("deletedLegacyImages", 0);

        // 磁盘清理放事务提交后：回滚了就不该动文件。响应体在提交之后才序列化，所以计数能带上。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupUserUploads(userId, uploadRefs, result);
                }
            });
        } else {
            cleanupUserUploads(userId, uploadRefs, result);
        }
        return result;
    }

    private void cleanupUserUploads(long userId, Set<String> uploadRefs, Map<String, Object> result) {
        result.put("deletedImages", uploadCleanup.purgeOwner(userId));
        result.put("deletedLegacyImages", uploadCleanup.deleteLegacyRefsAfterOwnerDeleted(userId, uploadRefs));
    }

    /**
     * 取可被管理的目标账号：必须是普通用户。
     * 管理员账号不可被改密/删除——既防管理员之间互相夺号，也天然堵住「把自己删了」的自锁死路。
     */
    private SysUser requireManagedUser(Long userId) {
        if (userId == null) {
            throw BizException.badRequest("缺少用户 id");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }
        if (target.isAdmin()) {
            throw BizException.forbidden("只能管理普通用户账号");
        }
        return target;
    }

    /** 权限闸门：管理员接口与维护任务共用 */
    public void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw BizException.forbidden("仅管理员可访问");
        }
    }
}
