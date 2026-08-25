-- ============================================================
-- V3: 多用户改造（注册/登录、数据按用户隔离、管理员看全部）
-- 存量单用户部署：原唯一用户即管理员，其数据全部回填到该账号下。
-- 已发布迁移不可修改：后续变更只能新增 V4__、V5__ ...
-- ============================================================

-- 1) sys_user 加角色（ADMIN/USER）；存量唯一用户回填为 ADMIN
ALTER TABLE `sys_user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'ADMIN/USER' AFTER `nickname`;

UPDATE `sys_user`
SET `role` = 'ADMIN'
WHERE `id` = (SELECT `id` FROM (SELECT MIN(`id`) AS `id` FROM `sys_user`) t);

-- 2) doc 加归属用户；存量文档回填给管理员（无管理员时保持 0，见最后清理）
ALTER TABLE `doc`
    ADD COLUMN `owner_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '归属用户' AFTER `id`;

UPDATE `doc`
SET `owner_id` = COALESCE(
        (SELECT `id` FROM (SELECT MIN(`id`) AS `id` FROM `sys_user` WHERE `role` = 'ADMIN') t),
        0);

-- 3) catalog_node 加归属用户；存量节点回填给管理员
ALTER TABLE `catalog_node`
    ADD COLUMN `owner_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '归属用户' AFTER `id`;

UPDATE `catalog_node`
SET `owner_id` = COALESCE(
        (SELECT `id` FROM (SELECT MIN(`id`) AS `id` FROM `sys_user` WHERE `role` = 'ADMIN') t),
        0);

-- 4) 唯一键纳入 owner_id（否则不同用户会撞 slug），并补查询索引
ALTER TABLE `doc`
    DROP INDEX `uk_doc_topic_slug`,
    ADD UNIQUE KEY `uk_doc_owner_topic_slug` (`owner_id`, `topic_id`, `slug`),
    ADD KEY `idx_doc_owner_updated` (`owner_id`, `updated_at`);

ALTER TABLE `catalog_node`
    DROP INDEX `uk_node_parent_slug`,
    ADD UNIQUE KEY `uk_node_owner_parent_slug` (`owner_id`, `parent_id`, `slug`),
    ADD KEY `idx_node_owner_parent` (`owner_id`, `parent_id`, `sort_order`);

-- 5) 清理 V2 种子遗留的 owner_id=0 节点（仅全新部署有；
--    存量部署已被第 3 步回填到管理员，不会误删）。
--    每个用户的默认 INBOX/未归类由注册流程与管理员初始化在运行时按需创建。
DELETE FROM `catalog_node` WHERE `owner_id` = 0;
