---
status: verified
since: 2026-05-25
verified_in: [pdf-to-excel]
cross_refs:
  - lessons/quality-gating.md (经验版)
  - lessons/empty-over-fake.md (相关原则)
---
# 输出质量门控模式

## 适用场景
AI/ML/OCR 管道产出结果不确定，需要在输出前过滤低质量结果。

## 结构
```
原始数据 → 质量门控(填充率/结构完整性/异常检测) → 合格数据 → 输出
                                              → 不合格 → 丢弃/降级
```

## 代码示例
```python
def quality_gate(data, min_fill_rate=0.2, min_cols=2, min_rows=2):
    total_cells = sum(len(row) for row in data)
    filled_cells = sum(1 for row in data for c in row if c)
    if total_cells == 0:
        return False
    if filled_cells / total_cells < min_fill_rate:
        return False
    if len(data[0]) < min_cols or len(data) < min_rows:
        return False
    return True
```

## 已知应用
- pdf-to-excel: `_valid()` 函数

## 标签
#quality #ai-pipeline #validation

