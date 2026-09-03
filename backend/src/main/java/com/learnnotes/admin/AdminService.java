package com.learnnotes.admin;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.admin.dto.AdminDocRow;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SearchUtil;
import com.learnnotes.doc.mapper.DocMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 管理员服务（V3）：查看所有用户的文档与用户列表。仅 ADMIN 角色可调用。
 */
@Service
public class AdminService {

    private final DocMapper docMapper;
    private final SysUserMapper userMapper;
    private final CatalogNodeMapper catalogMapper;

    public AdminService(DocMapper docMapper, SysUserMapper userMapper, CatalogNodeMapper catalogMapper) {
        this.docMapper = docMapper;
        this.userMapper = userMapper;
        this.catalogMapper = catalogMapper;
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

    private void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw BizException.forbidden("仅管理员可访问");
        }
    }
}
