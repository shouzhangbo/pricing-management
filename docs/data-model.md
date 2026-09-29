# 详细设计 · 数据模型（Data Model）

> 上游文档：`architecture.md`（v2.5）
> **v3.0 模型重构**：①层级插入「费用组→费用」；②方案更名 `pricing_scheme`，版本机制由「Bundle 冻结」改为「**start/end 时间区间版本化**」；③删除 `pricing_plan_version`、`publish_record`、`consumer_state_report`、`consistency_diff`、`local_message`。

---

## 0. 文档信息

| 项 | 内容 |
|----|------|
| 文档状态 | ✅ v3.6 已同步 v2.5 口径 |
| 数据库 | MySQL 8.0，InnoDB，utf8mb4，单库不分片 |

### 版本变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-09-24 | 计价系统模型（分库分表/流水/快照）|
| v2.0 | 2026-09-24 | 管理平台模型（方案/版本 Bundle/双模板/核查）|
| v3.0 | 2026-09-24 | **按评审决策重构**：新增费用组/费用层；pricing_plan→pricing_scheme；version_no+Bundle 改为 start/end 时间版本；删除 Bundle 版本表、发布记录表、下游上报/差异表、本地消息表；容量与一致性机制重算 |
| v3.1 | 2026-09-24 | 按决策 D5 修正费用:方案语义 = **1:N 实例并存、各自绑维度**：I1 重定域到实例级；新增 **I4 费用级维度互斥不变式**与命中集合交集校验算法；发布锁升级为费用级；时点查询改为多实例+维度消歧 |
| v3.2 | 2026-09-24 | 确认 D6：不加"允许重叠按规则决胜"柔性模式，冲突一律拒绝发布；I1/I4 校验改为**闭旧+转正后的事务内终态复核**（支撑"默认实例 NOT_IN 缩集 + 新增专属实例"同组原子变更）；补"默认+专属"维度表达范式 |
| v3.3 | 2026-09-24 | 评审点全部关闭（决策 D7：剩余口径按当前默认设计执行，不再单独讨论），文档定稿；启动详细设计收尾 |
| v3.4 | 2026-09-24 | **按 D8/D9/D10 同步**：发布锁（Redis fee 级）从锁步与 §6.3 防线描述中删除，并发全由 DB 行锁承担；afterCommit 去清缓存；容量读压力口径改为直查 DB。**表结构零变更**：D10 新功能（批量导入导出/维度绑定管理/沙盒试算）全部复用既有 12 表，不新增表/列 |
| v3.5 | 2026-09-24 | 维度绑定表新增**联合索引 `idx_dimval_time`**（维度值多值索引 + start_time/end_time，MySQL 8.0.17+），支撑"维度点×时点反查生效实例"：hit 消歧、维度绑定视图、冲突预检的共同数据地基 |
| v3.6 | 2026-09-29 | 同步分发契约修正：hit 前置检索使用业务/费用组/费用编码三元作用域，避免将仅组内唯一的 `fee_code` 当作全局键。 |

---

## 1. 核心层级模型（对齐功能构想）

```
业务 biz_line
 └─ 定价场景 pricing_scene                    「什么业务情形」
     └─ 费用组 pricing_fee_group              「一次费用展示的整体包」── 管理端以组为单位
         └─ 费用 pricing_fee_item             组内 N 个费用: 名称/编码/顺序/是否必填
             └─ 定价方案 pricing_scheme       每个费用可有 N 个方案实例并存(各绑不同维度);
                 │                            任意(费用×维度点×时点)唯一命中一个生效版本
                 ├─ 绑定维度 pricing_scheme_dimension   生效范围: 城市/网格/商家分组... ★时间版本(决定实例归属)
                 ├─ 因子赋值 pricing_scheme_factor_value      引用平台级因子字典
                 └─ 模板引用 pricing_scheme_template            CALC + DISPLAY 成对锁定(版本行内)
平台级字典: 因子 pricing_factor · 模板库 pricing_template(自身也版本化, 内容不可变)
流程审计:   审批流水 approval_flow · 操作审计 audit_log(月分区)
```

### 1.1 时间版本化（本版核心机制，取代 version_no + Bundle）

