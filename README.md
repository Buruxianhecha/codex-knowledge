# Codex Knowledge Base 可移植工程经验库 v6

> 自包含、可蒸馏、可演化、可保鲜，并且明确区分已验证事实、推断、未闭环事项与历史兼容元数据。

## 快速开始

第一次打开本库时，按顺序阅读：

1. [`SYSTEM.md`](SYSTEM.md) — 五层角色与交付原则。
2. [`preferences/user-profile.md`](preferences/user-profile.md) — 用户偏好和约束。
3. [`KNOWLEDGE_INDEX.md`](KNOWLEDGE_INDEX.md) — 115 个核心条目的完整索引。
4. [`memory/distilled-memory.md`](memory/distilled-memory.md) — 高密度项目和经验速查。
5. [`projects/2026-08-26-two-month-learning-audit.md`](projects/2026-08-26-two-month-learning-audit.md) — 2026-06-26 至 2026-08-26 全量审计。

涉及 API、模型、平台限额、安全或部署时，再读 [`FRESHNESS.md`](FRESHNESS.md)。知识准入和维护读 [`.value-rules.md`](.value-rules.md)、[`QUALITY.md`](QUALITY.md) 与 [`templates/checklist/knowledge-maintenance-diff-checklist.md`](templates/checklist/knowledge-maintenance-diff-checklist.md)。

## 核心原则

| 原则 | 含义 |
|------|------|
| 未验证 = 未完成 | 命令、产物、集成、宿主、用户路径分层报告 |
| 用户实际优先 | 理论与真实体验冲突时继续复现和排查 |
| 结论不超过证据 | 参与不写主导，盘中不写收盘，手册不写部署成功 |
| 排他需求是合同 | “只/仅/不要/单文件”禁止默认扩展 |
| 修改现有作品先保护 | 未点名模板、布局、数据、行为默认不动 |
| 行为时间按有效状态计量 | 页面打开时间不等于真实阅读/学习/工时 |
| 空值优于假数据 | 无法确认就留空或标注未确认 |
| 历史不删除 | 过时、冲突和失败保留状态与替代关系 |
| 变化知识要重验 | API、平台、模型、限额、安全按有效期复查 |
| 事实源不可被维护压没 | 压缩属于 Bundle/Compact；源文件批量缩水必须 diff 审核 |

## 最近两个月新增

### 写作、研究与提示词

- 洪涝救援与动物风险责任。
- 兔娘式直播传播机制。
- 数字时代亲密关系与平台陪伴。
- 东东式宣传、企业治理与法律风险。
- 平台化人工智能治理转向。
- 翁家翌人物全生命周期研究。
- 18 智能体证据研究工作流。
- 复杂提示词从“长文本”升级为状态、不变量、禁止项和验收矩阵组成的可执行规格。

### 产品、工程与自动化

- 豆包本地环境：实例脚本层通过，客户端接纳未闭环。
- 拾光相册：IndexedDB、本地 owner 隔离、Auth Provider 与 production 边界。
- 文章货架：签到事实账本 + Activity-Gated Reading Time。
- 卡塞尔/剑来/凡人：单调状态、存档、语义结算、VM/smoke/CI。
- 抖音续火花包：凭据、dry-run 与未部署边界。
- 极简电视时钟：排他需求被“善意扩展”破坏的失败案例。
- Morning Briefing：Microsoft Graph、Device Code、Task Scheduler；认证知识已在 2026-08-26 按官方文档纠错。
- Codex 插件：plugin ID / marketplace ID / runtime snapshot 分层验证。

### BIM、结构图纸与约束式修改

- 梁、基础、楼板、増打、上/下端先对齐 reference plane、offset、尺寸、局部范围。
- 断面局部 `500/50` 不自动扩展成全构件固定常数。
- `640×950` 等截面尺寸与位置偏移分开。
- Must change / Must preserve / May adapt + 未指定区域负向 diff。

### 本轮知识库审计的新经验

