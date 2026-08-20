package com.learnnotes.catalog.service;

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
 * 分类目录树 CRUD（R1–R4、D1、D2）。
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

    public List<CatalogNodeDto> tree() {
        List<CatalogNode> all = mapper.selectAll();
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
    public CatalogNodeDto create(Long parentId, String name, String slug, String remark, String icon, Integer sortOrder) {
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("名称不能为空");
        }
        CatalogNode node = new CatalogNode();
        node.setName(name.trim());
        node.setRemark(remark);
        node.setIcon(icon);
        node.setSortOrder(sortOrder == null ? 100 : sortOrder);
        node.setAutoCreated(false);
        if (parentId == null || parentId == 0) {
            // 大类
            node.setParentId(0L);
            node.setNodeLevel(CatalogNode.LEVEL_CATEGORY);
            node.setSlug(uniqueSlug(0, slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        } else {
            CatalogNode parent = requireById(parentId);
            if (parent.getNodeLevel() != CatalogNode.LEVEL_CATEGORY) {
                throw BizException.badRequest("小方向只能挂在大类之下，不允许三级分类");
            }
            node.setParentId(parentId);
            node.setNodeLevel(CatalogNode.LEVEL_TOPIC);
            node.setSlug(uniqueSlug(parentId, slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        }
        mapper.insert(node);
        return toDto(mapper.selectById(node.getId()));
    }

    @Transactional
    public void update(Long id, String name, String remark, String icon, Integer sortOrder) {
        CatalogNode node = requireById(id);
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
    public void move(Long id, Long parentId, Integer sortOrder) {
        CatalogNode node = requireById(id);
        if (node.getNodeLevel() != CatalogNode.LEVEL_TOPIC) {
            throw BizException.badRequest("只有小方向可以移动");
        }
        CatalogNode parent = requireById(parentId);
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
    public void delete(Long id) {
        CatalogNode node = requireById(id);
        if (isProtected(node)) {
            throw BizException.badRequest("INBOX/未归类 是长期兜底路径，不允许删除");
        }
        if (node.getNodeLevel() == CatalogNode.LEVEL_CATEGORY) {
            int children = mapper.countByParent(id);
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

    public CatalogNode requireById(Long id) {
        if (id == null) {
            throw BizException.badRequest("id 不能为空");
        }
        CatalogNode node = mapper.selectById(id);
        if (node == null) {
            throw BizException.notFound("分类节点不存在：" + id);
        }
        return node;
    }

    public CatalogNode findInbox() {
        return mapper.selectByParentAndSlug(0, SLUG_INBOX);
    }

    public CatalogNode findUncategorized() {
        CatalogNode inbox = findInbox();
        return inbox == null ? null : mapper.selectByParentAndSlug(inbox.getId(), SLUG_UNCATEGORIZED);
    }

    private String uniqueSlug(long parentId, String slug) {
        String base = slug == null || slug.isBlank() ? SlugUtil.slugify("node", "node") : slug;
        if (mapper.selectByParentAndSlug(parentId, base) == null) {
            return base;
        }
        for (int i = 2; i < 100; i++) {
            String candidate = base + "-" + i;
            if (mapper.selectByParentAndSlug(parentId, candidate) == null) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }

    private boolean isProtected(CatalogNode node) {
        return SLUG_INBOX.equals(node.getSlug()) || SLUG_UNCATEGORIZED.equals(node.getSlug());
    }
}
