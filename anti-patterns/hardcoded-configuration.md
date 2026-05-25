---
confidence: 1.0
first_seen: pdf-to-excel (2026-05-24)
severity: high
---

# Hardcoded Configuration（硬编码配置）

## 症状
路径、密钥、URL、阈值等配置值直接写在代码中，换环境即不可用。

## 腐烂路径
```
开发环境 → 写死路径 → 正常工作
换电脑/重装 → 路径不存在 → 程序崩溃
修路径 → 改了 A 文件漏了 B 文件 → 部分功能失效
```

## 检测信号
- 代码中出现绝对路径字符串
- 配置值分散在多个文件
- `.env` 或 `config.py` 存在但部分模块不读

## 修复
统一配置入口：环境变量 或 config.py。所有模块从同一入口读取。代码中零绝对路径。

## 已知案例
- pdf-to-excel: TESSERACT_EXE = "D:\OCR\tesseract.exe"
- pdf-to-excel: uploads 路径在 ocr.py 中写死，与 Flask config 不一致
