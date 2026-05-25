---
status: active
confidence: 1.0
reuse_count: 1
last_used: 2026-05-25
anti_pattern: anti-patterns/copy-paste-architecture.md
---
# 三个 OCR 引擎复制粘贴——没有抽象

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
ocr.py、ocr_paddle.py、ocr_gpt.py 各 100+ 行，70% 代码重复。FakeTable 类在三个文件中各自定义。改一处需要改三处。

## 根因
先实现了主流程（pdfplumber→Excel），后续加 OCR 引擎时直接复制文件修改中间逻辑。没有在第二个引擎加入时停下来抽公共层。

## 修复
抽 `BaseOCRExtractor`，定义 `extract(pdf_path) -> List[TableData]` 接口。三个引擎实现该接口。FakeTable 替换为 dataclass。

## 预防
- 同一接口的第二个实现 = 抽象信号
- 用 `jscpd` 或 `sonar` 检测代码重复
- 加新引擎前先看能不能复用现有 pipeline

## 标签
#copy-paste #refactoring #ocr #technical-debt


