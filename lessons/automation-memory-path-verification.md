---
status: active
confidence: 0.93
reuse_count: 0
last_used: 2026-06-21
verified_in: [codex-automation]
expires_after: none
cross_refs:
  - patterns/duplicate-repo-source-of-truth-check.md
  - decisions/automation-source-of-truth-first.md
  - mistakes/2026-06-22-env-var-assumption-on-windows.md
---

# 自动化记忆路径先验核验

## Lesson

自动化记忆不要默认依赖 `$CODEX_HOME` 拼接结果。Windows/PowerShell 下更稳的做法是先确认记忆文件的真实可访问路径，再把环境变量当成候选定位方式，而不是事实源。

## Why

- `CODEX_HOME` 可能为空或未按预期注入。
- 相对/拼接路径容易在不同 shell 上失效。
- 多副本仓库可能让“路径存在”仍然指向旧版本。
- 先核验实际 Git 状态可以减少“看起来能用、实际读错位置”的风险。

## When to use

- 读取 automation memory。
- 生成日记、知识同步、恢复上下文。
- 任意需要从本地固定位置读取长期工作状态的任务。

## Related checks

- `git status --short --branch`
- `git log --oneline -n 5`
- 目标文件是否真实存在和可读取。
- 是否存在同名仓库/旧副本。

## 行动规则

路径只是定位线索；最终 source of truth 由实际文件、Git 分支和提交历史共同确认。