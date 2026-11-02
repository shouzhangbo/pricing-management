# 接口文档

> **版本：v0.2（2026-10-24）**
> 本文档区分“当前可调用接口”和“当前需求但尚未实现接口”。审批流暂不属于当前版本，相关接口和状态不纳入本阶段页面联调。

## 1. 基本约定

- 服务地址：`http://{host}:8080`
- 管理端接口前缀：`/api`
- 请求与响应编码：UTF-8；请求体使用 `application/json`。
- 时间字段使用 ISO-8601 本地时间，例如 `2026-10-10T14:30:00`。
- 分页从 `pageNo=1` 开始，默认每页 `20` 条，`pageSize` 最大 `100`。
- 当前版本尚未接入鉴权；接入网关或认证组件后，应在本文档补充认证头、权限点及审计操作者传递规则。
- 当前版本不包含审批流；草稿可直接进入“发布前校验/发布”流程。
- 管理端编辑以“费用组”为页面聚合边界；方案、维度、因子和模板接口是组编辑页的子资源。
- `dimValues`、因子值、模板内容均使用固定 JSON 结构，不能把任意 JSON 当作稳定前端契约。

## 2. 通用响应与错误

除健康检查外，管理端接口统一返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

| HTTP 状态 | `code` | 含义 | 调用方处理建议 |
|---|---|---|---|
| 200 | `0` | 成功 | 使用 `data` |
| 400 | `PARAM_ERROR` | 请求字段校验未通过，或 `dimValues` 不是非空 JSON 数组 | 修正参数后重试 |
| 400 | 其他业务参数错误 | 业务校验未通过 | 根据 `message` 修正请求 |
| 404 | `NOT_FOUND` | 方案、费用组、费用项或维度绑定不存在，或不属于指定范围 | 刷新数据后重试 |
| 409 | `CONFLICT` | 乐观锁版本过期、对象已发布不可编辑，或方案正被其他人编辑 | 刷新详情并携带最新 `rowVersion` 重试 |
| 500 | `SYSTEM_ERROR` | 非预期系统异常或获取编辑锁被中断 | 使用退避策略重试；持续失败时联系维护方 |

`rowVersion` 是编辑接口的乐观锁版本号，必须取自上次查询或创建响应；不要自行递增。

## 3. 数据结构

### 3.1 分页结果 `PageResult<T>`

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | 满足条件的总记录数 |
| `pageNo` | int | 当前页码 |
| `pageSize` | int | 当前页大小 |
| `records` | array | 当前页数据 |

### 3.2 定价方案 `SchemeView`

| 字段 | 类型 | 说明 |
|---|---|---|
| `schemeId` | long | 方案主键 |
| `feeId` | long | 归属费用项主键 |
| `schemeName` | string | 方案名称 |
| `status` | string | 当前状态；当前编辑能力仅允许 `DRAFT` |
| `startTime` / `endTime` | datetime | 版本生效区间；草稿阶段可能为空 |
| `remark` | string | 备注 |
| `rowVersion` | int | 乐观锁版本号 |

### 3.2.1 方案编辑上下文 `SchemeEditorView`（规划中）

用于费用组编辑页一次加载方案主信息、维度、因子赋值和模板引用，避免前端依赖多个接口自行拼装。

| 字段 | 类型 | 说明 |
|---|---|---|
| `scheme` | `SchemeView` | 方案主表 |
| `dimensions` | array | 维度绑定列表 |
| `factorValues` | array | 因子赋值列表 |
| `templates` | array | CALC/DISPLAY 模板引用 |
| `editable` | boolean | 当前是否允许编辑 |
| `editReason` | string | 不可编辑原因 |

### 3.2.2 费用组编辑上下文 `FeeGroupEditorView`（规划中）

| 字段 | 类型 | 说明 |
|---|---|---|
| `group` | `FeeGroupView` | 费用组信息 |
| `fees` | array | 组内费用项及其方案编辑上下文 |
| `rowVersion` | int | 费用组编辑版本 |
| `validation` | object | 最近一次校验结果，可为空 |

### 3.2.3 发布校验结果 `ValidationReport`（规划中）

| 字段 | 类型 | 说明 |
|---|---|---|
| `valid` | boolean | 是否允许发布 |
| `errors` | array | 阻断发布的错误列表 |
| `warnings` | array | 不阻断发布的提示列表 |
| `errors[].path` | string | 前端定位路径，如 `fees[0].schemes[1].dimensions` |
| `errors[].code` | string | 稳定错误码 |
| `errors[].message` | string | 面向运营的错误说明 |

### 3.3 维度绑定 `DimensionView`

