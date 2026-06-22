---
status: verified
confidence: 0.93
reuse_count: 0
last_used: 2026-06-22
cross_refs:
  - lessons/2026-06-22-automation-runtime-verification.md
  - decisions/2026-06-22-automation-source-of-truth-and-command-checks.md
---

# 绝对路径优先

## Pattern

在本地自动化和知识归档流程里，优先使用绝对路径定位记忆、仓库和输出文件，只有在路径已被验证且不会漂移时，才考虑相对路径或环境变量。

## Steps

1. 先确认目标仓库的绝对路径。
2. 再确认自动化记忆和配置文件的绝对路径。
3. 对命令做可执行性检查，不把配置文本当成执行证明。
4. 只有在以上三项都确认后，才进入写入、提交、推送。

## Result

- 降低路径漂移
- 降低写错目录的概率
- 更容易定位失败点

