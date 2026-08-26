---
status: active
confidence: 0.90
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cost:
  token_cost: low
  latency: varies
  complexity: simple
  maintenance: low
  scalability: scalable
cross_refs:
  - lessons/multi-engine-parallel-select.md
  - decisions/ocr-parallel-vs-sequential.md
  - templates/code/multi-engine-extractor.py
---
# 多引擎并行择优模式

多个引擎都能产出时并行执行，使用可量化质量/成本评分选择结果，而不是简单“失败后降级”。

## 已知应用
- `pdf-to-excel`：多种 OCR/解析引擎择优。

## 状态说明
只有一个独立项目有明确成功应用记录，因此保持 `active`，不提前标记 `verified`。
