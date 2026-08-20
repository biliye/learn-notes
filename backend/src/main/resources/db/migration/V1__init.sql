-- ============================================================
-- V1: 初始表结构（逐字照抄设计规格 §4，勿改字段名/类型/索引）
-- 已发布迁移不可修改：后续变更只能新增 V3__、V4__ ...
-- ============================================================

-- 用户（单用户，仅一行）
CREATE TABLE `sys_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  `nickname`      VARCHAR(64)  NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类树：node_level 1=大类 2=小方向；根节点 parent_id=0
CREATE TABLE `catalog_node` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `node_level`   TINYINT      NOT NULL,
  `name`         VARCHAR(80)  NOT NULL COMMENT '显示名，如 Java / 函数',
  `slug`         VARCHAR(80)  NOT NULL COMMENT '匹配键，小写 ASCII',
  `remark`       VARCHAR(500) NULL COMMENT '我自己写的注释/说明（R2）',
  `icon`         VARCHAR(40)  NULL,
  `sort_order`   INT          NOT NULL DEFAULT 100,
  `auto_created` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '导入自动创建，待整理（R14）',
  `doc_count`    INT          NOT NULL DEFAULT 0 COMMENT '冗余计数，写时维护',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_parent_slug` (`parent_id`, `slug`),
  KEY `idx_node_parent` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档（当前版本正文内联，读路径单表命中）
CREATE TABLE `doc` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `topic_id`        BIGINT UNSIGNED NOT NULL COMMENT 'catalog_node(node_level=2)',
  `slug`            VARCHAR(120) NOT NULL,
  `title`           VARCHAR(200) NOT NULL,
  `summary`         VARCHAR(500) NULL,
  `tags`            VARCHAR(300) NULL COMMENT '逗号分隔',
  `source_filename` VARCHAR(255) NULL,
  `current_version` INT          NOT NULL DEFAULT 1,
  `content_md`      LONGTEXT     NOT NULL,
  `content_hash`    CHAR(40)     NOT NULL COMMENT 'sha1(content_md)，用于跳过无变更写入',
  `block_count`     INT          NOT NULL DEFAULT 0,
  `word_count`      INT          NOT NULL DEFAULT 0,
  `sort_order`      INT          NOT NULL DEFAULT 100,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_topic_slug` (`topic_id`, `slug`),
  KEY `idx_doc_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 历史版本（只追加）
CREATE TABLE `doc_version` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_id`       BIGINT UNSIGNED NOT NULL,
  `version`      INT          NOT NULL,
  `content_md`   LONGTEXT     NOT NULL,
  `content_hash` CHAR(40)     NOT NULL,
  `change_note`  VARCHAR(255) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ver_doc_version` (`doc_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 个人见解（块级锚点）
CREATE TABLE `doc_annotation` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_id`                BIGINT UNSIGNED NOT NULL,
  `anchor_hash`           CHAR(8)      NOT NULL COMMENT 'D5 hash8',
  `anchor_index`          INT          NOT NULL COMMENT '块序号，0 起',
  `block_snippet`         VARCHAR(300) NOT NULL COMMENT '创建时块文本快照，供 ORPHAN 人工重挂',
  `content_md`            TEXT         NOT NULL COMMENT '见解正文，支持 Markdown',
  `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/STALE/ORPHAN',
  `doc_version_at_create` INT          NOT NULL,
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ann_doc_status` (`doc_id`, `status`),
  KEY `idx_ann_doc_anchor` (`doc_id`, `anchor_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
