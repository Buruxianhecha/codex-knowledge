# Codex Knowledge Base 可移植工程经验库 v5

> 自包含、可蒸馏、可演化、可保鲜，并且明确区分已验证事实、推断、未闭环事项与历史兼容元数据。

## 快速开始

第一次打开本库时，按顺序阅读：

1. [`SYSTEM.md`](SYSTEM.md) - 五层角色与交付原则。
2. [`preferences/user-profile.md`](preferences/user-profile.md) - 用户偏好和约束。
3. [`KNOWLEDGE_INDEX.md`](KNOWLEDGE_INDEX.md) - 112 个核心条目的完整索引。
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
| 修改现有作品先保护 | 未被点名的模板、布局、数据和行为默认不动 |
| 行为时间按有效状态计量 | 页面打开时间不等于阅读/学习/工作时间 |
| 空值优于假数据 | 无法确认就留空或标注未确认 |
| 历史不删除 | 过时、冲突和失败保留状态与替代关系 |
| 变化知识要重验 | API、平台、模型、限额和安全结论按有效期复查 |
| 源知识不为审计而压缩 | Project/Lesson/Pattern 保留可复用细节，Compact 才承担压缩职责 |

## 最近两个月新增

### 写作、研究与提示词

- 洪涝救援与动物风险责任研究。
- 兔娘式直播传播机制研究。
- 数字时代亲密关系与平台陪伴研究。
- 东东式宣传、企业治理与法律风险研究。
- 豆包/千问/元宝智能体下线与平台治理研究。
- 翁家翌人物全生命周期研究。
- 18 智能体证据研究工作流。
- 大型提示词从“长文本”升级为带状态、不变量、禁止项和验收矩阵的可执行规格。

已蒸馏出来源分层、材料账本、SHA-256、命题强度、冲突裁决、引用审计、资料截止日、文档署名/元数据检查，以及多智能体共享 Schema 收口方法。

### 产品与工程

- 豆包本地工作环境编码修复及未闭环记录。
- 拾光相册 IndexedDB、本地账户隔离、Mock/Real 认证边界和上线前验收。
- 文章货架签到与真实阅读时长：以路由/前台/焦点/idle/Session 共同决定 `readingActive`，规格已明确、实现待验证。
- 抖音续火花部署包的凭据、dry-run 与未验证边界。
- 卡塞尔、剑来、凡人三个浏览器模拟器的状态机、存档、测试和 GitHub 发布。
- 极简电视时钟需求漂移失败案例。
- GitHub HEAD、Blob、CI 与上传限额的验证阶梯。

### BIM、结构图纸与约束式修改

- 梁、基础、楼板、増打、上/下端与标高输入先对齐 reference plane、offset、尺寸和局部几何。
- 断面某个増打数值不能默认扩展成全构件固定值；渐变厚度必须按实际范围表达。
- `640×950` 等截面尺寸与位置 offset 分离处理。
- 图像/模板/现有网站修改采用 Must change / Must preserve / May adapt 三层约束，不把整改任务变成重设计。

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
|-- metadata/          历史兼容元数据注册表
|-- compact/           压缩分发层
|-- scripts/           自动审计与维护脚本
|-- .github/workflows/ CI 质量门控
|-- codex-plugin-operations/
`-- artifacts/
```

## 核心条目统计

统计口径与旧版一致，只计项目、经验、模式、错误、决策、反模式、模板和失败案例。

| 类别 | 数量 |
|------|------|
| 项目 `projects/` | 12 |
| 经验 `lessons/` | 27 |
| 模式 `patterns/` | 15 |
| 错误 `mistakes/` | 18 |
| 决策 `decisions/` | 12 |
| 反模式 `anti-patterns/` | 6 |
| 模板 `templates/` | 19 |
| 失败案例 `bad-cases/` | 3 |
| 合计 | 112 |

历史条目中缺失的统一 Schema 字段由 `metadata/legacy-overrides.json` 显式管理；这不是隐藏债务。文件内 frontmatter 永远优先，旧文件再次实质修改时应逐步把兼容字段内联并移除 override。CI 会同时报告 override 数量。

## 本期关键验证状态

| 项目 | 当前状态 |
|------|----------|
| 卡塞尔开放世界模拟器 | 远端 HEAD 与 CI 成功，含长程和存档行为测试 |
| 剑来人生模拟器 | 远端 HEAD 与 CI 成功，含 VM 行为回归 |
| 凡人人界篇模拟器 | 远端 HEAD 与 CI 成功，测试深度为 smoke |
| 拾光相册 | 测试版构建和核心交互通过，暂不建议正式上线 |
| 文章货架阅读时长/签到 | 需求与状态模型已明确，未验证实现/部署 |
| BIM/结构图纸输入 | 判读方法已蒸馏，具体项目数值仍以图纸/软件为准 |
| 豆包本地环境 | 脚本层通过，客户端层未闭环 |
| 李跳跳规则修复 | 没有真实导入成功证据 |
| 抖音续火花部署包 | 包和说明存在，实际部署/运行未验证 |
| 极简电视时钟 | 远端仍不符合最终最小需求 |

## 知识生命周期

```text
raw
  -> active
  -> verified (2+ 独立证据/项目)
  -> best_practice (3+ 成功应用 + 至少 3 个月稳定)

active
  -> superseded / deprecated / archived
```

不删除旧结论。出现替代、冲突或失效时，保留历史并指向新条目。生命周期升级必须由证据支持，不能靠“看起来像最佳实践”。

## 自动审计

`scripts/validate_knowledge.py` + `.github/workflows/knowledge-audit.yml` 自动检查：

- 核心条目真实文件数与 README/Index/Bundle 统计。
- 生命周期晋升条件。
- YAML/兼容 registry 元数据完整性。
- cross_refs 与 Markdown 链接。
- Compact 源版本是否落后。
- 公开仓库中的具体用户主目录和常见凭据模式。

`errors=0` 是硬门槛；warning 表示仍有未登记债务。即使 `warnings=0`，registry 数量仍单独显示。

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
- 高复杂度任务优先按依赖图并行拆分，多智能体共享 Schema 后统一审计。
- 审计与整理不得删减事实源中的可复用上下文；需要缩短时只改 Compact/派生层。
- 推送后回读 HEAD、关键 Blob 和当前 HEAD 的 Knowledge Audit。

---

*最后更新：2026-08-26 | 核心条目：112 | 最近审计窗口：2026-06-26 至 2026-08-26*