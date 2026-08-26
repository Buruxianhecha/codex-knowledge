---
status: verified
confidence: 0.99
reuse_count: 2
last_used: 2026-08-25
verified_in:
  - minimal-tv-clock
  - user-interface-requirements
expires_after: none
cross_refs:
  - mistakes/minimal-tv-clock-requirement-drift.md
  - projects/2026-08-25-browser-simulator-release-series.md
  - lessons/user-perspective-verification.md
---

# 排他性最小需求是合同

> “只显示”“单文件”“不要其他信息”不是审美建议，而是禁止扩展的验收条件。

## 触发案例

极简电视时钟的最终需求是：单文件、纯黑背景、只显示 `HH:MM`、数字超大正立，不加秒数、日期、全屏按钮或其他信息。

实际仓库加入了多文件拆分、秒、日期、全屏按钮、Wake Lock 和防烧屏漂移。每项单独看都有产品理由，但组合起来改变了用户要的产品。用户最终明确要求重来。

## 为什么会漂移

- 把“更完整”误认为“更符合需求”。
- 用通用产品最佳实践覆盖具体用户目标。
- 验收只检查页面能否工作，没有逐条比较排他约束。
- 在部署/整理仓库时重新设计，而不是冻结已接受版本。

## 需求词的优先级

| 词语 | 默认解释 |
|------|----------|
| 只、仅 | 列表外元素默认禁止 |
| 不要、不显示、不加 | 明确负需求，优先级等同功能需求 |
| 单文件 | 交付结构要求，不只是运行效果 |
| 保持原样 | 除指定改动外，视觉和行为都冻结 |
| 尽量 | 可优化目标，需要权衡 |

## 冻结验收合同

实现前把需求改写成机器/人工都能核对的表：

```text
[ ] 文件树只有 index.html
[ ] body 可见文本只匹配 HH:MM
[ ] 不存在秒、日期、按钮、说明
[ ] 背景纯 #000
[ ] 字体正立、无旋转
[ ] 远端 HEAD 与验收版本一致
```

对“没有某物”的要求，必须做负向检查，如 DOM 文本、选择器和文件树中不存在。

## 何时可以增加功能

只有三种情况：用户明确要求；不增加就无法满足原目标；作为默认关闭且不改变交付结构的可选项，并得到用户同意。其余“善意扩展”先提议，不直接实现。

## 标签

#requirements #scope #minimal-ui #acceptance #negative-requirements
