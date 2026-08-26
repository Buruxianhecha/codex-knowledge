---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-08-26
verified_in: [structural-drawing-review]
expires_after: none
cross_refs:
  - projects/2026-08-21-bim-structural-drawing-input.md
  - lessons/drawing-values-derive-from-reference-planes.md
---

# Checklist：BIM / 结构图纸输入前复核

## 图纸证据

- [ ] 已定位平面图中的构件编号/范围。
- [ ] 已找到对应断面或详图。
- [ ] 已确认楼层/构件基准标高。
- [ ] 项目符号、缩写和日文标注没有擅自改义。

## 几何与位置

- [ ] 名义截面宽×高/板厚与位置 offset 分开记录。
- [ ] 已确认 reference plane（如梁上端、基础上端等）。
- [ ] 已确认 offset 正负方向。
- [ ] 上端/下端标高由 reference + offset + geometry 推导，而不是只看单个数值。

## 増打 / 局部加厚

- [ ] 已确认是上増打还是下増打。
- [ ] 已确认作用范围，不把局部断面值当全长常量。
- [ ] 若厚度渐变，已找到起终点/坡度/分区证据。
- [ ] 软件不支持渐变时，采用拆分或专用变厚方式，而不是强行填一个平均常数。

## 软件复核

- [ ] 字段语义与软件手册/界面一致。
- [ ] 3D/断面预览与图纸方向一致。
- [ ] 修改截面尺寸后，未要求变化的标高/offset 未被连带改动。
- [ ] 不确定字段保留“待确认”，没有凭经验补值。
