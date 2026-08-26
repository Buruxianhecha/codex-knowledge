---
status: active
confidence: 0.90
reuse_count: 0
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cross_refs:
  - templates/code/sqlite-migration-helper.py
---

# 渐进式 SQLite Schema 迁移模式

> 当前只有一个独立项目应用，因此保持 `active`，不把单项目成功写成跨项目验证。

## 适用场景

使用 SQLite 等轻量数据库，需要在保留已有数据的情况下追加字段或做小型 Schema 演进。

## 基础结构

```python
def migrate(conn):
    cur = conn.execute("PRAGMA table_info(users)")
    existing_cols = {row[1] for row in cur.fetchall()}
    if "new_field" not in existing_cols:
        conn.execute(
            "ALTER TABLE users ADD COLUMN new_field TEXT DEFAULT ''"
        )
    conn.commit()
```

核心思想是“检测 -> 按需变更 -> 可重复运行”，但真正生产迁移还需要版本号、事务、备份、失败恢复和数据回填策略。

## 已知应用

- pdf-to-excel：后续增加 avatar / display_name 等字段。

## 适用边界

- SQLite 的 `ALTER TABLE` 能力有限，复杂重构可能需要新表 + 数据复制 + rename。
- 默认值、NULL、索引、外键和大量数据回填都需要单独测试。
- 迁移脚本幂等不等于业务数据迁移正确。

## 标签

#database #sqlite #migration