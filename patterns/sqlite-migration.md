---
status: active
confidence: 0.90
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cross_refs:
  - templates/code/sqlite-migration-helper.py
---
# 渐进式 SQLite Schema 迁移模式

检测现有 Schema -> 按需迁移 -> 幂等执行，而不是版本变化就删库重建。

## 已知应用
- `pdf-to-excel`：后续增加字段。

## 状态说明
只有一个独立项目记录，暂保持 `active`；第二个独立成功应用后再评估 `verified`。
