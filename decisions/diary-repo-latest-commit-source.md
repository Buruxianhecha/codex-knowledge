---
status: active
date: 2026-06-13
decision_owner: codex-automation
confidence: 0.92
cross_refs:
  - lessons/automation-diary-source-of-truth.md
  - patterns/duplicate-repo-source-of-truth-check.md
---

# 日记仓库以最新提交副本为本次写入源头

## 背景

`写操作日记提醒` 自动化运行时发现两个 `codex-diary` 本地副本：

- `D:\CodexPlusPlus\codex-diary`
- `C:\Users\吴\Documents\Codex\2026-06-02\new-chat\codex-diary`

两者远端均为 `https://github.com/Buruxianhecha/codex-diary.git`。

## 决策

2026-06-13 本次日记写入 `C:\Users\吴\Documents\Codex\2026-06-02\new-chat\codex-diary`。

## 原因

- 该副本包含最新提交 `9dc2b6a Add 2026-06-12 operation diary`。
- 自动化 memory 指向同一提交和路径。
- 该副本 `main...origin/main` 干净同步。
- `D:\CodexPlusPlus\codex-diary` 落后到 2026-06-11 的提交 `b493862`。

## 后果

- 今天的日记历史会接在 2026-06-12 之后，不会断链。
- 后续应更新或废弃 `D:\CodexPlusPlus\codex-diary`，避免重复判断。

## 标签

#decision #codex-diary #git #automation

