-- Pricing Management initial schema (MySQL 8.0.17+, InnoDB, utf8mb4).
-- Run this script after selecting the target database, for example:
--   mysql -u root -p pricing_management_test < db/init.sql
-- Foreign keys are intentionally not created: the documented consistency boundary
-- is enforced by application transactions, row locks, and published-version checks.

CREATE TABLE biz_line (
  id BIGINT NOT NULL,
  biz_code VARCHAR(32) NOT NULL,
  biz_name VARCHAR(64) NOT NULL,
  owner VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  remark VARCHAR(512),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_code (biz_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务线';

CREATE TABLE pricing_scene (
  id BIGINT NOT NULL,
  biz_code VARCHAR(32) NOT NULL,
  scene_code VARCHAR(64) NOT NULL,
  scene_name VARCHAR(128) NOT NULL,
  scene_desc VARCHAR(512),
  factor_scope JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_scene (biz_code, scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价场景';

CREATE TABLE pricing_fee_group (
  id BIGINT NOT NULL,
  group_no VARCHAR(32) NOT NULL,
  group_name VARCHAR(128) NOT NULL,
  biz_code VARCHAR(32) NOT NULL,
  scene_code VARCHAR(64) NOT NULL,
  group_desc VARCHAR(512),
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_no (group_no),
  KEY idx_scene (biz_code, scene_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用组';

CREATE TABLE pricing_fee_item (
  id BIGINT NOT NULL,
  group_id BIGINT NOT NULL,
  fee_code VARCHAR(64) NOT NULL,
  fee_name VARCHAR(128) NOT NULL,
  seq_no INT NOT NULL,
  is_required TINYINT NOT NULL DEFAULT 1,
  fee_desc VARCHAR(512),
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_fee (group_id, fee_code),
  UNIQUE KEY uk_group_seq (group_id, seq_no),
  KEY idx_group_order (group_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组内费用项';

CREATE TABLE pricing_scheme (
  id BIGINT NOT NULL,
  root_scheme_id BIGINT NOT NULL COMMENT '同一方案实例的内部版本链根ID；首版本等于自身ID',
  fee_id BIGINT NOT NULL,
  scheme_name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  content_hash CHAR(64) NULL,
  gray_flag TINYINT NOT NULL DEFAULT 0,
  rollback_from_id BIGINT NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME(3) NULL,
  remark VARCHAR(512),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_root_scheme_start (root_scheme_id, start_time),
  KEY idx_root_scheme (root_scheme_id, start_time),
  KEY idx_fee_status (fee_id, status, start_time, end_time),
  KEY idx_schedule_scan (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价方案时间版本行';

CREATE TABLE pricing_scheme_dimension (
  id BIGINT NOT NULL,
  scheme_id BIGINT NOT NULL,
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  dim_code VARCHAR(64) NOT NULL,
  dim_values JSON NOT NULL,
  match_mode VARCHAR(16) NOT NULL DEFAULT 'IN',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_dim (scheme_id, dim_code),
  KEY idx_time (start_time, end_time),
  KEY idx_dimcode (dim_code),
  KEY idx_dimval_time ((CAST(dim_values AS CHAR(64) ARRAY)), start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案绑定维度';

CREATE TABLE pricing_scheme_factor_value (
  id BIGINT NOT NULL,
  scheme_id BIGINT NOT NULL,
  factor_code VARCHAR(64) NOT NULL,
  value_json JSON NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_factor (scheme_id, factor_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案因子赋值';

CREATE TABLE pricing_scheme_template (
  id BIGINT NOT NULL,
  scheme_id BIGINT NOT NULL,
  template_type VARCHAR(16) NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_version INT NOT NULL,
  slot_bindings JSON NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_type (scheme_id, template_type),
  KEY idx_template (template_code, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案模板引用';

CREATE TABLE pricing_factor (
  id BIGINT NOT NULL,
  factor_code VARCHAR(64) NOT NULL,
  factor_name VARCHAR(128) NOT NULL,
  category VARCHAR(32) NOT NULL,
  value_type VARCHAR(32) NOT NULL,
  unit VARCHAR(16) NULL,
  dict_json JSON NOT NULL,
  validate_rule JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_factor_code (factor_code),
  KEY idx_category (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价因子字典';

CREATE TABLE pricing_template (
  id BIGINT NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  template_type VARCHAR(16) NOT NULL,
  version_no INT NOT NULL,
  content_json JSON NOT NULL,
  pair_code VARCHAR(64) NULL,
  outputs_json JSON NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_ver (template_code, version_no),
  KEY idx_type_status (template_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价模板库';

CREATE TABLE approval_flow (
  id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  group_id BIGINT NOT NULL,
  scheme_ids JSON NOT NULL,
  action VARCHAR(16) NOT NULL,
  operator VARCHAR(64) NOT NULL,
  opinion VARCHAR(512) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT NOT NULL DEFAULT 0,
  row_version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_group (group_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流水';

CREATE TABLE audit_log (
  id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  operator VARCHAR(64) NOT NULL,
  trace_id VARCHAR(64) NULL,
  operate_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id, operate_time),
  KEY idx_biz (biz_type, biz_id, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计'
PARTITION BY RANGE (TO_DAYS(operate_time)) (
  PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
