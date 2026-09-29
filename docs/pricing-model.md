# 详细设计 · 定价域模型与双模板设计（Pricing Model & Templates）

> 上游文档：`architecture.md` v2.5（ADR-12 时间区间版本化、ADR-13 双模板锁定、ADR-18 组粒度发布、ADR-21 新功能落位）、`data-model.md` v3.6
> 本文档定义核心层级模型的对象与版本状态模型、因子体系、**计算模板/展示模板结构与相互绑定校验**、版本内容确定性装配（content_hash）、分发接口契约。
> 取代原 `pricing-engine.md`（计价执行归下游系统，已出本项目边界）。

---

## 0. 文档信息

| 项 | 内容 |
|----|------|
| 文档状态 | ✅ v1.7 已同步 v2.5 口径 |
| 归属模块 | `pm-domain` / `pm-application` / `pm-delivery` |

### 版本变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.0 | 2026-09-24 | 初稿：四级模型、因子四形态、公式树计算模板、槽位展示模板、成对绑定校验矩阵、Bundle 序列化规范、状态机、Dubbo 分发契约 |
| v1.1 | 2026-09-24 | 随 v2.1 决策修订：层级插入费用组/费用；pricing_plan→pricing_scheme；Bundle 冻结改为时间区间版本化；分发契约改生效集轮询；删除下游上报接口 |
| v1.2 | 2026-09-24 | 决策 D5：费用:方案 = 1:N 实例并存各绑维度；I1 重定域实例级，新增 I4 维度互斥；分发契约新增按方案取内容与 hit 维度消歧接口（现已统一为 schemeId） |
| v1.3 | 2026-09-24 | 决策 D6：互斥冲突不设柔性决胜，一律拒绝发布；新增"默认+专属"NOT_IN 补集范式说明；I1/I4 改事务内终态复核 |
| v1.4 | 2026-09-24 | 评审点全部关闭（D7 按当前默认设计定案），文档定稿 |
| v1.5 | 2026-09-24 | **按 D8/D10 同步**：新增 §9 沙盒试算（CalcEvaluator，D4 边界不变）；观察者模式动作去清缓存；原 §9/§10 顺延为 §10/§11 |
| v1.6 | 2026-09-24 | 模板方法登记补充：`AbstractImportPipeline` 批量导入五步骨架（配合 D10 与 teaching 专题④） |
| v1.8 | 2026-09-29 | 方案稳定编号移除：前后端与分发契约统一使用 `schemeId`；时间版本链改由内部 `root_scheme_id` 维护。 |

---

## 1. 域模型层级（对齐功能核心）

```
业务 BizLine
 └─ 定价场景 PricingScene          「什么业务情形」: 定义 factor_scope 因子白名单
     └─ 费用组 FeeGroup             「一次费用展示的整体包」: ★管理端同页一并配置、一张审批、整组原子发布(ADR-18)
         └─ 费用 FeeItem             组内费用: 名称 feeName / 编码 feeCode / 顺序 seqNo / 必填 required
             └─ 定价方案 PricingScheme 「一个费用的定价实例」: 聚合根; 同费用可 N 个实例并存各绑维度;
                 │                     每实例 start/end 时间区间多版本, 任一时刻实例至多一个生效版本
                 ├─ 绑定维度 Dimension     「在哪里/对谁生效」: CITY/GRID/MERCHANT_GROUP/CUSTOMER_LEVEL, 多维度 AND, ★同为时间版本
                 └─ 方案内容(版本行范围内)
                     ├─ 因子赋值 FactorValue[]  「用什么原料」: 引用因子字典, 按四形态赋值
                     └─ 模板引用 TemplatePair     「怎么算 + 怎么说」
                         ├─ 计算模板 CALC@v     发布给下游计费引擎执行的公式结构
                         └─ 展示模板 DISPLAY@v  平台展示"费用怎么构成"的文案结构
                             ★ 以 (template_code, template_version) 精确锁定, 随方案版本行发布冻结
```

