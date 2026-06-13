---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-06-13
verified_in: [codex-diary, codex-knowledge]
cross_refs:
  - patterns/duplicate-repo-source-of-truth-check.md
  - mistakes/powershell-default-encoding-false-mojibake.md
  - decisions/diary-repo-latest-commit-source.md
  - templates/operation-diary-automation-checklist.md
---

# 自动化写日记前先确认仓库源头

## 来源

- 日期：2026-06-13
- 场景：`写操作日记提醒` 自动化运行时，本机同时存在两个 `codex-diary` 仓库副本。

## 经验

自动化不能只根据目录名选择写入目标。日记、知识库、备份库这类长期仓库一旦出现多个本地副本，真正的 source of truth 应由“上次提交 + 远端同步状态 + 自动化记忆”共同决定。

## 操作模式

1. 先读自动化 memory，提取上次有效提交、路径和时间。
2. 对候选仓库执行 `git status --short --branch`。
3. 对候选仓库执行 `git log --oneline -n 8`。
4. 选择包含上次有效提交且与远端同步的仓库。
5. 写入前执行 `git pull --ff-only`。
6. 读中文 Markdown 或 TOML 时显式指定 UTF-8，避免把终端显示问题误判为文件损坏。

## 本次验证

- `D:\CodexPlusPlus\codex-diary` 停在 `b493862 Add 2026-06-11 operation diary`。
- `C:\Users\吴\Documents\Codex\2026-06-02\new-chat\codex-diary` 停在 `9dc2b6a Add 2026-06-12 operation diary`。
- 自动化 memory 也指向 `9dc2b6a`，因此后者是本次写入目标。

## 规则

当自动化任务涉及“继续更新长期仓库”时，先确认仓库源头，再写文件。不要让当前工作目录决定长期数据的归属。

## 标签

#automation #diary #git #source-of-truth #windows
