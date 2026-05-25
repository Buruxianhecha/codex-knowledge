---
status: verified
confidence: 0.9
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
cost:
  token_cost: low (代码模式，无 API 调用)
  latency: varies (取决于引擎数量)
  complexity: simple
  maintenance: low
  scalability: scalable
---
# 多引擎并行择优模式

## 适用场景
有多种算法/引擎/模型都能处理同一输入，但输出质量取决于输入特征，无法预先判断哪个最好。

## 结构
```
输入 → [引擎A] → 评分A ┐
     → [引擎B] → 评分B ├→ max(评分) → 输出
     → [引擎C] → 评分C ┘
```

## 代码示例
```python
class MultiEngineExtractor:
    def __init__(self):
        self.engines = []
    
    def register(self, engine, cost=1):
        self.engines.append((engine, cost))
    
    def extract(self, input_data):
        best_result, best_score = None, 0
        for engine, cost in self.engines:
            result = engine.run(input_data)
            score = engine.evaluate(result) / cost
            if score > best_score:
                best_result, best_score = result, score
        return best_result
```

## 已知应用
- pdf-to-excel: pdfplumber / Tesseract / PaddleOCR / GPT-4o 四路并行选优

## 标签
#architecture #ocr #multi-model


