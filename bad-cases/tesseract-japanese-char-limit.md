---
source: pdf-to-excel
date: 2026-05-24
category: ocr
severity: data-loss
---

# Tesseract 丢弃长文本

## 输入特征
- 日语 PDF 表格
- 单元格内容含 >10 字符的日语文本（如 "コンクリート強度試験結果" = 13 字符）

## 期望输出
完整提取所有文本。

## 实际输出
长度 >10 字符的文本被静默丢弃。

## 根因
```python
# ocr.py _reconstruct_table_from_tsv()
if not text or len(text) > 10:  # ← 针对英文的阈值
    continue
```

## 门控规则（从此案例提炼）
- OCR 输出的字符级过滤：阈值必须按目标语言调整
- 日语单字符代表完整语义单元，不能简单按字符数过滤
- 建议：过滤阈值用字节数而非字符数，或按语言动态设置

## 预防措施
- OCR 文本过滤参数做成可配置
- 对不同语言使用不同阈值
