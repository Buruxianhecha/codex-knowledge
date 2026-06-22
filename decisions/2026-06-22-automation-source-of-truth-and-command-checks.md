---
status: active
date: 2026-06-22
decision_owner: codex-automation
confidence: 0.95
cross_refs:
  - lessons/2026-06-22-automation-runtime-verification.md
  - patterns/2026-06-22-absolute-path-first.md
---

# 自动化以路径与命令双重校验为准

## Context

本地自动化经常同时依赖仓库路径、记忆路径和外部命令。只看配置文件不够，必须同时确认路径和命令在当前会话里都可用。

## Decision

把“绝对路径确认”和“命令可执行性确认”都设为自动化前置条件。

## Reason

- 防止 `$CODEX_HOME` 这类变量在不同 shell 会话里失效。
- 防止 `rtk` 这类约定命令在当前环境里不可用。
- 防止进入写文件阶段后才发现上下文错误。

## Result

自动化执行顺序变成：路径确认 -> 命令确认 -> 写入 -> 提交 -> 推送。