每个方案（前后端交互键为版本行 `schemeId`）由**多行版本区间**表达历史；同一实例的版本链仅以内部 `root_scheme_id` 关联。

```
pricing_scheme 行模型:
  [D1 ──────── D2)  版本行A  PUBLISHED   ← 历史(已闭合)
  [D2 ──────── ∞ )  版本行B  PUBLISHED   ← 当前生效
  (无区间)          版本行C  DRAFT       ← 编辑中, 未发布

不变式(发布事务强制):
  I1(实例时间线唯一) 同一 root_scheme_id 的 PUBLISHED 区间互不重叠 → 任一时刻一个实例至多一个生效版本
  I4(费用级维度互斥) 同一 fee_id 任意时点并存的各生效实例, 其命中维度集合两两不相交
                     (校验算法见 §6.2; 否则同一维度点会命中多份配置 → 定价不唯一)
  I2 新版本生效即闭合同实例旧版本 end_time(=新版本 start_time), 同事务完成
  I3 start/end 一经发布不可改; 唯一允许的 UPDATE 是"闭合 end"(且只能向前闭合)
推论: (fee_id × 维度点 × 时点) → 唯一生效方案 → 下游计费无需优先级、无需决胜
```

**三个附带收益**：
1. **定时生效零依赖 job**：预发布行带未来 start_time，时间到达自然生效——不存在"job 没跑导致新旧价不一致"；
2. **时点回放即审计**：`WHERE status='PUBLISHED' AND start_time<=:t AND (end_time>:t OR end_time IS NULL)` 可重建任意历史时刻的配置，取代 Bundle 快照的存证职能；
3. 子表（维度）冗余携带 start/end，可按时间直接圈定，无需先解析版本行。

### 1.2 编辑态 vs 生效态

| | 载体 | 可变性 |
|--|------|--------|
| 编辑态 | DRAFT 版本行 + 子表行（维度/因子/模板引用） | 可反复修改（编辑锁+乐观锁） |
| 生效态 | PUBLISHED 版本行 + 子表行 | 区间内不可变（仅允许闭合 end）|
| 下发内容 | 按版本行**时点装配**（子表 join），计算 `content_hash` | 装配结果只读 |

---

## 2. ER 总览与表清单

```
biz_line 1─N pricing_scene 1─N pricing_fee_group 1─N pricing_fee_item 1─N pricing_scheme(版本行)
                                                                    ├─N pricing_scheme_dimension
                                                                    ├─N pricing_scheme_factor_value
                                                                    └─1:1×2 pricing_scheme_template(CALC/DISPLAY)
pricing_factor(字典) ◀─引用─ factor_value      pricing_template(库,版本不可变) ◀─引用─ scheme_template
approval_flow / audit_log (横切)
```

共 **12 张表**：主数据 4（业务/场景/费用组/费用）+ 方案域 5（方案/维度/因子赋值/模板引用/模板库）+ 字典 1 + 流程审计 2。

公共字段规约（所有表含，DDL 中省略写法 `-- +公共`）：

```sql
id           BIGINT   NOT NULL COMMENT '雪花ID',
create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
is_deleted   TINYINT  NOT NULL DEFAULT 0,
row_version  INT      NOT NULL DEFAULT 0 COMMENT '行乐观锁'
```

---

## 3. 主数据域 DDL

### 3.1 biz_line 业务 / 3.2 pricing_scene 定价场景

（结构同 v2.0，注释更新，略作精简）

```sql
CREATE TABLE biz_line (
  id BIGINT NOT NULL,
  biz_code  VARCHAR(32) NOT NULL, biz_name VARCHAR(64) NOT NULL,
  owner     VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, remark VARCHAR(512),
  -- +公共
  PRIMARY KEY (id), UNIQUE KEY uk_biz_code (biz_code)
) COMMENT '业务线';

CREATE TABLE pricing_scene (
  id BIGINT NOT NULL,
  biz_code   VARCHAR(32) NOT NULL, scene_code VARCHAR(64) NOT NULL,
  scene_name VARCHAR(128) NOT NULL, scene_desc VARCHAR(512),
  factor_scope JSON NOT NULL COMMENT '场景可用因子白名单(约束组内所有方案)',
  status  VARCHAR(16) NOT NULL,
  -- +公共
  PRIMARY KEY (id), UNIQUE KEY uk_biz_scene (biz_code, scene_code)
) COMMENT '定价场景';
```

