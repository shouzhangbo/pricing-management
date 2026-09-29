# 详细设计 · 服务治理（Service Governance）

> 上游文档：`architecture.md` v2.5（§7 技术选型、ADR-14/19 分发与无缓存）、`pricing-model.md` v1.7（§8 SchemeDeliveryService 契约）、`transaction.md` v2.1
> 本文档定义治理对象与矩阵（超时/重试/容错/线程隔离）、消费者配额与鉴权、契约演进规则、发布与可观测性。
> **范围声明**：管理平台无高流量计价接口（D1）；治理重心 = **只读分发接口的契约稳定** 与 **对外消费者的配额隔离**，而非大流量防护。

---

## 0. 文档信息

| 项 | 内容 |
|----|------|
| 文档状态 | ✅ v2.2 已同步 v2.5 口径 |
| 组件 | Dubbo 3.2 + Nacos 2.3（注册/配置）+ Sentinel 1.8（限流）+ Micrometer/Prometheus |

### 版本变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-09-24 | 计价系统口径（quote 链路治理、交易侧降级预案、calc→rule 强依赖矩阵）——**全文废弃** |
| v2.0 | 2026-09-24 | 按只读分发模型（SchemeDeliveryService：effectiveSet / schemeAt / hit / effectiveSetAt）**全量重写**：治理矩阵、consumer 配额、无重试原则、契约演进、监控指标重定义 |
| v2.1 | 2026-09-24 | **按 D8/D9 同步**：删除全部缓存相关治理项（C1/C2、预热、bypass 开关与命中率指标）；分发接口全部直查 DB，超时预算按无缓存口径校准；Redis 监控收敛为编辑锁维度 |
| v2.2 | 2026-09-29 | 同步分发契约修正：`hit` 使用业务/费用组/费用编码三元作用域；明确 `governance.editLock.failClose` 是默认 fail-close 策略的配置项。 |

---

## 1. 治理对象收敛

```
                 ┌────────────────────────── pm 模块化单体 ──────────────────────────┐
 运营浏览器 ────▶│ pm-admin(内网, 走公司统一网关+BUC登录)                             │
                │        │                                                          │
 下游消费者 ────▶│ pm-delivery: SchemeDeliveryService(Dubbo, group=pricing-delivery) │
 (计费/结算/    │            + HTTP OpenAPI(非 Java 下游, AK/SK 签名)                │
  算法/风控)    │  治理对象①: 分发读接口(对外, 契约最严格)                            │
                 │  治理对象②: 管理内部用例接口(对内, 走通用 web 治理)                 │
                 │  治理对象③: MQ 发布事件(单向 best-effort, 无回依赖)                │
                 └──────────────────────────────────────────────────────────────────┘
```

- **无任何出站强依赖**：平台不 RPC 调下游（审批走公司 BPM 平台回调/工单，作为入站处理）；MQ/Nacos 按"可退化依赖"治理；Redis 仅剩草稿编辑锁（D9），故障时编辑入口 fail-close（cache-and-lock.md §3）；
- 下游上报类接口已删除（D2/D5）——治理面里没有"消费方状态管理"。

---

## 2. 接口治理矩阵（核心）

| 接口 | 类型 | 超时 | 重试 | 集群容错 | 幂等 | 限流配额 | 备注 |
|------|------|------|------|---------|------|---------|------|
| `effectiveSet(bizCode,groupNo)` | 读 | 200ms | **0** | failfast | GET 语义 | 10 QPS/consumer | 直查 DB（D8 无缓存），走 `idx_fee_status` 索引 |
| `schemeAt(schemeId,atTime)` | 读 | 500ms | 0 | failfast | GET 语义 | 10 QPS/consumer | 含版本行+子表装配 join |
| `hit(bizCode,groupNo,feeCode,dimCtx,atTime)` | 读 | 500ms | 0 | failfast | GET 语义 | 20 QPS/consumer | 下游单点查询主入口；费用编码在费用组内唯一 |
| `effectiveSetAt(bizCode,atTime)` | 读 | 1s | 0 | failfast | GET 语义 | 2 QPS/consumer | 审计/回放低频 |
| HTTP OpenAPI 等价四接口 | 读 | 同上 ×1.5 | 0 | — | 同上 | 同配额（AK 维度合并计）| 网关侧 |
| MQ `PRICING_PUBLISH_NOTIFY` | 事件 | — | 生产侧不重试 | — | 消费方以 contentHash 去重 | — | best-effort（ADR-15）|
| 审批回调（入站）| 写 | — | 上游按我方幂等键重试 | — | `uk(biz_type,biz_id,action)` | — | 重复回调返回终态 |
| 管理端用例接口 | 写 | 网关默认 | 0 | — | row_version CAS | 用户级 5 QPS | 409 冲突透传前端 |

**三条铁律**：

1. **重试一律为 0**：全读接口幂等但下游轮询天然自愈，重试只会把超时放大成雪崩；写接口靠 CAS 语义由用户重提。**这是与 v1.0（计价链路配重试/降级预案）最大的口径差异**；
2. 超时预算倒挂禁止：下游轮询客户端超时 ≥ 我方服务端超时，接入文档写明；
3. 新 Dubbo 接口必须登记本矩阵方可上线（CI 检查项）。

---

## 3. 线程与容量隔离

