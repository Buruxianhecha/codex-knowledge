---
status: active
since: 2026-05-25
cross_refs:
  - patterns/output-quality-gate.md (可复用模式)
  - lessons/empty-over-fake.md (相关原则)
---
# 输出质量门控比完美解析更重要

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
OCR 引擎经常产出"看起来像表格但不是"的垃圾数据。如果把垃圾直接输出给用户，信任度会崩塌。

## 经验
在数据进入最终输出前设一道质量门控（填充率、列数、唯一值数量），宁可少输出也不输出错误。

```python
def _valid(data):
    # 填充率 < 20% 且少于4行 → 丢弃
    if fill_rate < 0.2 and len(data) < 4:
        return False
    # 单列表超过30行 → 可能是文本流，丢弃
    if len(data) > 30 and len(data[0]) == 1:
        return False
```

## 行动指南
任何 AI/ML 输出管道都要加质量门控。门控规则应该从实际 bad case 中提炼。

## 标签
#quality #ocr #data-validation

