---
status: active
confidence: 0.92
reuse_count: 3
last_used: 2026-06-21
verified_in: [codex-diary, codex-knowledge]
cross_refs:
  - patterns/duplicate-repo-source-of-truth-check.md
  - mistakes/powershell-default-encoding-false-mojibake.md
  - decisions/diary-repo-latest-commit-source.md
  - templates/operation-diary-automation-checklist.md
---

# 自动化日记必须以实际仓库为准

## 说明

自动化日记任务常常同时存在记忆、旧副本和当前工作仓库。记忆可以帮助快速定位，但不能直接当作最终事实。

## 经验

- 先读自动化记忆，只把它当作候选线索。
- 再用文件系统确认真实仓库路径。
- 如果记忆路径与实际路径冲突，必须以真实仓库为 source of truth。
- 对于跨目录迁移过的日记仓库，`git log` 和 `git status` 比目录名更可靠。
- 不要假设 `$env:CODEX_HOME` 一定可用；关键路径应保留显式回退方案。

## 适用场景

- 自动化操作日记
- 多副本仓库维护
- 需要长期追加的 Git 日记仓库
