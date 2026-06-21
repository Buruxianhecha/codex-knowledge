---
status: active
date: 2026-06-21
decision_owner: codex-automation
confidence: 0.94
cross_refs:
  - lessons/automation-memory-path-verification.md
  - patterns/automation-source-of-truth-check.md
---

# 自动化任务优先核验 source of truth

## Context

在 Windows 自动化与知识沉淀任务里，路径、仓库和记忆文件都可能因为环境变量、旧路径或编码显示问题而偏离真实状态。

## Decision

以后处理操作日记、知识同步和上下文恢复时，先核验 source of truth，再开始写入和推送。

## Reason

- 防止沿用失效路径。
- 防止把用户已有变更误判成本次输出。
- 防止由于终端显示问题造成“看起来正确”的错误结论。

## Result

日记与知识库写入流程改为：核验 -> 记录 -> 增量沉淀 -> 再推送。
