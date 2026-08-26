---
status: active
confidence: 0.88
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
next_step: v2 迁移到标准 pip/venv 依赖管理
cost:
  token_cost: none
  latency: low at first-run
  complexity: medium
  maintenance: high
  scalability: poor
cross_refs:
  - mistakes/hardcoded-paths.md
  - lessons/v1-hardening.md
---
# 依赖管理：vendoring vs pip/venv

`pdf-to-excel` v1 为降低首次安装摩擦选择 vendoring，但依赖清单不完整，环境可复现性差。后续建议迁移标准 venv 与受控依赖清单。

`status` 只表达生命周期；未来计划放在 `next_step`，不塞进状态枚举。
