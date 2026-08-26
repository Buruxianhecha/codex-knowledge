---
status: active
confidence: 0.94
reuse_count: 1
last_used: 2026-08-25
verified_in: [jianlai-life-simulator]
expires_after: none
cost:
  token_cost: low
  latency: fast
  complexity: moderate
  maintenance: high
  scalability: limited
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - patterns/single-file-app-vm-test-harness.md
  - lessons/interactive-systems-need-semantic-invariants.md
---

# 发布前先定向修复单文件基线，再做结构重构

## 背景

剑来人生模拟器可恢复的 v4.6 基线是约 1.5 MB 的单文件 HTML，已经包含 UI、剧情和完整多存档。当前需求只要求修复固定选项公式、自由行动和五身份开场，并公开发布。

## 方案

| 方案 | 优点 | 风险 |
|------|------|------|
| A. 在单文件上定向修复 | 保留已知 UI/存档，回归面可控，可直接离线发布 | 文件继续偏大，长期维护成本高 |
| B. 先拆模块再修玩法 | 结构更清楚 | 同时改变加载、作用域、存档和发布方式，回归面大 |
| C. 原样发布 v4.6 | 最快 | 重新发布用户已指出的核心问题 |

## 决策

采用 A：保持发布形态，新增语义结算和身份序章，用 Node VM 测试壳覆盖真实状态逻辑。

## 决策边界

这不是“永远不重构”。适用条件是：

- 有可工作的历史基线。
- 用户要求是局部行为修复。
- 发布窗口短。
- 可以用测试约束新逻辑。
- 当前重构不会立刻消除用户可见风险。

如果后续持续扩展数据、多人协作或同类逻辑再次重复，应单独安排模块化迁移。

## 后果

- 成功保住离线单文件交付和旧存档。
- 测试能覆盖语义、随机和存档行为。
- 核心代码仍在大型 HTML，维护债务保留。
- 未来拆分必须以当前测试作为迁移合同。

## 标签

#decision #refactoring #single-file #release-risk #legacy
