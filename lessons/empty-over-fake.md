---
status: active
since: 2026-05-25
cross_refs:
  - lessons/quality-gating.md (质量门控体系的一环)
---
# 空值优于假数据

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
PDF 中的图纸、图形区域无法被 OCR 识别。常见做法是填 "N/A" 或 0。

## 经验
留空。填假数据会让用户误以为有真实数据，比留空危险得多。

## 行动指南
数据提取类工具：不可读区域 → 留空字符串 `""`，不做任何猜测填充。

## 标签
#data-quality #ux #ocr

