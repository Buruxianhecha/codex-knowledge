---
status: active
since: 2026-05-25
consequence_good: 准确率显著提高 (patterns/multi-engine-parallel-select.md)
consequence_bad: 成本高 (每次跑 GPT-4o)
known_issue: mistakes/copy-paste-ocr-engines.md (引擎没抽象导致维护困难)
---
# OCR 策略：并行择优 vs 串行降级

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
需要从多种 OCR 引擎中获取最佳结果。两个选项：
- 串行降级：A 失败 → B → C
- 并行择优：全跑一遍，按评分选最优

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| 串行降级 | 省资源，大部分情况第一个就够了 | "失败"的定义模糊，可能错过更好的结果 |
| 并行择优 | 总能拿到最佳结果 | 每次都要跑多个引擎，耗时和成本高 |

## 决策
选择了并行择优——按列数评分，三路全跑，选列最多的结果。

## 后果
准确率显著提高，但每次上传都会调用 GPT-4o API（成本）。后续应加入"如果前面引擎产出足够好就跳过 GPT"的优化。

## 标签
#architecture #ocr #trade-off

