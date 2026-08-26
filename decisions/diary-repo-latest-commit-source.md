---
status: active
date: 2026-06-15
decision_owner: codex-automation
confidence: 0.95
reuse_count: 2
last_used: 2026-08-26
verified_in:
  - automation-diary-workflow
  - codex-knowledge-audit
expires_after: none
cost:
  token_cost: low
  latency: low
  complexity: low
  maintenance: low
  scalability: moderate
cross_refs:
  - lessons/automation-diary-source-of-truth.md
  - patterns/duplicate-repo-source-of-truth-check.md
  - decisions/automation-source-of-truth-first.md
  - lessons/automation-memory-path-verification.md
---

# 自动化日记仓库以实际 Git 仓库为准

## Context

自动化记忆里可能保存旧路径，同名仓库也可能有多个副本；环境变量、默认工作目录和历史聊天中的路径都可能漂移。

## Decision

日记仓库的读取源和写入目标必须以当前实际文件系统与 Git 状态共同确认，而不是以记忆中的路径字符串为准。

## Source-of-truth 检查

```text
候选路径
-> 路径真实存在
-> .git / remote 正确
-> 当前 branch 正确
-> git status 符合预期
-> git log 与已知历史连续
-> remote HEAD / 最近提交一致
-> 才允许写入
```

如果有两个同名副本，仅“文件更新时间更新”也不够；要比较 remote、branch、HEAD、历史连续性和预期文件。

## Reason

- 防止路径漂移。
- 防止写入旧副本。
- 防止把连续日记历史写到错误分支。
- 防止环境变量失效时误判可写位置。
- 防止把用户已经存在的脏改动错误归因于本次自动化。

## Result

自动化流程固定为：

```text
恢复候选上下文
-> 核验真实仓库
-> 读取最新 Git 状态
-> 增量写入
-> diff 检查
-> commit/push
-> 远端回读 HEAD
```

历史上工作区曾位于用户目录下的 Codex 路径；公开知识只保留 `%USERPROFILE%\Documents\Codex\...` 或 `<WORKSPACE>` 这种泛化表达。

## Cost

前置检查增加少量命令和时间，但相比把知识写到旧仓库后再追溯/迁移，成本很低。

## 标签

#decision #automation #git #source-of-truth #repository