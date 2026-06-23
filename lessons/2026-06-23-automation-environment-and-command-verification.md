---
status: active
confidence: 0.94
reuse_count: 0
last_used: 2026-06-23
cross_refs:
  - patterns/2026-06-23-absolute-path-first-for-automation.md
  - decisions/2026-06-23-document-and-runtime-verification.md
  - mistakes/2026-06-23-not-trusting-shell-variables.md
---

# 自动化先验环境与命令可执行性

## Lesson

在 Windows 自动化里，任何本地约定都要先验证当前 shell 是否真能执行。环境变量、命令别名和文档说明都不能直接当作运行时事实。

## Why

- 降低因为会话差异导致的失败概率。
- 让回退策略尽早发生，而不是等到写文件或推送阶段才报错。
- 避免把“应该存在”误判成“已经可用”。

## When to use

- 自动化启动前。
- 依赖 `CODEX_HOME`、`rtk`、仓库路径、知识库路径时。
- 任何需要持续写入、提交、推送的本地流程。
