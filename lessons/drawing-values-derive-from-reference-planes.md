---
status: active
confidence: 0.95
reuse_count: 1
last_used: 2026-08-26
verified_in:
  - structural-drawing-review
expires_after: none
cross_refs:
  - projects/2026-08-21-bim-structural-drawing-input.md
  - templates/checklist/bim-structure-input-checklist.md
---

# 工程图纸数值先找基准面，再做算术

> BIM/结构输入最危险的错误之一，是数值本身算对了，但参考面、方向或局部范围错了。

## 四个问题

填任何标高、offset、厚度或増打前先回答：

1. 数值相对谁？
2. 正负方向是什么？
3. 这是几何尺寸还是位置偏移？
4. 它作用于整个构件还是局部范围？

## 典型误判

- “上端 0 + 厚度 2600 = 下端 -2600”在存在 offset/局部加厚时不一定成立。
- 把“梁上端 -400”手算成绝对标高，丢失 reference 语义。
- 把断面某点的 `下増 500` 当成整个构件固定 500。
- 截面改成 `640×950` 时顺手改变未要求的位置参数。

## 建议数据模型

```text
referencePlane
baseElevation
offset
nominalSize
localAddition
scope
```

把语义拆开存，比把所有东西压成一个绝对数字更可审计。

## 标签

#bim #engineering #reference-plane #offset #data-entry