> v2.0 的 `quote_mode(SINGLE/MULTI)` 移除：由两层约束取代——实例级 I1（任一时刻至多一个生效版本）+ 费用级 I4（并存实例命中维度两两互斥），合取后任意"费用×维度点×时点"唯一命中一份配置（D5）。

### 3.3 pricing_fee_group 费用组 ★新增

```sql
CREATE TABLE pricing_fee_group (
  id BIGINT NOT NULL,
  group_no   VARCHAR(32)  NOT NULL COMMENT '费用组编号(对外稳定键)',
  group_name VARCHAR(128) NOT NULL,
  biz_code   VARCHAR(32)  NOT NULL,
  scene_code VARCHAR(64)  NOT NULL COMMENT '所属场景',
  group_desc VARCHAR(512),
  status     VARCHAR(16)  NOT NULL COMMENT 'ENABLED/DISABLED',
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_no (group_no),
  KEY idx_scene (biz_code, scene_code, status)
) COMMENT '费用组: 管理端配置与发布的基本单元(组内所有费用一并配置、一并提审、整组原子发布)';
```

### 3.4 pricing_fee_item 费用 ★新增

```sql
CREATE TABLE pricing_fee_item (
  id BIGINT NOT NULL,
  group_id   BIGINT      NOT NULL,
  fee_code   VARCHAR(64) NOT NULL COMMENT '费用编码(下游按此识别费用项)',
  fee_name   VARCHAR(128) NOT NULL COMMENT '费用名称',
  seq_no     INT         NOT NULL COMMENT '顺序: 展示与计算明细的排列序',
  is_required TINYINT    NOT NULL DEFAULT 1 COMMENT '是否必填: 1=方案必配且必产出费用项; 0=可未配置/可为0',
  fee_desc   VARCHAR(512),
  status     VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED',
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_fee (group_id, fee_code),
  UNIQUE KEY uk_group_seq (group_id, seq_no) COMMENT '顺序不重复, 保证组内明细序稳定',
  KEY idx_group_order (group_id, seq_no)
) COMMENT '费用(组内费用定义; 你要求的四属性: 名称fee_name/编码fee_code/顺序seq_no/必填is_required)';
```

**组内配置完整性校验**（提审时强制）：所有 `is_required=1` 的费用必须已配齐方案（维度+因子+双模板通过校验）；非必填费用允许无生效方案。

---

## 4. 方案域 DDL（时间版本化核心）

### 4.1 pricing_scheme 定价方案（版本行）

```sql
CREATE TABLE pricing_scheme (
  id BIGINT NOT NULL,
  root_scheme_id BIGINT NOT NULL COMMENT '内部版本链根ID；首版本等于自身ID，不对前端暴露',
  fee_id      BIGINT      NOT NULL COMMENT '所属费用(费用:实例=1:N并存,各绑不同维度; 同费用生效实例受I4互斥约束)',
  scheme_name VARCHAR(128) NOT NULL,
  status      VARCHAR(16) NOT NULL COMMENT 'DRAFT草稿/PENDING_APPROVAL审批中/PUBLISHED已发布(区间定生效)/CANCELLED已作废',
  start_time  DATETIME(3) NULL COMMENT '版本生效时间; 发布时写入(支持未来时间=定时发布); DRAFT为NULL',
  end_time    DATETIME(3) NULL COMMENT '版本失效时间; NULL=无限期, 被新版本闭合时写入; DRAFT为NULL',
  content_hash CHAR(64) NULL COMMENT '发布时计算: 该版本全量内容(维度+因子赋值+双模板含槽绑定)的canonical序列化SHA-256',
  gray_flag   TINYINT NOT NULL DEFAULT 0 COMMENT '灰度标记(下游自主灰度消费)',
  rollback_from_id BIGINT NULL COMMENT '若为回滚产生: 来源历史版本行id',
  published_by VARCHAR(64) NULL, published_at DATETIME(3) NULL,
  remark VARCHAR(512),
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_root_scheme_start (root_scheme_id, start_time),
  KEY idx_root_scheme (root_scheme_id, start_time),
  KEY idx_fee_status (fee_id, status, start_time, end_time) COMMENT '当前/时点生效查询主路径',
  KEY idx_schedule_scan (status, start_time) COMMENT '审批超时/作废清理等辅助job'
) COMMENT '定价方案·时间版本行: 一行=一个时间区间版本(取代version_no+Bundle)';
```

