---
status: active
confidence: 0.93
reuse_count: 0
last_used: 2026-06-23
cross_refs:
  - lessons/2026-06-23-automation-environment-and-command-verification.md
---

# 不要把 shell 变量当成默认真值

## Mistake

一开始把 `$env:CODEX_HOME` 和 `rtk` 当成当前 shell 的可用前提，实际上它们都没有被确认可用。

## Impact

- 容易把路径定位和命令执行建立在错误前提上。
- 容易让自动化在一开始就偏离真实运行环境。
- 增加排障成本，尤其是在多会话、跨目录的 Windows 环境里。

## Fix

先用绝对路径和 `Get-Command` / 直接执行去验证，再决定是否依赖变量或约定命令。
