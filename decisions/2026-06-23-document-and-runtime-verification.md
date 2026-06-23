---
status: active
date: 2026-06-23
decision_owner: codex-automation
confidence: 0.95
cross_refs:
  - lessons/2026-06-23-automation-environment-and-command-verification.md
  - patterns/2026-06-23-absolute-path-first-for-automation.md
---

# 文档约定与运行时事实必须双重验证

## Context

本地自动化会同时依赖文档约定、环境变量和外部命令。只确认其中一项不够，尤其是在 Windows 会话里，很多“应该可用”的东西实际上并没有加载。

## Decision

把“文档确认”和“运行时验证”都设为前置条件，不再只看配置文件或说明文档。

## Reason

- `CODEX_HOME` 可能在当前 shell 中为空。
- `rtk` 可能在文档里存在，但在当前会话里不可执行。
- 先确认真实状态，能避免后续所有写入动作建立在错误前提上。

## Result

自动化顺序固定为：绝对路径确认 -> 命令可执行性确认 -> 写入 -> 提交 -> 推送。
