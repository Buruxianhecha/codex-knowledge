---
status: active
confidence: 0.99
reuse_count: 0
last_used: 2026-08-26
verified_in: [codex-knowledge]
expires_after: 2026-11-26
---

# Distilled Memory — 可移植版 v7

> 高密度速查；不是事实源替代品。详细证据回到具体 Project/Lesson/Pattern 和 `projects/2026-08-26-two-month-learning-audit.md`。

## 用户速查

| 属性 | 值 |
|------|-----|
| 交互称呼 | Lin |
| GitHub | Buruxianhecha |
| 历史 Git 显示名 | 怀民亦未寝；只解释已有提交 |
| 默认语言/时区 | 简体中文 / UTC+8 |
| 工程风格 | 实用、轻量、先原型后硬化 |
| 写作风格 | 深度、证据边界明确、不夸大 |
| 复杂任务 | 可拆时主动多智能体并行，共享 Schema 后统一审计 |

## 最高优先级

- 未验证 = 未完成；命令/产物/集成/宿主/用户路径/重启保持分层。
- 用户实际优先于理论推断。
- 排他需求是合同；修改现有作品保护未指定区域。
- 结论强度不超过证据强度。
- 行为时长按 active state，不按页面墙钟。
- 浏览器长期状态按版本化数据库处理。
- 空值优于假数据。
- 凭据和具体用户主目录不进公开库。
- Source knowledge 不为维护而压缩；Compact 才承担强压缩。

## 最近两个月强验证

- **卡塞尔开放世界模拟器**：HEAD `5676ac91d45c`，CI success；单调状态、旧页面拒绝、存档迁移、长程内容去重。
- **剑来人生模拟器**：HEAD `304ce029b17a`，CI success；动作语义、随机序章、多存档、死亡状态与 VM 行为回归。
- **凡人人界篇模拟器**：HEAD `e20f299caeb8`，最终 CI success；只有 4 项 smoke test，不能写成完整玩法验证。
- **拾光相册测试版**：构建和核心交互有验证；正式上传/恢复/服务端授权/真机仍不足，暂不建议生产使用。

## 规格明确但实现未验证

### 文章货架

```text
readingActive = articleRoute && visible && focused && !idle && validSession
```

只累计 active 区间；chunk 幂等、多标签页防双算、sleep delta clamp、业务时区切日。签到用 `(userId,businessDate)` 事实账本，连续/月历/全勤由账本推导。

### BIM / 结构输入

```text
平面定位
-> 断面/详图
-> reference plane
-> nominal size
-> offset
-> 上/下増打范围
-> 3D/断面复核
```

断面局部值不自动成为全构件常量；渐变厚度要按实际范围表达。

## 仍未闭环

- 豆包：实例脚本层通过，客户端接纳/重启保持未闭环。
- 李跳跳：大输入截断，没有真实导入成功证据。
- 抖音续火花：部署包存在，实际运行未证实。
- 极简电视时钟：远端仍偏离最终“单文件、纯黑、仅 HH:MM”等排他要求。
- Android 贪吃蛇：只有需求记录，没有可验收资产。

## 写作与研究

2026-07/08 形成了证据工程：

```text
问题/范围/截止日
-> Source Tier
-> Material Ledger
-> Claim Ledger
-> 支持/反证/冲突裁决
-> 允许措辞/禁止升级
-> 引用审计
-> 叙事
-> Word/PDF 署名与隐藏元数据检查
```

关键边界：`contributor` 不写成 `lead`；公司自述不等于法律认定；单案例不外推总体因果；SHA-256 只证明材料版本一致。

代表性项目：洪涝动物风险、兔娘式直播、数字亲密关系、东东式宣传、平台化 AI 治理、翁家翌人物研究、18 智能体证据工作流。

## 复杂提示词 / 多智能体

复杂提示词视为可执行规格：目标、现状、输入事实、必须项、禁止项、状态模型、不变量、输出契约、验证矩阵、失败处理、变更策略和收口机制。