| 措施 | 配置 | 目的 |
|------|------|------|
| Dubbo 线程池隔离 | delivery 组独立协议端口/线程池 `fixed 200`；内部管理接口不占其线程 | 慢装配不拖垮管理端操作，反之亦然 |
| 装配查询连接配额 | 分发只读事务走独立 Hikari 池（10 连接）| 与写事务（发布 5s 长事务）物理隔离，防发布占满连接 |
| 实例与部署 | 2 实例起（模块化单体同进程），Nacos 权重灰度 | 单实例可承载预估 <100 QPS 的 10 倍余量 |
| 直查 DB 自我保护（v2.1，替代预热） | 分发查询强制 `MAX_EXECUTION_TIME` hint（1s）；无缓存层、无预热环节（D8） | 防异常大结果集拖垮 DB；读压量级见 architecture §6.1 |

---

## 4. 消费者接入与配额治理

```
接入登记制: 每个下游 = consumer_id + AK/SK, Nacos 白名单配置(consumers.yaml)下发, 秒级生效
配额模型: Sentinel 参数流控, 资源=接口, 参数位=consumer_id, 默认按 §2 表; 个别放大需评审
轮询规范: effectiveSet 轮询间隔 ≥ 30s(轮询自愈节奏, v2.1 起与缓存 TTL 无关), 接入文档强制; 违规消费者用监控点名
鉴权边界: 分发接口只读; 管理用例接口不出内网网关, 双通道不互通
审计:     分发接口访问日志(采样) 7 天; 配置变更(Nacos)全量留痕
```

---

## 5. 契约演进与兼容（对外接口即承诺）

| 规则 | 说明 |
|------|------|
| 字段只加不改删 | DTO 新增字段默认值兼容旧 consumer；禁止改语义/删字段/改枚举值含义 |
| 版本标识 | `SchemeContentDTO.specVersion` 随契约演进递增；消费者接入时声明兼容 specVersion 范围，平台侧按 consumer 配置输出上限 |
| 时间格式 | 统一 `yyyy-MM-dd HH:mm:ss.SSS`（东八区，D7），字符串传输，避免跨语言 LocalDateTime 序列化分歧 |
| 大版本策略 | 破坏性变更 = 新 Dubbo version（`1.0.0 → 2.0.0`）双版本并行 ≥ 1 个迁移季度，Nacos 上下线 |
| 变更先行 | 任何契约变化先改 `pricing-model.md` §8 与本文档矩阵并定稿，再上线代码（文档先行流程）|

---

## 6. 发布与变更治理（平台自身）

| 手段 | 用法 |
|------|------|
| 实例灰度 | Nacos 权重 0→20→100 分批放量；分发接口无状态，随机路由即可 |
| 配置灰度 | Nacos 灰度发布（beta 到指定实例）用于新治理参数试运行 |
| DB 变更 | 先向后可用：加列/加索引 → 发布代码 → 回收旧列；禁止发布窗口内做不可回滚 DDL |
| 回滚 | 代码回滚即恢复（时间区间数据模型向前兼容：新版本行被旧代码可读——status/start/end 语义不变）；已发布配置数据**不随代码回滚**（配置回滚走平台"回滚=重新发布"功能，见 pricing-model §2）|

---

## 7. 可观测性（监控指标重定义）

### 7.1 核心指标

| 域 | 指标 | 告警阈值（初始值，Nacos 可调）|
|----|------|------------------------------|
| 分发接口 | QPS / RT P99 / 错误率（分接口分 consumer）| 错误率 >0.5% 持续 5min → P2；RT P99 > 超时 80% → P3 |
| 分发接口 | 消费者轮询延迟 = now − 该 consumer 上次 effectiveSet 成功时间 | > 3× 其声明间隔 → 看板点名（不告警，下游的事）|
| 发布链路 | 发布成功率 / 平均事务时长 / 终态复核拦截数（I1/I4 冲突分类计数）| 事务时长 >4s（逼近 timeout）→ P3；复核拦截 = 运营撞车信号，日报呈现 |
| 锁与 Redis | 编辑锁获取失败率 / 编辑入口拒绝数 / Redis 连接状态 | 拒绝突增 → P2；**Redis 故障对发布/分发零影响**（D9），不再有 bypass 指标 |
| 业务健康 | 待审批积压数与最老单据时长 / 已过期未闭合异常区间扫描（job 顺带巡检）| 积压 >3 天 → 通知；区间巡检违例 = P1（理论不可能，出现即 bug）|
| 依赖 | DB 慢 SQL（>1s）、MQ 发送失败计数、Nacos 掉线 | 慢 SQL 即时 P3；MQ 失败只计数不告警（best-effort 定位）|

### 7.2 链路留痕

- Trace：SkyWalking 入口 = admin HTTP / delivery Dubbo / MQ 消费，发布链路 `提交→审批→发布事务→事件` 以 traceId + schemeId 双键可查；
- 审计不依赖日志系统：`audit_log` 表（月分区）是业务事实源，日志/Trace 仅为排障辅助——**保留期以 DB 审计为准**。

---

## 8. 治理配置清单（Nacos 动态项汇总）

| 配置键 | 默认 | 说明 |
|--------|------|------|
| `governance.timeout.effectiveSet` | 200ms | 矩阵超时 |
| `governance.timeout.schemeAt` / `.hit` | 500ms | |
| `governance.quota.default.qps` | 按 §2 表 | consumer 默认配额 |
| `governance.consumers` | — | AK/SK 白名单与个体配额 |
| `governance.specVersion.max` | 1 | 按 consumer 覆盖 |
| `governance.editLock.failClose` | true | Redis 故障时编辑入口拒绝；这是默认 fail-close 策略的运行期开关（cache-and-lock §3）|
| `alert.threshold.*` | §7.1 | 阈值热调 |