**聚合与一致性边界**：`PricingScheme` 为聚合根（费用组为发布事务外壳）；对维度/因子赋值/模板引用的一切修改必须经聚合（应用层用例 → 聚合方法 → 仓储），保证发布校验规则不可能被旁路。

---

## 2. 版本与状态模型（v1.1 改为时间区间版本，模板方法定义流转骨架）

```
版本行生命周期(每行 = 一个时间区间):
DRAFT ─submit─▶ PENDING_APPROVAL ─approve─▶ PUBLISHED(start/end 已写入, 生效性完全由区间定义)
   │                    │reject→回DRAFT            │ 新版本发布时: 本行 end_time 被闭合(唯一允许的 UPDATE)
   └─CANCELLED(作废)                             └─区间滑过后自然成为"历史版本"(状态不变, 可回放/可回滚源)

不变式: I1 同实例(root_scheme_id，仅内部使用) PUBLISHED 区间互不重叠 / I4 同费用并存生效实例命中维度集两两互斥
        I2 闭旧+转正同事务原子 / I3 start・end 发布后不可改
推论: (费用×维度点×时点) 唯一命中一个生效方案 → 下游无需优先级决胜
D6: 不设"允许重叠按规则决胜"的柔性模式, 冲突即拒绝发布; "默认方案"用 NOT_IN 补集表达,
    与新增专属实例在同一次组发布内原子变更(终态复核, 见 data-model §6.2/§7)
回滚: 复制历史版本内容为新 DRAFT → 走正常发布(rollback_from_id 溯源), 区间链线性不倒退
```

- 流转骨架抽象为 `AbstractTransition`（模板方法）：`checkPermission() → checkStateMachine() → validateAggregate() → doExecute()【钩子:各流转差异】 → writeAudit()`，全部在**同一本地事务**内执行（architecture §4.1）。
- 定时发布 = 转正时 start 写未来时间；旧版 end 同事务提前闭合到同一时刻——**时钟即切换器，无生效 job**。
- 提审门槛（组粒度）：`is_required=1` 的费用必须已配齐合法方案；非必填费用允许缺省（发布时组内校验报告留痕）。

---

## 3. 因子体系

### 3.1 四种赋值形态（value_type → 校验策略 → 消费形态）

| value_type | 含义 | validate_rule 示例 | 下游消费形态 |
|-----------|------|-------------------|------------|
| NUMBER | 单数值（起步价、系数） | `{min:0, max:9999, precision:2}` | 常量 |
| ENUM | 枚举选择 | `{allowed:["SUNNY","RAIN","STORM"]}` | 条件分支输入 |
| RANGE_TABLE | 区间表（距离阶梯、重量阶梯） | `{axis:"KM", bandsMustCoverFrom:0, noOverlap:true, maxBands:100}` | 查表 |
| COEFF_TABLE | 系数表（天气×时段矩阵） | `{rows:[...],cols:[...],coeffRange:[0.8,3.0]}` | 二维查表 |

### 3.2 因子校验器（策略 + 工厂模式）

```java
public interface FactorValueValidator {              // 每种 value_type 一个实现(策略)
    FactorType type();
    void validate(FactorDict dict, PlanFactorValue value);   // 违规抛 FactorValidationException(定位到具体band/字段)
}
// 工厂: Spring 注入全部实现 → Map<FactorType, Validator>; 新增因子形态=新增策略, 零改动开闭
```

发布校验时对本方案**全部因子赋值**逐一执行校验；区间表校验重点：连续性（无缝隙）、无重叠、边界开闭约定（`[min, max)`，最后一档 max=null）。

### 3.3 场景约束（防止方案乱用因子）

`PricingScene.factor_scope ⊇ plan 使用的全部 factor_code`——提交审批时校验。计算模板中引用的 `FACTOR` 节点同样受此约束（模板是通用骨架，方案因子值是原料）。

---

## 4. 计算模板（CALC）—— 给下游计费引擎用

### 4.1 结构：受限表达式树（组合模式 / 可解释的 IR）

模板是**与执行引擎约定的中间表示**，不是脚本（安全、可静态校验、可确定性序列化）：

