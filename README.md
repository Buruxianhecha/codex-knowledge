# Codex Knowledge Base 可移植工程经验库 v5

> 自包含、可蒸馏、可演化、可保鲜、可自动审计，并区分已验证事实、推断和未闭环事项。

## 快速开始

1. [`SYSTEM.md`](SYSTEM.md)
2. [`preferences/user-profile.md`](preferences/user-profile.md)
3. [`KNOWLEDGE_INDEX.md`](KNOWLEDGE_INDEX.md)
4. [`memory/distilled-memory.md`](memory/distilled-memory.md)
5. [`projects/2026-08-26-two-month-learning-audit.md`](projects/2026-08-26-two-month-learning-audit.md)
6. [`QUALITY.md`](QUALITY.md) / [`FRESHNESS.md`](FRESHNESS.md)

单文件分享用 `KNOWLEDGE_BUNDLE.md`；有限文件上传用 `compact/`，但其 START-HERE 必须显示与主库一致的源版本。

## 核心原则

- 未验证 = 未完成；命令、产物、集成、宿主、用户路径分层。
- 用户实际优先；结论不超过证据。
- “只/仅/不要/单文件”是排他合同；修改现有作品保护未指定内容。
- 行为时间按有效状态计量；空值优于假数据。
- Pattern 至少两个独立成功项目才能 verified。
- 历史不删除：过时/冲突/失败保留状态与替代关系。
- API、平台、模型、限额和安全信息使用前重验。

## 最近两个月新增

写作/研究：洪涝责任、直播传播、数字亲密关系、企业宣传、AI 治理、翁家翌人物研究、18 智能体证据研究工作流；大型提示词升级为带状态、不变量、禁止项和验收矩阵的可执行规格。

产品/工程：豆包环境、拾光相册、文章货架签到与真实阅读时长、卡塞尔/剑来/凡人模拟器、极简电视时钟失败复盘、GitHub HEAD/Blob/CI 验证阶梯。

BIM/编辑：结构输入先对齐 reference plane/offset/尺寸/局部増打；现有网站/图片/模板采用 Must change / Must preserve / May adapt，并做负向 diff。

## 核心条目统计

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

## 自动知识审计

每次 push/PR 自动执行 `python3 scripts/validate_knowledge.py`，检查真实文件数、生命周期、元数据债务、断链、Compact 漂移、本机用户名路径和常见凭据模式。`error=0` 是硬门槛；warning 是已知历史债务。

---

*最后更新：2026-08-26 | 核心条目：112 | 审计窗口：2026-06-26 至 2026-08-26*