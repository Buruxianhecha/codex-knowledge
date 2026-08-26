# Codex Knowledge Base 可移植工程经验库 v3

> 自包含、可蒸馏、可演化、可保鲜，并且明确区分已验证事实、推断和未闭环事项。

## 快速开始

第一次打开本库时，按顺序阅读：

1. [`SYSTEM.md`](SYSTEM.md) - 五层角色与交付原则。
2. [`preferences/user-profile.md`](preferences/user-profile.md) - 用户偏好和约束。
3. [`KNOWLEDGE_INDEX.md`](KNOWLEDGE_INDEX.md) - 101 个核心条目的完整索引。
4. [`memory/distilled-memory.md`](memory/distilled-memory.md) - 高密度项目和经验速查。
5. [`projects/2026-08-26-two-month-learning-audit.md`](projects/2026-08-26-two-month-learning-audit.md) - 2026-06-26 至 2026-08-26 的全量审计。

涉及 API、模型、平台限额、安全或部署时，再读 [`FRESHNESS.md`](FRESHNESS.md)。决定内容是否入库时读 [`.value-rules.md`](.value-rules.md) 和 [`QUALITY.md`](QUALITY.md)。

## 核心原则

| 原则 | 含义 |
|------|------|
| 未验证 = 未完成 | 命令、产物、集成、宿主和用户路径分层报告 |
| 用户实际优先 | 理论与真实体验冲突时继续复现和排查 |
| 结论不超过证据 | 参与不写主导，盘中不写收盘，手册不写部署成功 |
| 排他需求是合同 | “只/仅/不要/单文件”禁止默认扩展 |
| 空值优于假数据 | 无法确认就留空或标注未确认 |
| 历史不删除 | 过时、冲突和失败保留状态与替代关系 |
| 变化知识要重验 | API、平台、模型、限额和安全结论按有效期复查 |

## 最近两个月新增

### 写作与研究

- 洪涝救援与动物风险责任研究。
- 兔娘式直播传播机制研究。
- 数字时代亲密关系与平台陪伴研究。
- 东东式宣传、企业治理与法律风险研究。
- 豆包/千问/元宝智能体下线与平台治理研究。
- 翁家翌人物全生命周期研究。
- 18 智能体证据研究工作流。

已蒸馏出来源分层、材料账本、SHA-256、命题强度、冲突裁决、引用审计、资料截止日和文档署名/元数据检查。

### 产品与工程

- 豆包本地工作环境编码修复及未闭环记录。
- 拾光相册 IndexedDB、本地账户隔离、Mock/Real 认证边界和上线前验收。
- 抖音续火花部署包的凭据、dry-run 与未验证边界。
- 卡塞尔、剑来、凡人三个浏览器模拟器的状态机、存档、测试和 GitHub 发布。
- 极简电视时钟需求漂移失败案例。
- GitHub HEAD、Blob、CI 与上传限额的验证阶梯。

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
|-- preferences/       用户偏好
|-- memory/            高密度蒸馏记忆
|-- projects/          项目与阶段审计
|-- lessons/           跨项目经验
|-- patterns/          可复用设计模式
|-- mistakes/          真实错误与根因
|-- decisions/         设计取舍与后果
|-- anti-patterns/     长期腐烂路径
|-- bad-cases/         失败输入与门控规则
|-- templates/         代码、配置和检查清单
|-- compact/           压缩分发层
|-- codex-plugin-operations/
`-- artifacts/
```

## 核心条目统计

统计口径与旧版一致，只计项目、经验、模式、错误、决策、反模式、模板和失败案例。

| 类别 | 数量 |
|------|------|
| 项目 `projects/` | 10 |
| 经验 `lessons/` | 23 |
| 模式 `patterns/` | 13 |
| 错误 `mistakes/` | 18 |
| 决策 `decisions/` | 12 |
| 反模式 `anti-patterns/` | 6 |
| 模板 `templates/` | 16 |
| 失败案例 `bad-cases/` | 3 |
| 合计 | 101 |

部分 2026-06-22 至 2026-06-28 的历史增量条目没有完整 YAML 元数据，已在索引中标为“历史元数据待补”。新条目全部按 `QUALITY.md` 写入状态、置信度、复用次数、最后使用时间和交叉引用。

## 本期关键验证状态

| 项目 | 当前状态 |
|------|----------|
| 卡塞尔开放世界模拟器 | 远端 HEAD 与 CI 成功，含长程和存档行为测试 |
| 剑来人生模拟器 | 远端 HEAD 与 CI 成功，含 VM 行为回归 |
| 凡人人界篇模拟器 | 远端 HEAD 与 CI 成功，测试深度为 smoke |
| 拾光相册 | 测试版构建和核心交互通过，暂不建议正式上线 |
| 豆包本地环境 | 脚本层通过，客户端层未闭环 |
| 李跳跳规则修复 | 没有真实导入成功证据 |
| 抖音续火花部署包 | 包和说明存在，实际部署/运行未验证 |
| 极简电视时钟 | 远端仍不符合最终最小需求 |

## 知识生命周期

```text
raw
  -> active
  -> verified (2+ 项目或反复验证)
  -> best_practice (3+ 项目且至少 3 个月稳定)

active
  -> superseded / deprecated / archived
```

不删除旧结论。出现替代、冲突或失效时，保留历史并指向新条目。

## 分享方式

| 场景 | 推荐内容 |
|------|----------|
| 任意 AI 单文件输入 | `KNOWLEDGE_BUNDLE.md` |
| 需要最新完整知识 | 整个仓库 |
| 快速了解用户与近期工作 | `README.md` + `memory/distilled-memory.md` + 两个月总审计 |
| 有限文件上传 | `compact/L1-3files/` 或 `compact/L2-7files/` |

## 维护检查

- 新项目完成后写项目、经验、错误、决策和模板的必要子集。
- 每次写入更新索引和蒸馏记忆。
- 每 5 个项目或每月检查重复、过期和状态升级。
- 技术事实过期后重验，不复制旧截图。
- 对未验证项目保留未闭环状态，不用乐观措辞覆盖。

---

*最后更新：2026-08-26 | 核心条目：101 | 最近审计窗口：2026-06-26 至 2026-08-26*
