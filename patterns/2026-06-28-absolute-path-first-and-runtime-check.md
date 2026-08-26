---
status: active
confidence: 0.95
reuse_count: 1
last_used: 2026-06-28
verified_in:
  - automation-diary-workflow
expires_after: none
cross_refs:
  - patterns/2026-06-22-absolute-path-first.md
  - patterns/2026-06-23-absolute-path-first-for-automation.md
  - lessons/2026-06-28-runtime-verification-before-workflow-claims.md
  - decisions/2026-06-28-fallback-to-native-powershell-when-rtk-missing.md
---

# 绝对路径优先与运行时检查流程

## 核心思想

绝对路径只能减少当前目录漂移，不能证明路径仍然有效。可靠流程是：先找到当前 source of truth，再核验路径和运行时能力。

## 流程

1. 读取旧记忆作为线索，但不把旧路径直接当事实。
2. 在当前文件系统确认真实仓库和目标文件。
3. 使用已确认的绝对路径，不依赖当前目录。
4. 验证当前 shell 中相关命令是否真的存在。
5. 命令缺失时切回已验证备用方案。
6. 只修改本次任务相关文件，避免覆盖已有脏改动。

## 状态边界

当前只有自动化日记工作流这一类应用记录，因此保持 `active`；获得第二个独立成功项目后再评估 `verified`。
