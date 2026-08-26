---
status: active
date: 2026-06-21
decision_owner: codex-automation
confidence: 0.94
reuse_count: 1
last_used: 2026-06-28
verified_in: [automation-diary-workflow]
expires_after: none
cost:
  token_cost: low
  latency: low
  complexity: low
  maintenance: low
  scalability: moderate
cross_refs:
  - lessons/automation-memory-path-verification.md
  - patterns/2026-06-28-absolute-path-first-and-runtime-check.md
---
# 自动化任务优先核验 source of truth

处理操作日记、知识同步和上下文恢复时，先核验当前 source of truth，再写入和推送。旧记忆路径、环境变量和终端显示只能作为线索，不作为当前事实。