多智能体：先画依赖 DAG；独立节点并行；共享 `facts/evidence/assumptions/conflicts/proposedChanges/verification/unresolved`；最终由一个审计角色裁决，不能直接拼多份 prose。

## 浏览器状态与互动世界

- LocalStorage/IndexedDB 是小型数据库：schemaVersion、owner、revision、迁移、恢复、完成态。
- 单调状态防重复点击、旧标签页倒退和刷新复活。
- 随机结果创建时固化并随存档保存。
- 动作结果按语义/场景/风险/状态，而不是“第几个按钮”。
- 长程内容比较要去掉编号等装饰差异后做语义/规范化指纹。

## 约束式编辑

```text
Must change
Must preserve
May adapt minimally
```

图片、模板和现有网站修改同时做正向验收与负向 diff；“修改现有项目”不等于从零重建。

## Windows / 自动化

- 记忆和环境变量是线索，真实仓库/文件/HEAD 才是 source of truth。
- 绝对路径降低 cwd 漂移，但仍要验证路径存在和命令可执行。
- C 盘写入/删除采取保守策略；工作区例外也不能无差别清空。
- PowerShell 5.1 与 7+ 的编码默认不同，不使用“永远 BOM/永远 NoBOM”的教条。
- `.NET String` 内部使用 UTF-16 不是 UTF-8 乱码的天然根因；要检查完整 bytes->decode->replace->encode->consumer 链。

## Microsoft Graph / Device Code 纠错

2026-08-26 官方文档复核后：

- Device Code 仅用于 public client。
- 应使用自己 Entra App Registration 的 client ID。
- `/token` 轮询必须发送与 `/devicecode` 相同的 `client_id`。
- 轮询遵守 `interval` 并处理 pending/slow_down/declined/expired。
- refresh token 是凭据，可过期/撤销；刷新后如果返回新 refresh token，应替换旧缓存。
- 可接受依赖时优先让 MSAL 管理协议/token cache。

## GitHub 发布

```text
冻结必须/禁止项
-> 本地完整快照
-> 明确测试类型
-> secret/隐私扫描
-> push
-> 回读 HEAD/关键 Blob
-> 当前 SHA CI
-> README/真实入口用户路径
```

CI 结论属于具体 SHA；历史红 run 与当前绿 run 可以同时为真。

## 知识库二次审计

已纠正：

- 多引擎择优 / SQLite migration / output quality gate 因只有单项目证据，从 `verified` 回到 `active`。
- 06-22 绝对路径模式标记 `superseded`，由 06-28 source-of-truth + runtime check 模式替代。
- 用户视角验收原则补齐跨项目证据后继续 `best_practice`。
- 具体 Windows 用户主目录从公开库移除。
- Compact 与主库源版本同步。
- 旧 YAML/cost 缺口由显式 `legacy-overrides.json` 管理。
- 审计中曾发生源知识过度压缩；详细正文已恢复，新增自动 Source Regression Guard。

## 知识维护不变量

事实源可纠错但不为 token 成本被压成摘要。批量维护必须 compare；20+ 行事实源一次缩水 40%+ 默认 CI 失败，除非人工确认并显式 `[allow-source-compaction]`。

## 当前知识状态

当前有 115 个核心条目：12 Project、28 Lesson、15 Pattern、19 Mistake、12 Decision、6 Anti-pattern、20 Template、3 Bad-case。

历史兼容 registry 仍会由 CI 单独报告数量；它是显式兼容层，不通过删正文来消除。

## 下一步优先级

1. 极简电视时钟按最终排他需求重建并回读远端。
2. 拾光正式版前补真实上传/恢复/权限/真机矩阵。
3. 文章货架在现有项目增量实现 readingActive，并测多标签页/idle/跨日。
4. 豆包从客户端接纳/环境生成器继续查根因。
5. 李跳跳必须取得完整原始输入和可导入 golden sample。
6. Legacy registry 在旧文件下一次实质编辑时逐步内联，不能批量破坏正文。

---

*来源：完整知识库、2026-06-26 至 08-26 两个月审计、GitHub 远端与 CI；最后深度审校：2026-08-26。*