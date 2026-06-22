---
status: active
confidence: 0.93
reuse_count: 0
last_used: 2026-06-22
cross_refs:
  - lessons/2026-06-22-automation-runtime-verification.md
---

# 不要把 Windows 环境变量默认当成真

## Mistake

看到 `$CODEX_HOME`、`rtk` 之类的约定后，直接假设当前 shell 会话已经具备这些能力。

## Impact

- 可能读错记忆路径
- 可能调用不存在的命令
- 可能把后续写入和推送建立在错误上下文上

## Fix

先用绝对路径确认关键文件，再用 `Get-Command` 或同类手段验证命令存在。

