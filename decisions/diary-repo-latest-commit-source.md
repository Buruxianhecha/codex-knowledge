---
status: active
date: 2026-06-15
decision_owner: codex-automation
confidence: 0.95
reuse_count: 2
last_used: 2026-06-28
verified_in:
  - automation-diary-workflow
  - codex-knowledge-audit
expires_after: none
cost:
  token_cost: low
  latency: low
  complexity: low
  maintenance: low
  scalability: moderate
cross_refs:
  - lessons/automation-diary-source-of-truth.md
  - patterns/duplicate-repo-source-of-truth-check.md
  - decisions/automation-source-of-truth-first.md
---
# 自动化日记仓库以实际 Git 仓库为准

最新提交源与写入目标必须以当前文件系统中确认过的 Git 仓库、默认分支和 HEAD 为准，而不是以旧记忆路径为准。

历史工作流曾使用用户目录下的 Codex 工作区；公开知识只保留 `%USERPROFILE%\Documents\Codex\...` 这一泛化表示，不记录具体 Windows 用户名。
