---
status: active
confidence: 1.0
reuse_count: 0
last_used: 2026-08-25
verified_in: [minimal-tv-clock]
risk: high
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - lessons/minimal-requirements-are-a-contract.md
  - lessons/user-perspective-verification.md
---

# 极简电视时钟偏离原始排他需求

## 用户确认的需求

- 单文件。
- 纯黑背景。
- 只显示 `HH:MM`。
- 字体超大、正立。
- 不加秒数、日期、全屏按钮或其他信息。

## 实际偏离

远端仓库拆成 `index.html`、`style.css`、`script.js` 和图标，并显示 `HH:MM:SS`、日期、全屏按钮，还加入 Wake Lock 和防烧屏漂移。

用户随后明确指出：“重来，最开始的要求你忘了吗”。仓库最后 push 早于这次纠正，因此远端仍是偏离版本。

## 根因

- 把一般电视时钟的合理功能当成用户必需功能。
- 在仓库整理阶段重新设计了已接受的最小产品。
- 验收检查“功能是否丰富”，没有检查“禁止项是否存在”。

## 影响

- 用户需要重复表达已经说清的要求。
- 托管版本与最终接受版本分叉。
- “极简”名称与实际界面不一致。

## 修复标准

修复不能只隐藏按钮或 CSS；必须回到单文件结构，并对 DOM 可见文本和文件树做负向断言。

## 预防

明确含“只/仅/不要/单文件”的需求时，生成排他清单。任何额外功能先询问，不默认加入。

## 标签

#mistake #requirements #scope-creep #minimal-ui #acceptance
