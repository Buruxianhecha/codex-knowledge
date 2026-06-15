---
status: active
confidence: 0.9
reuse_count: 2
last_used: 2026-06-15
---

# 双副本仓库的 source of truth 检查

## Pattern

当自动化记忆或历史记录里出现一个仓库路径，但文件系统里可能存在多个副本时，先做 source of truth 校验，再进行写入。

## 步骤

1. 从记忆中拿到候选路径。
2. 用文件系统枚举实际存在的目录。
3. 用 `git status --short --branch` 确认当前分支和脏改状态。
4. 用 `git log --oneline -n 8` 确认历史连续性。
5. 只对最终确认的那一个仓库执行写入和推送。
6. 若 shell 环境变量失效，立刻回退到显式绝对路径。

## 好处

- 防止写到旧副本。
- 防止误判任务完成。
- 防止把知识沉淀到错误位置。
