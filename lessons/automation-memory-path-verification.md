---
status: active
confidence: 0.93
reuse_count: 0
last_used: 2026-06-21
cross_refs:
  - patterns/automation-source-of-truth-check.md
  - decisions/automation-source-of-truth-first.md
  - mistakes/env-var-assumption-on-windows.md
---

# 自动化记忆路径先验核验

## Lesson

自动化记忆不要默认依赖 `$CODEX_HOME` 拼接结果。Windows/PowerShell 下更稳的做法是先确认记忆文件的绝对路径，再把环境变量当成可选回退。

## Why

- `CODEX_HOME` 可能为空或未按预期注入。
- 相对/拼接路径容易在不同 shell 上失效。
- 先核验绝对路径可以减少“看起来能用、实际读错位置”的风险。

## When to use

- 读取 automation memory。
- 生成日记、知识同步、恢复上下文。
- 任意需要从本地固定路径读取工作状态的任务。

## Related checks

- `git status --short --branch`
- `git log --oneline -n 5`
- 记忆文件是否位于真实可访问的绝对路径