| 字段 | 类型 | 说明 |
|---|---|---|
| `dimensionId` | long | 维度绑定主键 |
| `schemeId` | long | 所属方案主键 |
| `dimCode` | string | 维度编码 |
| `dimValues` | string | JSON 数组字符串，例如 `["0571","021"]` |
| `matchMode` | string | 匹配模式：`IN` 或 `NOT_IN` |
| `startTime` / `endTime` | datetime | 生效区间；草稿阶段可能为空 |
| `rowVersion` | int | 乐观锁版本号 |

### 3.4 费用组与费用项

`FeeGroupView`：`groupId`、`groupNo`、`groupName`、`sceneCode`、`status`。  
`FeeItemView`：`feeId`、`feeCode`、`feeName`、`seqNo`、`status`。

## 4. 已实现接口

### 4.1 健康检查

`GET /api/health`

成功响应：

```json
{"status":"UP"}
```

### 4.2 创建方案

`POST /api/schemes`

请求体：

```json
{
  "bizCode": "DELIVERY",
  "groupNo": "STANDARD",
  "feeCode": "BASE_FEE",
  "schemeName": "杭州基础配送费",
  "remark": "仅用于草稿配置"
}
```

| 字段 | 必填 | 约束 |
|---|---|---|
| `bizCode`、`groupNo`、`feeCode` | 是 | 非空，最大 64 字符；三者必须对应已存在的业务、费用组与费用项 |
| `schemeName` | 是 | 非空，最大 128 字符 |
| `remark` | 否 | 最大 512 字符 |

返回 `ApiResponse<SchemeView>`。

### 4.3 修改方案

`POST /api/schemes/{schemeId}`

> 当前实现使用 POST 表示更新；后续 API 大版本可评估收敛为 PUT/PATCH，但在变更前不得调整现有路径。

请求体：

```json
{
  "schemeName": "杭州基础配送费（修订）",
  "remark": "调整说明",
  "rowVersion": 0
}
```

仅草稿方案可修改。`schemeName` 最大 128 字符，`remark` 最大 512 字符，`rowVersion` 必填且不小于 0。返回 `ApiResponse<SchemeView>`。

### 4.4 查询方案详情

`GET /api/schemes/{schemeId}`

返回 `ApiResponse<SchemeView>`；不存在时返回 `404 / NOT_FOUND`。

### 4.5 删除方案

`POST /api/schemes/{schemeId}/delete`

请求体：`{"rowVersion": 0}`。仅允许删除草稿方案，且方案不存在未删除的维度绑定；否则返回 `409 / CONFLICT`。成功返回 `ApiResponse<DeleteResult>`，其中 `data.id` 是被逻辑删除的方案 ID。

### 4.6 分页查询方案

`GET /api/schemes`

| 查询参数 | 必填 | 说明 |
|---|---|---|
| `bizCode`、`groupNo`、`feeCode`、`schemeName`、`status` | 否 | 查询条件 |
| `pageNo` | 否 | 页码，默认 `1`，最小 `1` |
| `pageSize` | 否 | 每页数量，默认 `20`，范围 `1`～`100` |

返回 `ApiResponse<PageResult<SchemeView>>`。

### 4.7 新增维度绑定

`POST /api/schemes/{schemeId}/dimensions`

请求体：

```json
{
  "dimCode": "CITY",
  "dimValues": "[\"0571\",\"021\"]",
  "matchMode": "IN"
}
```

| 字段 | 必填 | 约束 |
|---|---|---|
| `dimCode` | 是 | 非空，最大 64 字符 |
| `dimValues` | 是 | 非空 JSON 数组，以字符串传递 |
| `matchMode` | 是 | 仅 `IN`、`NOT_IN` |

仅草稿方案可新增绑定。返回 `ApiResponse<DimensionView>`。

### 4.8 修改维度绑定

`POST /api/scheme-dimensions/{dimensionId}`

请求体：

```json
{
  "dimValues": "[\"0571\"]",
  "matchMode": "IN",
  "rowVersion": 0
}
```

`dimValues` 与 `matchMode` 的约束同新增接口；`rowVersion` 必填且不小于 0。仅所属方案为草稿时允许修改。返回 `ApiResponse<DimensionView>`。

### 4.9 查询维度绑定详情

`GET /api/scheme-dimensions/{dimensionId}`

返回 `ApiResponse<DimensionView>`；不存在时返回 `404 / NOT_FOUND`。

### 4.10 删除维度绑定

`POST /api/scheme-dimensions/{dimensionId}/delete`

请求体：`{"rowVersion": 0}`。仅所属方案仍为草稿时允许删除；返回 `ApiResponse<DeleteResult>`。

### 4.11 分页查询方案维度绑定