> 说明：一个费用下可建**多个方案实例**（如杭州实例、上海实例）；首版本的 `root_scheme_id` 等于其 `id`，后续版本继承该内部根 ID。前后端只传每个版本行的 `schemeId`，业务沟通使用 `schemeName`，不再存在方案稳定编号。维度不直接打在费用上，而是由每个实例的 dimension 行声明归属范围。**"费用+具体维度点"（如某城市）→ 唯一命中一个实例的当前生效版本**。下游与审计以 `schemeId + start_time(+ content_hash)` 定位版本。

### 4.2 pricing_scheme_dimension 方案绑定维度（时间版本化）

```sql
CREATE TABLE pricing_scheme_dimension (
  id BIGINT NOT NULL,
  scheme_id  BIGINT      NOT NULL COMMENT '版本行id(行随版本走)',
  start_time DATETIME(3) NULL COMMENT '冗余自主方案行(★按决策与方案同用时间标识; 支持按时间直接圈定)',
  end_time   DATETIME(3) NULL,
  dim_code   VARCHAR(64) NOT NULL COMMENT '维度: CITY/GRID/MERCHANT_GROUP/CUSTOMER_LEVEL/...',
  dim_values JSON NOT NULL COMMENT '命中值集 ["0571","021"]',
  match_mode VARCHAR(16) NOT NULL DEFAULT 'IN' COMMENT 'IN/NOT_IN',
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_dim (scheme_id, dim_code),
  KEY idx_time (start_time, end_time),
  KEY idx_dimcode (dim_code) COMMENT '运营视角: 按维度反查哪些方案在何时期生效; 也是I4互斥校验数据源',
  KEY idx_dimval_time ((CAST(dim_values AS CHAR(64) ARRAY)), start_time, end_time) COMMENT '★维度值+时间区间联合(8.0.17+ 多值索引): 维度点×时点反查生效实例, 加速 hit 消歧/维度绑定视图/冲突预检'
) COMMENT '方案生效维度(多维度AND; 发布后行不可改; 同费用实例间的维度集合受I4两两不相交约束)';
```

> **`idx_dimval_time` 使用说明（v3.5）**：查询套路 = `WHERE JSON_CONTAINS(dim_values, JSON_QUOTE(:dimValue)) AND start_time <= :t AND (:t < end_time OR end_time IS NULL) AND dim_code = :code`，多值索引承担"维度值→候选行"的检索，时间区间在索引内二次过滤；NOT_IN 行命中该维度值的补集不在索引结果内，需按 `dim_code` 另取少量候选行在内存按补集语义判定（单费用维度行量小，可接受）。典型消费方：`hit(bizCode, groupNo, feeCode, dimCtx, at)` 前置检索、维度绑定覆盖图、同组冲突预检、区间巡检 job。

### 4.3 pricing_scheme_factor_value 因子赋值

```sql
CREATE TABLE pricing_scheme_factor_value (
  id BIGINT NOT NULL,
  scheme_id BIGINT NOT NULL,
  factor_code VARCHAR(64) NOT NULL,
  value_json  JSON NOT NULL COMMENT '按因子value_type赋值(NUMBER/ENUM/RANGE_TABLE/COEFF_TABLE)',
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_factor (scheme_id, factor_code)
) COMMENT '方案因子赋值(挂版本行; 受场景factor_scope+因子validate_rule双重校验)';
```

### 4.4 pricing_scheme_template 方案模板引用（双模板成对）