```
节点类型(封闭集):
  CONST{value}                 常量
  FACTOR{code}                 因子引用(NUMBER/ENUM) → 运行时取方案因子赋值
  LOOKUP{factor, table}        区间/系数查表(RANGE_TABLE/COEFF_TABLE 因子)
  OP{op, args[]}               +-*/ min max ceil floor round(精度白名单)
  CONDITION{if, then, else}    条件(比较: eq/gt/ge/lt/le/in)
  OUTPUT{name, expr}           命名输出字段(★对展示模板暴露的绑定面)
```

示例（标准配送起步阶梯 + 天气系数）：

```json
{
  "templateCode": "CALC_DELIVERY_STANDARD", "versionNo": 4,
  "outputs": [
    { "name": "baseFee",  "expr": ["OP","+",{ "LOOKUP":"DISTANCE" },
              { "OP","*": [ {"CONST":1}, {"LOOKUP":"WEATHER"} ] }] },
    { "name": "totalFee", "expr": ["OP","max",[{"OUTPUT_REF":"baseFee"},{"CONST":2}]] }
  ],
  "outputMeta": [
    { "name":"baseFee",  "label":"基础配送费", "unit":"YUAN", "displayable": true },
    { "name":"totalFee", "label":"应付配送费", "unit":"YUAN", "displayable": true }
  ]
}
```

### 4.2 模板静态校验（发布前，模板自身 + 方案上下文两级）

| 校验项 | 时机 |
|--------|------|
| 节点类型在封闭集内、引用成环检测、深度 ≤ 32 | 模板版本发布时 |
| `FACTOR/LOOKUP` 引用的 factor_code ⊆ 场景 factor_scope | 方案挂模板时 + 方案发布时 |
| 所有 OUTPUT 可达且类型推导为金额/数值（禁止字符串参与算术） | 方案发布时 |
| 表达式对全因子赋值的"干跑"（构造哨兵 ctx 求值一次，防运行期空引用） | 方案发布时 |

---

## 5. 展示模板（DISPLAY）—— 给平台/C端展示用

### 5.1 结构：文案骨架 + 变量槽 + 槽绑定

```json
{
  "templateCode": "DISP_DELIVERY_STANDARD", "versionNo": 3,
  "title": "配送费说明",
  "items": [
    { "slot":"baseFeeText",  "text":"基础配送费 ${amount} 元（含3公里）",
      "bind":"calc.baseFee",  "format":"0.00", "showWhen": null },
    { "slot":"weatherTip",   "text":"恶劣天气补贴 ${amount} 元",
      "bind":"calc.weatherSurcharge", "format":"0.00",
      "showWhen":"calc.weatherSurcharge > 0" }
  ],
  "summary": { "slot":"totalText", "text":"共 ${amount} 元", "bind":"calc.totalFee" },
  "styleHint": { "locale":"zh-CN", "currencySymbol":"¥" }
}
```

- 渲染职责在**下游展示服务**（本平台只供给结构与文案，不做 C 端渲染）；
- `showWhen` 为受限表达式（仅比较与逻辑运算，引用 calc 输出），与计算模板同一套解析器（复用组合模式解释器）。

### 5.2 双模板绑定校验矩阵（★ADR-13 核心，平台"数据一致"的模型根基）

发布时强制校验，任一不通过即拒绝发布：

| # | 校验 | 防住的问题 |
|---|------|-----------|
| B1 | DISPLAY 每个 `bind` 引用的字段 ∈ CALC `outputs`（含 showWhen 引用） | 展示引用了不存在的计算项 |
| B2 | CALC 中 `displayable=true` 的输出字段必须被至少一个槽绑定，或在白名单（如中间量） | 算了但没展示 → 客诉"莫名加价" |
| B3 | 槽位 slot 唯一；一个槽只允许一个 bind（金额构成守恒） | 渲染歧义 |
| B4 | format 与输出 unit 类型匹配（金额必须两位小数定点格式） | 展示精度与计算精度不一致 |
| B5 | pair_code 互指：CALC.pair=DISP 编码且 DISP.pair=CALC 编码 | 配错对 |

