-- ============================================================
-- V2: 兜底分类种子数据（R13 兜底路径，长期存在，不可删除）
-- 大类 INBOX / 收件箱 → 小方向 未归类
-- 管理员账号不在此插入：由后端启动时按环境变量创建（密码不进仓库）
-- ============================================================

INSERT INTO `catalog_node` (`parent_id`, `node_level`, `name`, `slug`, `remark`, `icon`, `sort_order`, `auto_created`)
VALUES (0, 1, 'INBOX', 'inbox', '未归类文档的兜底收件箱，导入无法解析分类时落在这里', 'inbox', 0, 1);

INSERT INTO `catalog_node` (`parent_id`, `node_level`, `name`, `slug`, `remark`, `icon`, `sort_order`, `auto_created`)
SELECT `id`, 2, '未归类', 'uncategorized', '导入无法解析分类时落在这里，需人工整理', 'folder', 0, 1
FROM `catalog_node`
WHERE `node_level` = 1 AND `slug` = 'inbox';
