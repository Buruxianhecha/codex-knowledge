---
confidence: 1.0
first_seen: pdf-to-excel (2026-05-24)
severity: medium
---

# Business Logic in Routes（路由中的业务逻辑）

## 症状
路由处理函数包含核心业务逻辑（数据提取、格式转换、验证），而不仅仅是请求/响应处理。

## 腐烂路径
```
路由层 → 混入业务逻辑 → 无法单独测试
需求变化 → 改路由 → 影响 API 契约
加新入口(CLI) → 无法复用 → 再写一遍
```

## 检测信号
- 路由函数超过 30 行
- 路由文件同时 import Flask 和业务库
- "我想加个 CLI 但逻辑都在 routes 里"

## 修复
抽 `services/` 层。路由只做：解析请求 → 调 service → 格式化响应。

## 已知案例
- pdf-to-excel: main.py 410 行，包含 OCR 调度、Excel 生成、表格验证