> B2 的"金额守恒"终极校验：`summary.bind 字段 == CALC 声明的 finalOutput`，且各分项之和与 totalFee 的勾稽关系由 CALC 模板内 `conservation` 声明（如 `total = baseFee + weatherSurcharge`）供发布时干跑断言。

---

## 6. 版本内容装配与 content_hash（时间版本化下的确定性机制，v1.1 修订）

```
版本内容装配(内存 PlanSchemeView) = 方案行 + 维度行[] + 因子赋值[] + 双模板引用(含槽绑定) + 模板内容
     --canonicalize--> JSON 文本 --SHA-256--> content_hash(存入 pricing_scheme 行)
canonical 规则: ① key 字典序 ② 集合按业务主键排序(dim_code/factor_code/slot/template_code)
              ③ 金额 BigDecimal scale=2 字符串 ④ 不含 start/end/operator/create_time 等环境与区间字段
                (hash 度量"配置内容", 不度量"何时生效")
              ⑤ 序列化工具全局唯一(Jackson 固定配置); 单测: 乱序插入→同 hash; 任一字段变→hash 变
```

意义：**同内容必同 hash，差异必现 hash**——下游拉取后本地复算即可验证传输/装配完整；变更感知不依赖任何指针与核查表（v2.0 的 Bundle 落库存储已删除，历史内容由版本行+子表自身承载，见 data-model §1.1）。

---

## 7. 设计模式登记（本项目实际落点）

| 模式 | 落点 | 解决的问题 |
|------|------|-----------|
| 组合 + 解释器 | 计算/展示模板表达式树（Node 家族 + Evaluator/Validator） | 公式结构化、可静态校验、可确定性序列化 |
| 建造者 | `SchemeContentViewAssembler`（多源装配→canonical→hash，发布与回放共用） | 装配步骤复杂且必须一次正确，两个入口一套逻辑 |
| 模板方法 | `AbstractTransition` 状态流转骨架；`AbstractImportPipeline` 批量导入骨架（解析→校验→装配→持久化→报告，v1.6 新增） | 五种流转共享"校验-事务-审计"流程，差异进钩子；导入步骤固定、每种可导入对象（方案/维度绑定/因子赋值）以钩子实现类型差异，新增对象类型不改骨架 |
| 策略 + 工厂 | `FactorValueValidator` 四形态族 | 因子校验开闭扩展 |
| 外观 | `pm-delivery` 四个读接口收敛全部下游消费 | 契约面最小化 |
| 观察者（轻量） | 发布成功 → Spring 事件：仅投 MQ(best-effort) | 副作用与主事务解耦（afterCommit；v1.5 起无清缓存动作，D8） |
| 适配器 | `CanonicalSerializer`（装配→canonical→hash）、展示 format 适配 | 存储/消费形态与领域模型隔离 |

（原 v1.0 责任链计价环节设计随计价出圈移除；下游计费引擎如何按模板执行属其内部设计。）

---

## 8. 分发接口契约（pm-api 模块，只读）

```java
public interface SchemeDeliveryService {                    // Dubbo, group=pricing-delivery
    /** 生效集: 业务/组粒度, 每项 {feeCode, schemeId, startTime, contentHash, dimSummary},
        同费用可多行(多实例各带维度摘要), 小体量, 供轮询与本地索引构建 */
    RpcResult<EffectiveSetDTO> effectiveSet(String bizCode, String groupNo);
    /** 按 实例+时点 取方案全量内容(版本行+维度+因子+双模板+槽绑定装配) */
    RpcResult<SchemeContentDTO> schemeAt(Long schemeId, LocalDateTime atTime);
    /** 维度消歧便捷接口: 费用编码仅在费用组内唯一，传作用域与具体维度上下文，返回唯一命中实例(I4 保证至多一个) */
    RpcResult<SchemeContentDTO> hit(String bizCode, String groupNo, String feeCode,
                                    DimContextDTO dimCtx, LocalDateTime atTime);
    /** 时点生效集(审计/回放用): 含该时刻的完整清单 */
    RpcResult<EffectiveSetDTO> effectiveSetAt(String bizCode, LocalDateTime atTime);
}
// HTTP 等价开放接口(供非 Java 下游): GET /openapi/pricing/effective/{bizCode}
//                                  GET /openapi/pricing/scheme/{schemeId}?at=2026-09-24T10:00:00
//                                  GET /openapi/pricing/hit/{bizCode}/{groupNo}/{feeCode}?city=0571&at=...
//                                  GET /openapi/pricing/effective/{bizCode}/history?at=2026-09-24T10:00:00
契约规则: 全部 GET 语义幂等; 鉴权=consumer_id+AK/SK; DTO 字段演进兼容(只加不改删);
无上报接口(v1.1 删): 下游状态管理不在平台职责内(决策 D1/D2 收敛)。
```

