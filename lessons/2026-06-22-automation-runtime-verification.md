---
status: active
confidence: 0.94
reuse_count: 0
last_used: 2026-06-22
cross_refs:
  - patterns/2026-06-22-absolute-path-first.md
  - decisions/2026-06-22-automation-source-of-truth-and-command-checks.md
  - mistakes/2026-06-22-env-var-assumption-on-windows.md
---

# 自动化运行前先验命令可执行性

## Lesson

在 Windows 自动化里，文档里写了某个工具或环境变量，不代表当前 shell 会话里真的可用。执行前先验证命令存在，再决定是否依赖该工具。

## Why

- 避免把“约定”误当成“运行时事实”。
- 减少因为 PATH、会话隔离或插件状态不同而导致的失败。
- 让回退策略更早生效，不把问题拖到写文件或推送阶段。

## When to use

- 自动化脚本启动前
- 依赖 `rtk`、`CODEX_HOME`、其他本地运行时工具时
- 任何需要在本机持续写入、提交、推送的流程里