`GET /api/schemes/{schemeId}/dimensions`

| 查询参数 | 必填 | 说明 |
|---|---|---|
| `dimCode`、`dimValue` | 否 | 维度编码、维度值筛选条件 |
| `pageNo` | 否 | 默认 `1`，最小 `1` |
| `pageSize` | 否 | 默认 `20`，范围 `1`～`100` |

返回 `ApiResponse<PageResult<DimensionView>>`。

### 4.12 查询业务下费用组

`GET /api/businesses/{bizCode}/fee-groups`

`bizCode` 必填且不可为空。返回 `ApiResponse<FeeGroupView[]>`。

### 4.13 查询费用组下费用项

`GET /api/businesses/{bizCode}/fee-groups/{groupNo}/fee-items`

`bizCode`、`groupNo` 均必填且不可为空。返回 `ApiResponse<FeeItemView[]>`。

## 5. 当前需求接口（规划中，审批除外）

以下接口是当前项目规划和费用组配置页面需要的业务契约，尚未实现，不能联调。

| 接口 | 用途 |
|---|---|
| `GET /api/fee-groups/{groupNo}/editor?bizCode=...` | 一次加载费用组、费用项、方案及其完整编辑上下文 |
| `GET /api/schemes/{schemeId}/editor` | 加载单个方案及维度（因子、模板接口待后续补齐） |
| `PUT /api/fee-groups/{groupNo}/draft` | 以费用组为边界保存草稿，使用组级 `rowVersion` |
| `POST /api/fee-groups/{groupNo}/validate` | 执行必填费用、因子、模板、维度冲突等发布前校验 |
| `POST /api/fee-groups/{groupNo}/publish` | 组粒度本地事务发布，支持立即或未来 `startTime` |
| `GET /api/fee-groups/{groupNo}/versions?at=...` | 查询费用组指定时点或历史版本 |
| `POST /api/schemes/{schemeId}/copy` | 复制历史/草稿内容生成新的 DRAFT 方案 |
| `GET /api/schemes/{schemeId}/diff?compareTo=...` | 方案版本差异对比 |
| `GET /api/factors` | 查询启用因子及其值类型、校验规则 |
| `GET /api/templates?type=CALC|DISPLAY` | 查询可绑定模板版本 |
| `PUT /api/schemes/{schemeId}/factor-values` | 全量保存方案因子赋值 |
| `PUT /api/schemes/{schemeId}/templates` | 保存 CALC/DISPLAY 模板成对引用 |
| `GET /api/schemes/{schemeId}/dimensions/coverage` | 查询维度覆盖图和冲突预检结果 |
| `POST /api/import/schemes/validate` | 校验批量导入文件，不落库 |
| `POST /api/import/schemes` | 导入为 DRAFT，不允许直接发布 |
| `GET /api/export/schemes` | 导出草稿、当前生效集或历史时间线 |

## 6. 下游分发与试算接口（规划中，不可调用）

下列接口来自 [定价域模型设计](pricing-model.md)，但当前没有 HTTP Controller，不能用于联调或生产调用。

| 接口 | 用途 | 当前状态 |
|---|---|---|
| `GET /openapi/pricing/effective/{bizCode}` | 按业务与费用组拉取当前生效方案集 | 规划中 |
| `GET /openapi/pricing/scheme/{schemeId}?at=...` | 按方案与时点获取完整内容 | 规划中 |
| `GET /openapi/pricing/hit/{bizCode}/{groupNo}/{feeCode}?city=...&at=...` | 按维度上下文命中唯一方案 | 规划中 |
| `GET /openapi/pricing/effective/{bizCode}/history?at=...` | 按业务与时点查询生效方案集 | 规划中 |
| `POST /api/sandbox/calc` | 草稿或已发布方案的沙盒试算 | 规划中 |

对应 Dubbo 契约 `SchemeDeliveryService` 已定义，但 Provider 当前统一返回 `NOT_IMPLEMENTED`；在完成查询实现、AK/SK 鉴权、DTO 样例和兼容性测试前，不得对下游宣称可用。

## 7. 当前版本明确不做

- 审批提交、审批通过、驳回、审批回调和 `PENDING_APPROVAL` 页面流程。
- 在线计价、订单价格快照和下游消费状态回报。

## 8. 文档维护规则

1. 新增、删除或修改 HTTP/Dubbo 契约时，必须在同一变更中更新本文档及相关设计文档。
2. 接口从“规划中”变为“已实现”前，至少补齐请求/响应字段、示例、认证方式、错误码和联调测试。
3. 破坏性变更（删字段、改字段语义、改路径）必须走版本化路径；普通演进只允许新增可选字段。