回滚与变更感知：回滚产生**新的版本行**（start 更晚、内容=历史副本），故下游以 `(schemeId, startTime, contentHash)` 判新。`root_scheme_id` 仅用于内部回溯版本链；前后端和下游均不使用它。多个维度互斥的实例可以在同一费用、同一 `startTime` 并存；唯一版本定位与幂等键必须使用 **`(schemeId, startTime)`**（`contentHash` 用于变更与完整性校验）。（红字约定：下游若缓存过旧时点内容，`schemeAt(t)` 的结果永不变化——时点语义天然可永久缓存。）

---

## 9. 沙盒试算（v1.5 新增，决策 D10）

- **入口**：管理端内部接口 `POST /api/sandbox/calc`（pm-admin）——入参为 schemeId（**允许 DRAFT 草稿**，这正是"编辑之后验一下"的价值所在）+ 一组因子样例参数值；
- **执行**：按 §4.1 装载该版本的 CALC 公式树 → `CalcEvaluator` 按 §4 语义解释执行（组合+解释器模式的第二个消费方：evaluator 是节点语义的参考实现）；
- **输出**：试算金额 + 公式逐层展开（每个节点的输入值/分支命中/中间结果）+ 所用版本与绑定维度说明；支持**新旧版本对比试算**（同一组参数分别跑两个版本，审批页直接展示差异）；
- **缺因子策略**：必填因子未给 → 试算直接报"缺因子 X"——试算暴露配置缺口的能力本身就是校验价值；非必填用因子字典默认值；
- **边界（D4 不变，ADR-21）**：试算仅供配置验证流程，**不进入 §8 分发契约、不做 Dubbo 暴露、不承接任何在线计费订单流量**；公式树语义以本文档为唯一规范，平台 evaluator 服务试算与用例基准，下游计费执行自责实现，两侧以规范+标准用例集对齐；
- **零新表**：试算不落库（结果无事实性，历史可审计靠版本行本身）。

---

## 10. 测试设计要点

1. **canonical 序列化属性测试**：乱序插入/等价数值（4.0 vs 4.00）→ checksum 恒等；任意字段变 → checksum 变；
2. 绑定校验矩阵 B1~B5 每条正反用例；干跑校验对"缺因子赋值/区间空洞"必须拦截；
3. 状态机全路径穷举测试（含非法跃迁拒绝）；
4. 分发契约兼容测试：老 consumer 拉新 bundle 的字段容忍（只加不改删回归集）。

---

## 11. 评审确认点（已全部关闭 · D7 按默认定案）

1. CALC 模板节点封闭集按当前 7 类定案；若未来出现"分段函数嵌套过深"场景，按扩展流程（加节点类型+模板 version_no 递增）重开评审；
2. B2 校验的 `displayable` 标记由模板作者在模板版本冻结时声明，不加审批二次确认环节；
3. 下游以 `(contentHash, startTime)` 为变更感知键已定案（不要求本地复算 hash，不暴露 canonical 规则）；
4. 模板库自身 version_no 内容不可变与方案时间区间双机制并存已定案：模板是引用对象（一处定义多方案引用），方案是配置主体（时间线属于方案）。
