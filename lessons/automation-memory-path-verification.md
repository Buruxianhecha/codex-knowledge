---
status: active
confidence: 0.94
reuse_count: 1
last_used: 2026-06-28
verified_in: [automation-diary-workflow]
expires_after: none
cross_refs:
  - patterns/2026-06-28-absolute-path-first-and-runtime-check.md
  - decisions/automation-source-of-truth-first.md
  - mistakes/2026-06-22-env-var-assumption-on-windows.md
---
# 自动化记忆路径先验核验

自动化记忆不要默认依赖 `$CODEX_HOME` 或旧会话路径。先在当前文件系统确认目标文件和仓库，再把环境变量/记忆路径当线索。

绝对路径本身也会过期，所以需要 source-of-truth 与运行时能力双重核验。