```sql
CREATE TABLE pricing_scheme_template (
  id BIGINT NOT NULL,
  scheme_id BIGINT NOT NULL,
  template_type    VARCHAR(16) NOT NULL COMMENT 'CALC/DISPLAY',
  template_code    VARCHAR(64) NOT NULL,
  template_version INT NOT NULL COMMENT '锁定到模板具体版本(★禁止引用"最新版")',
  slot_bindings    JSON NULL COMMENT '仅DISPLAY行: 展示槽位→CALC输出字段绑定表',
  -- +公共
  PRIMARY KEY (id),
  UNIQUE KEY uk_scheme_type (scheme_id, template_type) COMMENT '每版本CALC/DISPLAY各且仅1 → 成对约束',
  KEY idx_template (template_code, template_version) COMMENT '模板下线保护反查'
) COMMENT '方案-模板引用(版本行内冻结; 绑定校验矩阵见 pricing-model.md §5)';
```

### 4.5 pricing_factor 因子字典 / pricing_template 模板库

（结构承 v2.0 不变，仅注释口径同步）

```sql
CREATE TABLE pricing_factor (
  id BIGINT NOT NULL,
  factor_code VARCHAR(64) NOT NULL, factor_name VARCHAR(128) NOT NULL,
  category VARCHAR(32) NOT NULL COMMENT 'ORIGINAL_PRICE/WEIGHT/VOLUME/DISTANCE/WEATHER/HOLIDAY/TIME_PERIOD/AREA...',
  value_type VARCHAR(32) NOT NULL COMMENT 'NUMBER/ENUM/RANGE_TABLE/COEFF_TABLE',
  unit VARCHAR(16), dict_json JSON NOT NULL, validate_rule JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  -- +公共
  PRIMARY KEY (id), UNIQUE KEY uk_factor_code (factor_code), KEY idx_category (category, status)
) COMMENT '定价因子字典(平台级元数据, 零代码扩展)';

CREATE TABLE pricing_template (
  id BIGINT NOT NULL,
  template_code VARCHAR(64) NOT NULL, template_name VARCHAR(128) NOT NULL,
  template_type VARCHAR(16) NOT NULL COMMENT 'CALC/DISPLAY',
  version_no    INT NOT NULL COMMENT '模板自身仍用version_no(内容不可变, 改=新版本)',
  content_json  JSON NOT NULL COMMENT 'CALC:公式树; DISPLAY:文案+变量槽 (pricing-model.md §4/§5)',
  pair_code     VARCHAR(64) NULL COMMENT '成对意向编码',
  outputs_json  JSON NULL COMMENT 'CALC输出字段清单(展示槽绑定与B1校验数据源)',
  status VARCHAR(16) NOT NULL,
  -- +公共
  PRIMARY KEY (id), UNIQUE KEY uk_code_ver (template_code, version_no),
  KEY idx_type_status (template_type, status)
) COMMENT '定价模板库(注意: 模板版本化=内容不可变追加; 与方案版本化=时间区间, 是两种不同机制)';
```

---

## 5. 流程与审计域

```sql
CREATE TABLE approval_flow (
  id BIGINT NOT NULL,
  biz_type   VARCHAR(32) NOT NULL COMMENT 'GROUP_PUBLISH(组内整批方案一次审批单)/GROUP_OFFLINE',
  group_id   BIGINT NOT NULL,
  scheme_ids JSON NOT NULL COMMENT '本次审批覆盖的方案版本行id清单',
  action     VARCHAR(16) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/WITHDRAW',
  operator   VARCHAR(64) NOT NULL, opinion VARCHAR(512),
  -- +公共
  PRIMARY KEY (id),
  KEY idx_group (group_id, create_time)
) COMMENT '审批流水(审批单粒度=费用组, 对齐"一并配置一并发布")';

CREATE TABLE audit_log (
  id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL COMMENT 'SCHEME/DIMENSION/FACTOR_VALUE/TEMPLATE/GROUP/FEE_ITEM',
  biz_id   VARCHAR(64) NOT NULL,
  action   VARCHAR(32) NOT NULL,
  before_json JSON NULL, after_json JSON NULL,
  operator VARCHAR(64) NOT NULL, trace_id VARCHAR(64),
  operate_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id, operate_time),
  KEY idx_biz (biz_type, biz_id, operate_time)
) COMMENT '操作审计'
PARTITION BY RANGE (TO_DAYS(operate_time)) (
  PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01'))   -- job 预建下月分区, 半年前分区归档
);
```

