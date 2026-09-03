-- ============================================================
-- V4: 多级目录 —— 大类可配置子树最大层级
-- catalog_node.max_level：仅大类(parent_id=0)有意义，
--   值为该大类子树允许的最大 node_level（含大类本身，2=现状 大类→小方向）。
-- 子节点恒为 NULL（层级由 node_level 表达，深度上限一律以所属大类的 max_level 为准）。
-- 已发布迁移不可修改：后续变更只能新增 V5__、V6__ ...
-- ============================================================

ALTER TABLE `catalog_node`
    ADD COLUMN `max_level` SMALLINT NULL
        COMMENT '仅大类(parent_id=0)有效：子树允许的最大目录层级(含大类)；2=两级(现状)' AFTER `node_level`;

-- 存量顶层大类默认维持两级
UPDATE `catalog_node`
SET `max_level` = 2
WHERE `parent_id` = 0 AND `max_level` IS NULL;
