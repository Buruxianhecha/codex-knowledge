---
status: superseded
confidence: 0.93
reuse_count: 0
last_used: 2026-06-22
verified_in: []
expires_after: none
replaced_by: patterns/2026-06-28-absolute-path-first-and-runtime-check.md
cross_refs:
  - lessons/2026-06-22-automation-runtime-verification.md
  - decisions/2026-06-22-automation-source-of-truth-and-command-checks.md
---

# 绝对路径优先

> 历史模式。核心思想仍有效，但已被“绝对路径 + 运行时检查”取代。

## Pattern

在本地自动化和知识归档流程里，优先使用已验证的明确路径定位记忆、仓库和输出文件，不依赖不稳定的当前工作目录。

## Steps

1. 先确认目标仓库的实际位置。
2. 再确认自动化记忆和配置文件的位置。
3. 对命令做可执行性检查，不把配置文本当成执行证明。
4. 只有以上确认后才进入写入、提交、推送。

## 为什么被替代

单纯“绝对路径优先”仍可能把过期副本或失效命令当真。06-28 版本把 source-of-truth 与运行时命令验证一起纳入流程，因此本条目不再单独作为推荐模式。

## Result

- 降低路径漂移。
- 降低写错目录概率。
- 为后续更完整的运行时核验模式提供了前身。