package com.learnnotes.admin;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SearchUtil;
import com.learnnotes.doc.mapper.DocMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员服务（V3）：查看所有用户的文档与用户列表。仅 ADMIN 角色可调用。
 */
@Service
public class AdminService {

    private final DocMapper docMapper;
    private final SysUserMapper userMapper;

    public AdminService(DocMapper docMapper, SysUserMapper userMapper) {
        this.docMapper = docMapper;
        this.userMapper = userMapper;
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", docMapper.countAdminPage(kw));
        result.put("page", page);
        result.put("size", size);
        result.put("items", docMapper.selectAdminPage(kw, (page - 1) * size, size));
        return result;
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
