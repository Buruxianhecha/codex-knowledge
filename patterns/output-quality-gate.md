---
status: active
confidence: 0.90
reuse_count: 0
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cross_refs:
  - lessons/quality-gating.md
  - lessons/empty-over-fake.md
---

# 输出质量门控模式

> 当前只有一个独立项目应用，因此保持 `active`。

## 适用场景

AI/ML/OCR 管道产出结果不确定，需要在输出前过滤低质量结果。

## 结构

```text
原始数据 -> 质量门控(填充率/结构完整性/异常检测) -> 合格 -> 输出
                                               -> 不合格 -> 留空/降级/人工复核
```

## 代码示例

```python
def quality_gate(data, min_fill_rate=0.2, min_cols=2, min_rows=2):
    if not data:
        return False
    total_cells = sum(len(row) for row in data)
    filled_cells = sum(1 for row in data for c in row if c)
    if total_cells == 0:
        return False
    if filled_cells / total_cells < min_fill_rate:
        return False
    if len(data) < min_rows:
        return False
    if max((len(row) for row in data), default=0) < min_cols:
        return False
    return True
```

真实规则应从 bad case 反推，不把示例阈值当通用常数。

## 已知应用

- pdf-to-excel：`_valid()` 一类输出过滤。

## 边界

门控不能创造正确数据；它只能拒绝明显低质量结果。宁可留空也不要用“0/N/A/猜测值”伪装确定性。

## 标签

#quality #ai-pipeline #validation