- 生命周期不能超前晋升：单项目 Pattern 回到 `active`。
- 隐私扫描禁止具体用户主目录。
- Legacy YAML 用显式 registry 兼容，不为“清 warning”破坏历史正文。
- 维护曾发生事实源过度压缩；已恢复详细内容并新增 Source Regression Guard。
- PowerShell 编码旧因果与 Device Code 示例被事实复核并纠正。

## 目录结构

```text
codex-knowledge/
|-- README.md
|-- SYSTEM.md
|-- QUALITY.md
|-- FRESHNESS.md
|-- KNOWLEDGE_INDEX.md
|-- KNOWLEDGE_BUNDLE.md
|-- .codex-instructions.md
|-- .value-rules.md
|-- preferences/       用户稳定偏好
|-- memory/            高密度蒸馏记忆
|-- projects/          项目与阶段审计
|-- lessons/           跨项目经验
|-- patterns/          可复用设计模式
|-- mistakes/          真实错误与根因
|-- decisions/         设计取舍与成本
|-- anti-patterns/     长期腐烂路径
|-- bad-cases/         失败输入与门控
|-- templates/         代码/配置/检查清单
|-- metadata/          历史兼容元数据
|-- compact/           压缩分发层
|-- scripts/           自动审计脚本
`-- .github/workflows/ CI 质量门控
```

## 核心条目统计

| 类别 | 数量 |
|------|------|
| 项目 `projects/` | 12 |
| 经验 `lessons/` | 28 |
| 模式 `patterns/` | 15 |
| 错误 `mistakes/` | 19 |
| 决策 `decisions/` | 12 |
| 反模式 `anti-patterns/` | 6 |
| 模板 `templates/` | 20 |
| 失败案例 `bad-cases/` | 3 |
| 合计 | 115 |

## 验证状态摘要

| 项目 | 当前状态 |
|------|----------|
| 卡塞尔开放世界模拟器 | 远端 HEAD + CI 成功，含长程/存档行为测试 |
| 剑来人生模拟器 | 远端 HEAD + CI 成功，含 VM 行为回归 |
| 凡人人界篇模拟器 | 远端 HEAD + CI 成功，但测试深度仅 smoke |
| 拾光相册 | 测试版核心交互通过；正式上传/恢复/授权/真机不足 |
| 文章货架 | 阅读时长/签到规格明确，实现/部署未验证 |
| BIM/结构输入 | 方法已蒸馏，具体数值仍依完整图纸/软件字段 |
| 豆包本地环境 | 脚本层通过，客户端层未闭环 |
| 李跳跳规则 | 无真实导入成功证据 |
| 抖音续火花 | 包存在，实际部署/运行未验证 |
| 极简电视时钟 | 远端仍未满足最终最小需求 |

## 自动审计

Knowledge Audit 现在有两层：

1. `scripts/validate_knowledge.py`：统计、生命周期、registry、断链、Compact、隐私、常见 secret。
2. `scripts/check_source_regression.py`：比较父提交，防止事实源一次性大幅缩水/删除。

Legacy metadata/cost 使用显式 registry，数量由 CI 单独报告；`warnings=0` 只表示所有已知缺口有归属，不表示历史文件已被强制重写。

## 分享方式

| 场景 | 推荐内容 |
|------|----------|
| 单文件接管 | `KNOWLEDGE_BUNDLE.md` |
| 最新完整事实 | 整个仓库 |
| 快速理解近期工作 | README + Memory + 两个月总审计 |
| 文件数/token 受限 | `compact/L1-3files/` 或 `compact/L2-7files/` |

## 维护检查

- 新项目完成后只沉淀有复用价值的项目/经验/错误/决策/模板。
- 每次写入更新 Index/Memory/Bundle，Compact 按源版本同步。
- API/平台/模型/安全知识过期后联网重验。
- 未验证项目保持未闭环状态。
- 高复杂度任务按依赖图并行，多智能体共享 Schema 后统一收口。
- 批量维护事实源前后必须 compare；大幅删除默认阻断。
- 推送后回读 master HEAD 和当前 HEAD 的 Knowledge Audit。

---

*最后更新：2026-08-26 | 核心条目：115 | 审计窗口：2026-06-26 至 2026-08-26*