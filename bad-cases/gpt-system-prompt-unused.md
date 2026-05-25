---
source: pdf-to-excel
date: 2026-05-24
category: ocr
severity: silent-failure
---

# GPT SYSTEM_PROMPT 未生效

## 输入特征
- 通过 GPT-4o Vision 做表格 OCR
- 定义了详细 SYSTEM_PROMPT（含 6 条规则）

## 期望输出
GPT 按 SYSTEM_PROMPT 中的规则精确提取表格。

## 实际输出
GPT 按默认行为提取，质量不稳定。SYSTEM_PROMPT 从未被传入 API。

## 根因
```python
# ocr_gpt.py
SYSTEM_PROMPT = """..."""  # 定义了但...

# API 调用时：
messages=[{
    "role": "user",
    "content": [{"type": "text", "text": "Extract the table..."}]
}]  # ← 没有 system message
```

## 门控规则
- LLM API 调用：必须验证 prompt 参数完整性
- 定义了但未使用的常量 → lint 警告
- 建议：prompt 模板和 API 调用写在一起，或通过工厂函数保证不遗漏

## 预防措施
- LLM 调用路径加集成测试
- 常量靠近使用点
