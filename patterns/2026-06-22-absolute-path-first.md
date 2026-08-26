---
status: superseded
confidence: 0.94
reuse_count: 1
last_used: 2026-06-28
verified_in:
  - automation-diary-workflow
expires_after: none
replaced_by: patterns/2026-06-28-absolute-path-first-and-runtime-check.md
cross_refs:
  - lessons/2026-06-22-automation-runtime-verification.md
  - decisions/2026-06-22-automation-source-of-truth-and-command-checks.md
  - patterns/2026-06-28-absolute-path-first-and-runtime-check.md
---

# 绝对路径优先

> 历史版本。该模式已被“绝对路径优先 + 运行时检查”替代；保留用于解释演化过程。

## Pattern

在本地自动化和知识归档流程里，优先使用已经核验存在的绝对路径定位记忆、仓库和输出文件，不依赖不稳定的当前工作目录。

## 为什么被替代

仅强调“绝对路径”仍可能把已经过期的绝对路径当事实。2026-06-28 的新模式增加了 source-of-truth 与当前 shell 能力核验，因此更完整。
