---
status: active
confidence: 0.92
reuse_count: 1
last_used: 2026-08-25
verified_in: [jianlai-life-simulator]
expires_after: none
cost:
  token_cost: low
  latency: fast
  complexity: moderate
  maintenance: medium
  scalability: limited
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - lessons/verification-claims-must-match-test-depth.md
  - decisions/targeted-single-file-fix-before-refactor.md
---

# 单文件应用的 VM 测试壳

## 适用场景

已有大型 HTML 把 CSS、数据和 JavaScript 全部内联，短期不能安全拆分，但需要对真实状态逻辑做自动化回归。

## 方法

1. 从 HTML 提取目标内联 `<script>`。
2. 用 Node.js `vm` 在隔离上下文执行。
3. 提供最小浏览器替身：`window`、`document`、Element、ClassList、`localStorage`、计时器。
4. 追加仅测试使用的 bridge，把内部函数暴露为窄 API。
5. 注入可预测 `Math.random`，稳定测试随机流程。

## 为什么优于字符串检查

静态搜索只能证明函数名或关键字存在。VM 壳会执行原函数，可以观察属性变化、存档往返、随机结果保持和错误分支。

## 最小原则

- 只实现测试需要的 DOM 能力，未用接口不模拟。
- 测试 bridge 不修改生产文件。
- 浏览器特有能力仍需真实浏览器验收。
- 不把 Fake DOM 通过当作布局、触摸或文件选择器验证。

## 适合测试

- 纯状态转换。
- `localStorage` 保存/读取。
- 随机抽样和确定性恢复。
- 按钮动作调用的业务函数。
- 结束态、错误态和迁移。

## 不适合替代

- CSS 布局和视觉截图。
- 浏览器权限、文件选择器、Wake Lock。
- 真机触摸、性能和可访问性树。
- 网络和 Service Worker 行为。

## 标签

#testing #node-vm #single-file #legacy #test-harness
