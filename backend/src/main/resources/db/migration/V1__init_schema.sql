-- V1__init_schema.sql
-- Lab Report Writer 数据库初始化

CREATE DATABASE IF NOT EXISTS labreport DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE labreport;

CREATE TABLE IF NOT EXISTS `user` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username`         VARCHAR(64)  NOT NULL UNIQUE,
    `password`         VARCHAR(256) NOT NULL COMMENT 'BCrypt hash',
    `display_name`     VARCHAR(128),
    `role`             VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
    `login_fail_count` INT          NOT NULL DEFAULT 0,
    `last_login_at`    DATETIME,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project` (
    `id`                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`               BIGINT       NOT NULL,
    `name`                  VARCHAR(255) NOT NULL,
    `description`           VARCHAR(1024),
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/GENERATING/COMPLETED/FAILED',
    `output_dir`            VARCHAR(512),
    `config_json`           JSON COMMENT 'ReportConfig序列化',
    `personal_info_json`    JSON COMMENT '个人信息',
    `classified_files_json` JSON COMMENT 'FileClassifier结果',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `experiment_task` (
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `title`             VARCHAR(255) NOT NULL DEFAULT '',
    `objectives`        TEXT,
    `principles`        TEXT,
    `required_tools`    JSON COMMENT '工具/材料列表',
    `procedure_steps`   JSON COMMENT '步骤列表',
    `required_outputs`  JSON COMMENT '要求输出列表',
    `required_analysis` TEXT,
    `submission_format` VARCHAR(255),
    `raw_task_text`     MEDIUMTEXT,
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_template` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `project_id`      BIGINT NOT NULL,
    `file_path`       VARCHAR(512),
    `display_name`    VARCHAR(255),
    `variables`       JSON COMMENT '变量名列表',
    `variable_values` JSON COMMENT '变量值映射',
    `sections`        JSON COMMENT '章节列表',
    `cover_fields`    JSON COMMENT '封面字段',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_record` (
    `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
    `project_id`        BIGINT       NOT NULL,
    `task_id`           VARCHAR(64)  COMMENT '异步任务UUID',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    `output_file_path`  VARCHAR(512),
    `output_file_size`  BIGINT,
    `error_message`     TEXT,
    `progress_phase`    INT          DEFAULT 0 COMMENT '当前阶段1-7',
    `phase_name`        VARCHAR(64)  COMMENT '阶段名称',
    `evidence_summary`  TEXT,
    `validation_json`   JSON COMMENT '验证报告',
    `generated_at`      DATETIME,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id),
    INDEX idx_task_id (task_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uploaded_file` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `project_id`      BIGINT       NOT NULL,
    `original_name`   VARCHAR(512) NOT NULL,
    `stored_name`     VARCHAR(128) NOT NULL COMMENT 'UUID存储名',
    `file_path`       VARCHAR(512) NOT NULL,
    `file_size`       BIGINT,
    `file_type`       VARCHAR(64)  COMMENT 'SOURCE_CODE/TEMPLATE/TASK_BOOK/OTHER',
    `language`        VARCHAR(32)  COMMENT 'java/python/cpp/c/etc',
    `mime_type`       VARCHAR(128),
    `md5_hash`        VARCHAR(64),
    `classified_role` VARCHAR(64)  COMMENT 'FileClassifier分类角色',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `provider`    VARCHAR(32)  NOT NULL COMMENT 'GEMINI/DEEPSEEK/QWEN',
    `role`        VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
    `content`     MEDIUMTEXT   NOT NULL,
    `token_count` INT,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project_provider (project_id, provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `system_config` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key`   VARCHAR(128) NOT NULL UNIQUE,
    `config_value` TEXT,
    `description`  VARCHAR(512),
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
