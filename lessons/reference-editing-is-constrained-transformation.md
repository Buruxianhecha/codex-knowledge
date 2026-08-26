---
status: verified
confidence: 0.97
reuse_count: 3
last_used: 2026-08-26
verified_in:
  - shiguang-album-existing-site
  - visual-template-editing
  - minimal-tv-clock
expires_after: none
cross_refs:
  - lessons/minimal-requirements-are-a-contract.md
  - templates/checklist/reference-preservation-checklist.md
  - projects/2026-08-12-shiguang-album.md
---

# 参考图与现有作品修改属于“约束式变换”，不是重新设计

> 当任务是“按这个整改”“只改这里”“用原来的模板”“修改现有项目，不是重建”时，未被点名的部分默认属于保护区。

## 三类区域

开始修改前把目标拆成：

- **Must change**：明确要求修改的对象、文字、尺寸、坐标、功能。
- **Must preserve**：模板、布局、比例、品牌元素、已有业务逻辑、未点名内容。
- **May adapt**：为保证改动自然所需的最小尺寸/间距/响应式调整。

`May adapt` 不能反过来吞掉 `Must preserve`。

## 常见失败

- 用户只让换文字，结果重排整个版式。
- 用户给坐标，模型自行“优化位置”。
- 参考图用于学习视觉方向，却直接把参考图片嵌入作品。
- 现有网站要求增加功能，却从零重建导致已有数据/交互丢失。
- 修一个对象时顺便清理了用户没有要求删除的内容。

## 验收方式

修改后不只检查“新东西是否出现”，还要做负向 diff：

```text
指定对象发生了什么变化？
未指定对象是否发生了变化？
模板比例是否改变？
坐标/尺寸是否仍符合明确标注？
现有数据和功能是否保留？
```

最理想的交付是“变化范围可以解释”，而不是“整体看起来更好”。

## 标签

#editing #visual #existing-project #requirements #diff #preservation