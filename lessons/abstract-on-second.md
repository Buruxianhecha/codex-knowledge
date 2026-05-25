---
status: active
since: 2026-05-25
cross_refs:
  - mistakes/copy-paste-ocr-engines.md (这个错误的根因)
  - decisions/ocr-parallel-vs-sequential.md (如果有抽象层，决策实现更简单)
---
# 第二个实现时就应该抽象

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
先写了 Tesseract OCR，然后需要加 PaddleOCR，选择了复制 Tesseract 的文件改中间逻辑。再加 GPT-4o 时又复制了一次。

## 经验
第一次实现可以具体。第二次实现同一个接口时，必须停下来抽公共层。第三次还不抽象就是技术债。

## 行动指南
同一接口的第二个实现 = 抽象信号。不要等第三个。

## 标签
#architecture #refactoring #technical-debt

