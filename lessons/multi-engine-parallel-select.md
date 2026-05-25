---
status: active
confidence: 0.85
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
---
# 多引擎并行择优 > 串行降级

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
需要从多种 OCR 引擎中选择最佳结果。传统做法是串行：A 失败→B 失败→C。但 OCR 没有"绝对失败"，只有"产出质量不同"。

## 经验
不是"前面的失败才用后面的"，而是"全跑一遍，按列数（结构化程度）选最优"。

```python
# 好的做法
for engine in engines:
    result = engine.run(pdf)
    if result.cols > best_cols:
        best = result
return best
```

## 行动指南
当多个方案都能产出结果但质量不同时，优先并行评估而非串行降级。设计一个可量化的评分函数（列数、填充率、置信度）。

## 标签
#ocr #architecture #algorithm-design


