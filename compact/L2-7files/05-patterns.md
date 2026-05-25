# Patterns — 可复用模式

---

## 1. 多引擎并行择优 [verified]

```
输入 → [引擎A]→评分A ┐
     → [引擎B]→评分B ├→ max(评分) → 输出
     → [引擎C]→评分C ┘
```
适用: 多种算法都能处理同一输入，质量取决于输入特征。
代码: MultiEngineExtractor 类（见 templates）

---

## 2. 渐进式 Schema 迁移 [verified]

```python
def migrate():
    cols = [row[1] for row in conn.execute("PRAGMA table_info(t)")]
    if "new_field" not in cols:
        conn.execute("ALTER TABLE t ADD COLUMN new_field TEXT DEFAULT ''")
```
检测→按需添加，幂等安全。不丢数据，可渐进升级。

---

## 3. 轻量 Supabase REST 客户端

不用官方 SDK，httpx 直接调 REST API。自己管理 anon key 和 Bearer token。
Auth + Storage + DB 全覆盖，零额外依赖。

---

## 4. 输出质量门控 [verified]

原始数据 → 质量检查(填充率/结构完整性/异常检测) → 合格输出 / 不合格丢弃。
适用于所有 AI/ML/OCR 管道。
