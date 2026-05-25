---
source: pdf-to-excel
date: 2026-05-24
category: data-quality
severity: data-loss
---

# 多行数字表被误判为噪音

## 输入特征
- PDF 中含多行数字的表格（如工程量清单）
- 每行都是纯数字

## 期望输出
正常提取所有数字。

## 实际输出
被 `_is_diagram_noise()` 判定为"图纸噪音"，置空。

## 根因
```python
def _is_diagram_noise(text):
    if cleaned.isdigit() and len(cleaned) <= 20 and "\n" in t:
        return True  # 多行纯数字 → 认为是噪音
```

## 门控规则
- 噪声过滤规则不能仅凭"看起来像噪音"就丢弃
- 纯数字表是合法的表格内容
- 噪声检测应基于**空间分布**（大空白区域、不连续）而非**内容特征**

## 预防措施
- 噪声过滤优先用几何特征（位置、间距）而非内容特征
- 内容过滤用白名单而非黑名单
