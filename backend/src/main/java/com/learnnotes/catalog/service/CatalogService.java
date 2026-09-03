package com.learnnotes.catalog.service;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.catalog.dto.CatalogNodeDto;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SlugUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分类目录树 CRUD（R1–R4、D1、D2）。V4 起支持任意深度多级目录：
 * 顶层大类（parent_id=0）记录 max_level（子树允许的最大层级，含大类本身），
 * 文档只挂在"当前没有子目录"的目录（叶目录）上；含文档的目录不能再建子目录。
 * V3 起分类树按用户隔离：普通用户只能读写自己的节点；管理员可读写任意节点。
 */
@Service
public class CatalogService {

    /** 兜底路径：不可删除、改名与移动 */
    public static final String SLUG_INBOX = "inbox";
    public static final String SLUG_UNCATEGORIZED = "uncategorized";

    private static final Comparator<CatalogNodeDto> BY_SORT =
            Comparator.comparingInt(d -> d.getSortOrder() == null ? 100 : d.getSortOrder());

    private final CatalogNodeMapper mapper;

    public CatalogService(CatalogNodeMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- 读 ----------

    public List<CatalogNodeDto> tree(CurrentUser user) {
        List<CatalogNode> all = mapper.selectAll(user.userId());
        Map<Long, CatalogNodeDto> dtoById = new HashMap<>();
        for (CatalogNode node : all) {
            dtoById.put(node.getId(), toDto(node));
        }
        for (CatalogNode node : all) {
            if (node.getParentId() != null && node.getParentId() != 0) {
                CatalogNodeDto parent = dtoById.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dtoById.get(node.getId()));
                }
            }
        }
        // 任意深度：每个有子节点的目录都按其 sort_order 排序
        for (CatalogNodeDto dto : dtoById.values()) {
            if (!dto.getChildren().isEmpty()) {
                dto.getChildren().sort(BY_SORT);
            }
        }
        return dtoById.values().stream()
                .filter(dto -> dto.getParentId() == null || dto.getParentId() == 0)
                .sorted(BY_SORT)
                .collect(Collectors.toList());
    }

    private CatalogNodeDto toDto(CatalogNode node) {
        CatalogNodeDto dto = new CatalogNodeDto();
        dto.setId(node.getId());
        dto.setParentId(node.getParentId());
        dto.setNodeLevel(node.getNodeLevel());
        dto.setMaxLevel(node.getMaxLevel());
        dto.setName(node.getName());
        dto.setSlug(node.getSlug());
        dto.setRemark(node.getRemark());
        dto.setIcon(node.getIcon());
        dto.setSortOrder(node.getSortOrder());
        dto.setDocCount(node.getDocCount());
        dto.setAutoCreated(Boolean.TRUE.equals(node.getAutoCreated()));
        return dto;
    }

    // ---------- 写 ----------

    /** 兼容旧调用：顶层大类按默认两级创建 */
    @Transactional
    public CatalogNodeDto create(CurrentUser user, Long parentId, String name, String slug,
                                 String remark, String icon, Integer sortOrder) {
        return create(user, parentId, name, slug, remark, icon, sortOrder, null);
    }

    /**
     * 新建目录节点。顶层大类（parentId=0/null）需在 maxLevel 中声明该大类允许的目录层级；
     * 子目录层级 = 父目录层级 + 1，不能超过所属大类的 max_level。
     */
    @Transactional
    public CatalogNodeDto create(CurrentUser user, Long parentId, String name, String slug,
                                 String remark, String icon, Integer sortOrder, Integer maxLevel) {
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
            node.setMaxLevel(normalizeMaxLevel(maxLevel));
            node.setSlug(uniqueSlug(user.userId(), 0,
                    slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        } else {
            CatalogNode parent = requireById(parentId, user);
            int childLevel = parent.getNodeLevel() == null ? CatalogNode.LEVEL_TOPIC : parent.getNodeLevel() + 1;
            int topMax = effectiveMax(rootOf(parent));
            if (childLevel > topMax) {
                throw BizException.badRequest("该大类只允许到 " + topMax + " 级目录，不能再往下建子目录");
            }
            requireExpandable(parent, "在含文档的目录下新建子目录");
            node.setParentId(parentId);
            node.setNodeLevel(childLevel);
            node.setMaxLevel(null);
            node.setSlug(uniqueSlug(user.userId(), parentId,
                    slug == null || slug.isBlank() ? SlugUtil.slugify(name, "node") : slug));
        }
        mapper.insert(node);
        return toDto(mapper.selectById(node.getId()));
    }

    /** 兼容旧调用（不调整大类层级） */
    @Transactional
    public void update(CurrentUser user, Long id, String name, String remark, String icon, Integer sortOrder) {
        update(user, id, name, remark, icon, sortOrder, null);
    }

    @Transactional
    public void update(CurrentUser user, Long id, String name, String remark, String icon,
                       Integer sortOrder, Integer maxLevel) {
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
        if (maxLevel != null) {
            if (node.getParentId() == null || node.getParentId() == 0) {
                int level = normalizeMaxLevel(maxLevel);
                int deepest = subtreeDeepest(node.getOwnerId(), id);
                if (deepest > level) {
                    throw BizException.badRequest("该大类下已有 " + deepest + " 级目录，层级不能小于 " + deepest);
                }
                update.setMaxLevel(level);
            } else {
                throw BizException.badRequest("目录层级只能在大类上配置");
            }
        }
        mapper.update(update);
    }

    /**
     * 移动任意非顶层子树。目标不能是自身/自己的后代，不能是含文档的目录；
     * 深度受目标大类 max_level 限制，子树 node_level 会整体重算。
     */
    @Transactional
    public void move(CurrentUser user, Long id, Long parentId, Integer sortOrder) {
        CatalogNode node = requireById(id, user);
        if (isProtected(node)) {
            throw BizException.badRequest("INBOX/未归类 是兜底路径，不允许移动");
        }
        if (node.getParentId() == null || node.getParentId() == 0) {
            throw BizException.badRequest("顶层大类不能移动（层级由该大类自身决定），可在目标大类下新建再迁移文档");
        }
        if (parentId == null || parentId == 0) {
            throw BizException.badRequest("顶层大类不能作为子目录的目标，请选择某个目录");
        }
        CatalogNode target = requireById(parentId, user);
        if (target.getId().equals(node.getId())) {
            throw BizException.badRequest("不能移动到自身目录");
        }
        List<Long> mySubtreeIds = subtreeIds(node.getOwnerId(), node.getId());
        if (mySubtreeIds.contains(target.getId())) {
            throw BizException.badRequest("不能移动到自己的子目录下");
        }
        requireExpandable(target, "把目录移动到含文档的目录下");
        // 深度放不放得下：子树最深级随目标层级整体平移
        int targetTopMax = effectiveMax(rootOf(target));
        int targetBase = (target.getNodeLevel() == null ? CatalogNode.LEVEL_TOPIC : target.getNodeLevel() + 1);
        int delta = targetBase - (node.getNodeLevel() == null ? CatalogNode.LEVEL_TOPIC : node.getNodeLevel());
        int subtreeDeepest = subtreeDeepest(node.getOwnerId(), node.getId());
        if (subtreeDeepest + delta > targetTopMax) {
            throw BizException.badRequest("目标大类的目录层级最大为 " + targetTopMax
                    + " 级，容纳不下该子树（需要 " + (subtreeDeepest + delta) + " 级）");
        }
        // 目标父目录下 slug 冲突（uk_node_owner_parent_slug）
        if (mapper.selectByParentAndSlug(node.getOwnerId(), target.getId(), node.getSlug()) != null) {
            throw BizException.conflict("目标目录下已存在 slug 为 " + node.getSlug() + " 的节点");
        }
        // 整棵子树 node_level 平移（相对于所属大类变深/变浅）
        if (delta != 0) {
            for (Long nid : mySubtreeIds) {
                CatalogNode sub = mapper.selectById(nid);
                CatalogNode upd = new CatalogNode();
                upd.setId(nid);
                upd.setNodeLevel((sub.getNodeLevel() == null ? CatalogNode.LEVEL_TOPIC : sub.getNodeLevel()) + delta);
                mapper.update(upd);
            }
        }
        CatalogNode upd = new CatalogNode();
        upd.setId(id);
        upd.setParentId(parentId);
        upd.setSortOrder(sortOrder);
        mapper.update(upd);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        CatalogNode node = requireById(id, user);
        if (isProtected(node)) {
            throw BizException.badRequest("INBOX/未归类 是长期兜底路径，不允许删除");
        }
        int children = mapper.countByParent(node.getOwnerId(), id);
        if (children > 0) {
            throw BizException.conflict("该目录下还有 " + children + " 个子目录，请先删除或移出");
        }
        if (node.getDocCount() != null && node.getDocCount() > 0) {
            throw BizException.conflict("请先迁移该目录下的 " + node.getDocCount() + " 篇文档");
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

    /** 该节点当前是否还有子目录（文档只允许挂在没有子目录的目录上） */
    public boolean hasChildren(CatalogNode node) {
        return mapper.countByParent(node.getOwnerId(), node.getId()) > 0;
    }

    /** 根→本节点 的完整祖先链（不含节点本身的调用方可做归属校验）。 */
    public List<CatalogNode> pathFromRoot(Long nodeId) {
        LinkedList<CatalogNode> chain = new LinkedList<>();
        CatalogNode cur = nodeId == null ? null : mapper.selectById(nodeId);
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur.getId())) {
            chain.addFirst(cur);
            if (cur.getParentId() == null || cur.getParentId() == 0) {
                break;
            }
            cur = mapper.selectById(cur.getParentId());
        }
        return chain;
    }

    /** 返回 rootId 及其全部后代节点 id（任意深度，含 rootId 自身） */
    public List<Long> subtreeIds(Long ownerId, Long rootId) {
        List<Long> out = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            out.add(id);
            for (CatalogNode child : mapper.selectByParent(ownerId, id)) {
                queue.add(child.getId());
            }
        }
        return out;
    }

    /** 子树中最深的 node_level（含 rootId） */
    public int subtreeDeepest(Long ownerId, Long rootId) {
        int deepest = 1;
        for (Long id : subtreeIds(ownerId, rootId)) {
            CatalogNode n = mapper.selectById(id);
            if (n != null && n.getNodeLevel() != null) {
                deepest = Math.max(deepest, n.getNodeLevel());
            }
        }
        return deepest;
    }

    // ---------- 内部 ----------

    private CatalogNode rootOf(CatalogNode node) {
        CatalogNode cur = node;
        Set<Long> seen = new HashSet<>();
        while (cur.getParentId() != null && cur.getParentId() != 0 && seen.add(cur.getId())) {
            CatalogNode parent = mapper.selectById(cur.getParentId());
            if (parent == null) {
                break;
            }
            cur = parent;
        }
        return cur;
    }

    /** 所属大类的有效 max_level（老数据未配置按两级） */
    private int effectiveMax(CatalogNode top) {
        return top == null || top.getMaxLevel() == null ? CatalogNode.DEFAULT_MAX_LEVEL : top.getMaxLevel();
    }

    private int normalizeMaxLevel(Integer maxLevel) {
        int level = maxLevel == null ? CatalogNode.DEFAULT_MAX_LEVEL : maxLevel;
        if (level < CatalogNode.DEFAULT_MAX_LEVEL || level > CatalogNode.MAX_LEVEL_LIMIT) {
            throw BizException.badRequest("目录层级需在 " + CatalogNode.DEFAULT_MAX_LEVEL
                    + " ~ " + CatalogNode.MAX_LEVEL_LIMIT + " 之间");
        }
        return level;
    }

    /** 含文档的目录不能再建子目录（文档只放叶目录的配套约束） */
    private void requireExpandable(CatalogNode node, String action) {
        if (node.getDocCount() != null && node.getDocCount() > 0) {
            throw BizException.badRequest("该目录下已有 " + node.getDocCount()
                    + " 篇文档，" + action + " 不被允许（请先把文档移出再细分）");
        }
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
        inbox.setMaxLevel(CatalogNode.DEFAULT_MAX_LEVEL);
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
}
