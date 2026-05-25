---
status: active
since: 2026-05-25
related_decision: decisions/libs-vendoring.md (同属可移植性问题)
lesson: lessons/v1-hardening.md (硬化清单第3项)
---
# 硬编码路径

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
- `TESSERACT_EXE = Path(r"D:\OCR\tesseract.exe")`
- `Path(r"D:\Projects\pdf-to-excel\uploads")` 在 ocr.py 中写死
- 换机器或重装系统后直接不可用

## 根因
快速原型期图方便，直接写绝对路径。Flask config 有 `UPLOAD_FOLDER` 但 OCR 模块没读。

## 修复
- Tesseract: 从环境变量 `TESSERACT_PATH` 或系统 PATH 读取
- 上传目录: 统一读取 Flask config

## 预防
- 规则：配置信息只出现在 `.env` / `config.py` / 环境变量中
- 代码中永远不出现绝对路径字符串

## 标签
#hardcoded #configuration #portability