### v3.0 删除表清单（留痕）

| 删除表 | 原职能 | 由什么取代 |
|--------|--------|-----------|
| pricing_plan_version | Bundle 冻结快照 | pricing_scheme 版本行 + 子表时点装配 + content_hash |
| publish_record | 发布动作流水 | audit_log(action=PUBLISH/ROLLBACK/OFFLINE) + 版本行本身(published_at/by) |
| consumer_state_report / consistency_diff | 下游上报核查 | 机制整体移除（决策：平台只保发布正确与可回放，下游一致性出圈）|
| local_message | 事件可靠投递 | 发布提交后 best-effort 投 MQ；失败仅告警；下游按时间版本拉取天然兜底 |

---

## 6. 关键查询模式（时间版本的日常代价与红利）

```sql
-- ① 当前生效集(分发/组展示): 同费用可能返回多行(多实例), 下游/展示按维度点消歧取唯一命中
SELECT s.* FROM pricing_scheme s
WHERE s.status='PUBLISHED' AND s.start_time<=NOW(3) AND (s.end_time>NOW(3) OR s.end_time IS NULL)
  AND s.fee_id IN (SELECT id FROM pricing_fee_item WHERE group_id=?);
-- 命中具体维度点(如杭州订单): 上结果 join scheme_dimension 取包含该城市值的实例(由I4保证至多一个)

-- ② 时点回放(审计/争议): 把 NOW(3) 换成历史时刻 t
-- ③ 方案历史链: WHERE root_scheme_id=? ORDER BY start_time DESC
```

- 版本区间重叠**无法用 DB 约束表达**，由发布事务串行化保证：事务内按 fee_id 升序对涉及费用逐个 `SELECT ... FOR UPDATE` 锁该费用全部开区间生效行（含兄弟实例）→ 闭旧+转正 → **事务内终态复核 I1/I4（§6.2，基于本组发布完成后的最终生效态，违规即回滚）**；v3.4（D9）起无 Redis 发布锁，DB 行锁独扛；

### 6.2 I4 维度互斥校验（发布事务内，终态复核）

```
复核时机: 在本组"闭旧+转正"全部写入之后、提交之前(事务内视图=发布后终态),
          对本次发布涉及的费用, 重建其生效集并两两执行交集检查:
对生效集中每对实例版本 (V, S)(区间相交):
  逐 dim_code 比较两侧值集(未声明的维度=全集; NOT_IN 取补集):
    存在任一 dim_code 使两侧值集不相交 → 命中集互斥, 该对通过
    所有共有 dim_code 值集均相交 → 命中集合相交 → 冲突, 拒绝发布(回滚)并输出冲突报告(哪个城市/网格与哪个实例碰撞)
原理: 多维 AND = 笛卡尔积, 积相交充要于每维投影相交; 校验在发布时一次完成(写频极低, O(同费用实例数²×维度数), 成本可忽略)
为何放在写入后复核而非锁旧行比对(D6):
  合法场景"默认实例 NOT_IN 缩集 + 新增广州专属"在同一次组发布内——
  若逐一对照未更新的旧行, 旧默认集仍包含广州 → 误拒; 终态复核则正确通过, 且回滚保证无中间态窗口
```

**"默认 + 专属"维度表达范式（D6，计费命中实例）**：

