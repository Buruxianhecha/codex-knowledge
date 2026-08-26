---
status: active
confidence: 0.96
reuse_count: 2
last_used: 2026-08-26
verified_in:
  - evidence-research-workflow
  - codex-knowledge-audit
expires_after: none
cross_refs:
  - projects/2026-08-19-evidence-research-workflow.md
  - lessons/complex-prompts-are-executable-specifications.md
  - patterns/evidence-ledger-and-claim-calibration.md
---

# Pattern：多智能体共享 Schema 编排

> 并行的目标是缩短关键路径和提高互审强度，不是生成更多答案。

## 何时值得并行

适合：

- 高难度、多步骤、材料多。
- 子任务相互独立或依赖很少。
- 需要不同专业视角。
- 可以定义统一输出 Schema。

不适合：

- 几分钟内可直接完成的小任务。
- 后一步完全依赖前一步结果的强串行任务。
- 多个 Agent 只是重复阅读同一材料、没有不同职责。

## 推荐拓扑

```text
Coordinator
  |- Evidence / Retrieval Agent
  |- Engineering Agent
  |- Writing / Research Agent
  |- Risk / Counterexample Agent
  `- Index / Consistency Agent

             ↓ shared schema
        Final Auditor / Merger
```

## 共享 Schema

所有子任务至少返回：

```text
scope
facts[]
evidenceIds[]
assumptions[]
conflicts[]
proposedChanges[]
verification[]
unresolved[]
```

研究任务再加 `claimId/sourceTier/allowedWording`；工程任务再加 `file/path/test/status`。

## 依赖规则

1. 先画 dependency DAG。
2. 无依赖节点并行。
3. 有依赖节点等待最小必要输入，不等待所有 Agent 全部结束。
4. 共享的是结构化事实，不直接互抄长篇 prose。
5. 最终只由一个审计角色解决冲突、去重并生成交付物。

## 对抗式复核

至少一个 Agent 的职责应是找：

- 被遗漏的禁止项。
- 证据不足的强结论。
- 重复/矛盾条目。
- 未验证却被写成完成的事项。

## 停止条件

并行不是无限扩张。满足以下条件即可收口：

- 核心问题均有 owner。
- 关键冲突已裁决或明确保留未确认。
- 输出 Schema 完整。
- 验证矩阵有结果。
- 最终交付可以由单一审计者重建。

## 标签

#multi-agent #orchestration #parallel #shared-schema #audit