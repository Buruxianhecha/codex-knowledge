---
status: active
confidence: 0.90
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cost: high
cross_refs:
  - decisions/ocr-parallel-vs-sequential.md
  - lessons/quality-gating.md
  - templates/code/multi-engine-extractor.py
---

# 多引擎并行择优模式

> 当前只有一个独立项目应用，因此保持 `active`，不升级为 `verified`。

## 适用场景

有多种算法/引擎/模型都能处理同一输入，但输出质量取决于输入特征，无法预先判断哪个最好。

## 结构

```text
输入 -> [引擎A] -> 评分A ┐
     -> [引擎B] -> 评分B ├-> 选择最优 -> 输出
     -> [引擎C] -> 评分C ┘
```

## 代码示例

```python
class MultiEngineExtractor:
    def __init__(self):
        self.engines = []

    def register(self, engine, cost=1):
        self.engines.append((engine, cost))

    def extract(self, input_data):
        best_result, best_score = None, float("-inf")
        for engine, cost in self.engines:
            result = engine.run(input_data)
            score = engine.evaluate(result) / max(cost, 1e-9)
            if score > best_score:
                best_result, best_score = result, score
        return best_result
```

示例只是模式骨架；真实系统应决定引擎是串行还是并行执行，并明确失败、超时、预算和质量评分的口径。

## 已知应用

- pdf-to-excel：pdfplumber / Tesseract / PaddleOCR / GPT Vision 多路提取后择优。

## 成本与边界

- 多引擎通常提高覆盖率，但增加 CPU/GPU/API 成本和延迟。
- “全跑再选”不是永远正确；若前置引擎已达到质量阈值，可跳过昂贵模型。
- 评分函数比引擎数量更关键；错误评分会稳定地选错结果。

## 标签

#architecture #ocr #multi-model #selection