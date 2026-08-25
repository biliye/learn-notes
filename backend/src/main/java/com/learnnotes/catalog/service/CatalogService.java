package com.learnnotes.catalog.service;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.catalog.dto.CatalogNodeDto;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SlugUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类目录树 CRUD（R1–R4、D1、D2）。V3 起分类树按用户隔离：
 * 普通用户只能读写自己的节点；管理员可读写任意节点（看全部文档用）。
 */
@Service
public class CatalogService {

    /** 兜底路径：不可删除与改名 */
    public static final String SLUG_INBOX = "inbox";
    public static final String SLUG_UNCATEGORIZED = "uncategorized";

    private final CatalogNodeMapper mapper;

    public CatalogService(CatalogNodeMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- 读 ----------

    public List<CatalogNodeDto> tree(CurrentUser user) {
        List<CatalogNode> all = mapper.selectAll(user.userId());
        Map<Long, CatalogNodeDto> dtoById = all.stream()
                .collect(Collectors.toMap(CatalogNode::getId, this::toDto));
        for (CatalogNode node : all) {
            if (node.getParentId() != null && node.getParentId() != 0) {
                CatalogNodeDto parent = dtoById.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dtoById.get(node.getId()));
                }
            }
        }
        return dtoById.values().stream()
                .filter(dto -> dto.getParentId() == null || dto.getParentId() == 0)
                .sorted(Comparator.comparingInt(d -> d.getSortOrder() == null ? 100 : d.getSortOrder()))
                .peek(d -> d.getChildren().sort(
                        Comparator.comparingInt(c -> c.getSortOrder() == null ? 100 : c.getSortOrder())))
                .collect(Collectors.toList());
    }

    private CatalogNodeDto toDto(CatalogNode node) {
        CatalogNodeDto dto = new CatalogNodeDto();
        dto.setId(node.getId());
        dto.setName(node.getName());
        dto.setSlug(node.getSlug());
        dto.setRemark(node.getRemark());
        dto.setIcon(node.getIcon());
        dto.setSortOrder(node.getSortOrder());
        dto.setDocCount(node.getDocCount());
        dto.setAutoCreated(Boolean.TRUE.equals(node.getAutoCreated()));
        dto.setParentId(node.getParentId());
        return dto;
    }

    // ---------- 写 ----------

    @Transactional
    public CatalogNodeDto create(CurrentUser user, Long parentId, String name, String slug,
                                 String remark, String icon, Integer sortOrder) {
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("名称不能为空");
        }
        CatalogNode node = new CatalogNode();
        node.setOwnerId(user.userId());
        node.setName(name.trim());
        node.setRemark(remark);
        node.setIcon(icon);
        node.setSortOrder(sortOrder == null ? 100 : sortOrder);
        node.setAutoCreated(false);
        if (parentId == null || parentId == 0) {
            // 大类
            node.setParentId(0L);
            node.setNodeLevel(CatalogNode.LEVEL_CATEGORY);
            node.setSlug(uniqueSlug(user.userId(), 0,
                    slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        } else {
            CatalogNode parent = requireById(parentId, user);
            if (parent.getNodeLevel() != CatalogNode.LEVEL_CATEGORY) {
                throw BizException.badRequest("小方向只能挂在大类之下，不允许三级分类");
            }
            node.setParentId(parentId);
            node.setNodeLevel(CatalogNode.LEVEL_TOPIC);
            node.setSlug(uniqueSlug(user.userId(), parentId,
                    slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        }
        mapper.insert(node);
        return toDto(mapper.selectById(node.getId()));
    }

    @Transactional
    public void update(CurrentUser user, Long id, String name, String remark, String icon, Integer sortOrder) {
        CatalogNode node = requireById(id, user);
        if (isProtected(node)) {
            throw BizException.badRequest("INBOX/未归类 是兜底路径，不允许改名");
        }
        CatalogNode update = new CatalogNode();
        update.setId(id);
        update.setName(name);
        update.setRemark(remark);
        update.setIcon(icon);
        update.setSortOrder(sortOrder);
        mapper.update(update);
    }

    @Transactional
    public void move(CurrentUser user, Long id, Long parentId, Integer sortOrder) {
        CatalogNode node = requireById(id, user);
        if (node.getNodeLevel() != CatalogNode.LEVEL_TOPIC) {
            throw BizException.badRequest("只有小方向可以移动");
        }
        CatalogNode parent = requireById(parentId, user);
        if (parent.getNodeLevel() != CatalogNode.LEVEL_CATEGORY) {
            throw BizException.badRequest("小方向只能移动到某个大类之下");
        }
        CatalogNode update = new CatalogNode();
        update.setId(id);
        update.setParentId(parentId);
        update.setSortOrder(sortOrder);
        mapper.update(update);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        CatalogNode node = requireById(id, user);
        if (isProtected(node)) {
            throw BizException.badRequest("INBOX/未归类 是长期兜底路径，不允许删除");
        }
        if (node.getNodeLevel() == CatalogNode.LEVEL_CATEGORY) {
            int children = mapper.countByParent(user.userId(), id);
            if (children > 0) {
                throw BizException.conflict("该大类下还有 " + children + " 个小方向，请先迁移或删除");
            }
        } else {
            if (node.getDocCount() != null && node.getDocCount() > 0) {
                throw BizException.conflict("请先迁移该方向下的 " + node.getDocCount() + " 篇文档");
            }
        }
        mapper.deleteById(id);
    }

    // ---------- 供其他模块调用 ----------

    /** 文档模块在增删移文档时维护 doc_count（T07/T08） */
    public void incrDocCount(Long id, int delta) {
        if (id != null) {
            mapper.incrDocCount(id, delta);
        }
    }

    /**
     * 按 id 取节点并校验归属：本人或管理员可访问，否则 403。
     * 管理员可读任意用户的节点（文档详情/面包屑需要跨用户）。
     */
    public CatalogNode requireById(Long id, CurrentUser user) {
        if (id == null) {
            throw BizException.badRequest("id 不能为空");
        }
        CatalogNode node = mapper.selectById(id);
        if (node == null) {
            throw BizException.notFound("分类节点不存在：" + id);
        }
        if (!user.isAdmin() && !node.getOwnerId().equals(user.userId())) {
            throw BizException.forbidden("无权访问该分类");
        }
        return node;
    }

    /** 新用户注册 / 管理员初始化时创建默认 INBOX 兜底树（幂等）。 */
    @Transactional
    public void ensureDefaults(Long userId) {
        if (findInbox(userId) != null) {
            return;
        }
        CatalogNode inbox = new CatalogNode();
        inbox.setOwnerId(userId);
        inbox.setParentId(0L);
        inbox.setNodeLevel(CatalogNode.LEVEL_CATEGORY);
        inbox.setName("INBOX");
        inbox.setSlug(SLUG_INBOX);
        inbox.setSortOrder(0);
        inbox.setAutoCreated(true);
        mapper.insert(inbox);

        CatalogNode uncategorized = new CatalogNode();
        uncategorized.setOwnerId(userId);
        uncategorized.setParentId(inbox.getId());
        uncategorized.setNodeLevel(CatalogNode.LEVEL_TOPIC);
        uncategorized.setName("未归类");
        uncategorized.setSlug(SLUG_UNCATEGORIZED);
        uncategorized.setSortOrder(0);
        uncategorized.setAutoCreated(true);
        mapper.insert(uncategorized);
    }

    public CatalogNode findInbox(Long userId) {
        return mapper.selectByParentAndSlug(userId, 0, SLUG_INBOX);
    }

    public CatalogNode findUncategorized(Long userId) {
        CatalogNode inbox = findInbox(userId);
        return inbox == null ? null : mapper.selectByParentAndSlug(userId, inbox.getId(), SLUG_UNCATEGORIZED);
    }

    private String uniqueSlug(Long ownerId, long parentId, String slug) {
        String base = slug == null || slug.isBlank() ? SlugUtil.slugify("node", "node") : slug;
        if (mapper.selectByParentAndSlug(ownerId, parentId, base) == null) {
            return base;
        }
        for (int i = 2; i < 100; i++) {
            String candidate = base + "-" + i;
            if (mapper.selectByParentAndSlug(ownerId, parentId, candidate) == null) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }

    private boolean isProtected(CatalogNode node) {
        return SLUG_INBOX.equals(node.getSlug()) || SLUG_UNCATEGORIZED.equals(node.getSlug());
    }
}
