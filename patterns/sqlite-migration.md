---
status: verified
since: 2026-05-25
verified_in: [pdf-to-excel]
cross_refs:
  - templates/code/sqlite-migration-helper.py (可复用代码)
---
# 渐进式 Schema 迁移模式

## 适用场景
使用 SQLite 等轻量数据库，需要在不停机不丢数据的情况下加字段。

## 结构
```python
def migrate():
    cur = conn.execute("PRAGMA table_info(users)")
    existing_cols = [row[1] for row in cur.fetchall()]
    if "new_field" not in existing_cols:
        conn.execute("ALTER TABLE users ADD COLUMN new_field TEXT DEFAULT ''")
```

检测→按需添加，幂等操作（多次运行安全）。

## 代码示例
见 pdf-to-excel database.py `migrate_add_profile()`

## 已知应用
- pdf-to-excel: avatar 和 display_name 字段后续添加

## 标签
#database #sqlite #migration

