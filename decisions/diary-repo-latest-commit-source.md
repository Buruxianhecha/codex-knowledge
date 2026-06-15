---
status: active
date: 2026-06-15
decision_owner: codex-automation
confidence: 0.95
cross_refs:
  - lessons/automation-diary-source-of-truth.md
  - patterns/duplicate-repo-source-of-truth-check.md
---

# 自动化日记仓库以实际 Git 仓库为准

## Context

自动化记忆里可能保存旧路径，或者同名仓库存在多个副本。今天再次遇到这一类情况，而且 shell 环境变量也可能失效。

## Decision

日记仓库的最新提交源与写入目标，必须以当前文件系统里确认过的 Git 仓库为准，而不是以记忆中的路径为准。

## Reason

- 防止路径漂移。
- 防止写入旧副本。
- 防止把 `main` 分支上的连续历史写断。
- 防止在环境变量失效时误判可写路径。

## Result

今天的日记任务将继续落到 `C:\Users\吴\Documents\Codex\2026-06-02\new-chat\codex-diary`，并且显式绝对路径优先于不稳定环境变量。