```
费用A ─ 方案实例B  CITY IN  {北京}              ← 专属: 北京门店命中 B
      ─ 方案实例C  CITY IN  {上海}              ← 专属: 上海门店命中 C
      ─ 方案实例D  CITY NOT_IN {北京,上海}      ← "默认"=补集表达, 其余城市命中 D
三者命中集两两不相交, I4 成立; 新城市专属 = 同组页内改 D 的 NOT_IN + 新增专属实例, 一次组发布原子生效
(不声明任何维度的"纯默认"实例命中全集, 只能单独存在, 与任何专属并存都会被 I4 拒绝)
``

- 时间列统一 `DATETIME(3)`，**服务器单时区（东八区）存本地时间**是团队规约前提；若未来多时区运营需改 UTC（见 §9 待评审 3）。

---

## 7. 发布事务（组粒度原子，伪代码）

```
@Transactional(rollbackFor=Exception.class, timeout=5)      -- 组内多方案一批, timeout 放宽
publishGroup(groupId):
  1 校验: 必填费用齐备 + 每方案领域校验(因子完备/双模板配对/槽绑定B1~B5/取值合法)
  2 锁:   本次发布涉及的全部费用(含新建实例的归属费用), 按 fee_id 升序逐个:
          SELECT...FOR UPDATE 该费用全部"开区间 PUBLISHED 行"(含兄弟实例, 锁住 I1/I4 校验读)
          -- D9: 无 Redis 发布锁; 升序获取 = 全局锁序一致防死锁
  3 闭合: UPDATE 同实例旧区间行 SET end_time=:newStart, row_version=row_version+1
          WHERE id=:curId AND row_version=:expect        -- 乐观锁双保险, 0行受影响即回滚
  4 转正: UPDATE DRAFT版本行 SET status='PUBLISHED', start_time=:newStart,
          end_time=:planEnd, content_hash=:hash          -- 子表行不动(发布后禁止再改)
  5 终态复核(D6): 事务视图重建涉及费用的生效集(=发布后终态),
          两两复核 I1(区间重叠)/I4(§6.2 维度互斥) → 任一违规整体回滚
          (置于写入后: 支持"默认 NOT_IN 缩集+新增专属"同组原子变更的终态判定)
  6 留痕: approval_flow 终态 + audit_log 逐行 before/after
提交后: afterCommit → MQ 发布事件(失败仅日志告警)
```

定时发布 = 步骤 5 的 `:newStart` 用未来时间，**闭合旧行也在当刻立即完成**（旧版本 end 提前写好 = 新版本 start）→ 时间一到自然切换，无 job 依赖。

---

## 8. 容量重算（v3.0）

| 表 | 驱动因素 | 3 年量级 | 评估 |
|----|---------|---------|------|
| 费用组/费用 | 场景×组×费用 | < 10 万行 | 忽略 |
| **pricing_scheme（含全部版本行）** | 费用 5 万 × 年均改价 10 次 × 3 年 | **~150 万行** | 单表轻松；比 v2.0 bundle 模型行数增加但均为窄行 |
| 维度/因子赋值/模板引用 | 版本行 × 平均 3~8 行 | ~800 万行 | 单量最大表, 仍远低于单表阈值 |
| approval_flow / audit_log | 100 运营 × 50 操作 | < 600 万/年 | 月分区+归档 |
| 读压力 | 下游拉取生效集(直查 DB, D8) + 管理端 | < 100 QPS | 全走索引点查/小范围扫 |

- **结论不变且更强**：无需分库分表；v2.0 的最大存储问题（bundle_json 90GB→25GB）随 Bundle 机制删除而**消失**，总库量 < 10GB；
- 新成本：**装配型读**（下发要 join 子表拼版本内容）与**区间扫描**——均由 `<100 QPS + 索引点查` 覆盖（D8：不配缓存），可接受；
- 归档策略：`end_time < now()-3y` 的版本行及子表迁移归档库（合规保留），audit_log 分区滚动。

---

## 9. 评审确认点（已全部关闭 · D7 按默认定案）

1. ~~费用:方案语义~~ **已定案（D5）**：费用:方案实例 = 1:N 并存、各绑不同维度；任意(费用×维度点×时点)唯一命中；
2. ~~维度独立时间线~~ **已定案（D7，按默认）**：维度冗余方案区间、随方案版本行发布，改维度 = 发新方案版本；
3. ~~时间口径~~ **已定案（D7，按默认）**：单时区（东八区）DATETIME(3) 存本地时间；未来多时区运营再评估 UTC；
4. ~~canonical 规则对外~~ **已定案（D7，按默认）**：不对外暴露序列化规则文档，下游以 `(contentHash, startTime)` 作变更感知键、不强制本地复算；
5. ~~组级生效时间~~ **已定案（D7，按默认）**：生效时间在每个方案行上；同组同时刻切换由 newStart 同值写入天然满足，无需组级字段。

> 容量假设（§8）同样按当前量级定案关闭（D7）：结论"不分库分表、总库量 <10GB"成立；若业务量级发生数量级变化，重开 ADR-16。
