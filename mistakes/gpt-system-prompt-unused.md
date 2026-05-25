---
status: active
since: 2026-05-25
lesson: lessons/abstract-on-second.md (OCR 引擎没统一接口导致测试遗漏)
---
# GPT SYSTEM_PROMPT 定义但未使用

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
ocr_gpt.py 定义了一个详细的 SYSTEM_PROMPT 常量（含 6 条 CRITICAL RULES），但 API 调用时 messages 数组里只有一条 user message，SYSTEM_PROMPT 从未传入。

## 根因
可能是先写了 prompt，后来改了 API 调用方式，忘了把 system prompt 加回去。没有自动化测试覆盖 OCR 路径。

## 修复
```python
messages=[
    {"role": "system", "content": SYSTEM_PROMPT},
    {"role": "user", "content": [...]}
]
```

## 预防
- LLM API 调用加集成测试验证 prompt 生效
- prompt 常量靠近使用点，或统一管理
- 代码审查时检查 API 参数完整性

## 标签
#llm #prompt-engineering #bug

