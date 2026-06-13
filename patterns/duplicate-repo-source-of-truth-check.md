---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-06-13
verified_in: [codex-diary]
cross_refs:
  - lessons/automation-diary-source-of-truth.md
  - decisions/diary-repo-latest-commit-source.md
---

# 重复仓库 Source-of-Truth 检查模式

## 适用场景

本机出现多个同名或同远端仓库副本，需要决定本次应该在哪一个副本继续写入。

## 流程

```text
候选仓库列表
  -> 读取自动化 memory / 最近任务记录
  -> 对每个仓库查 git log 和 git status
  -> 匹配上次有效提交
  -> 确认当前分支与 origin 同步
  -> git pull --ff-only
  -> 只在最新副本写入
```

## 判断标准

- 优先级一：包含上次有效提交。
- 优先级二：当前分支与远端同步。
- 优先级三：工作区干净。
- 优先级四：路径与自动化 memory 一致。

## 反例

只因为某个路径更短、目录名更熟悉、修改时间更新，就把它当作写入目标。这会导致历史断裂，甚至把新日记提交到落后副本。

## 验证命令

```powershell
rtk git -C '<repo>' status --short --branch
rtk git -C '<repo>' log --oneline --decorate -n 8
rtk git -C '<repo>' pull --ff-only
```

## 标签

#git #automation #source-of-truth #workflow

