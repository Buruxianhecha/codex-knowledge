---
status: active
confidence: 0.90
reuse_count: 0
last_used: 2026-05-24
verified_in: [pdf-to-excel]
expires_after: none
cost: medium
cross_refs:
  - anti-patterns/business-logic-in-routes.md
  - lessons/v1-hardening.md
---

# Web 应用 vs CLI 工具

## 来源

- 项目：pdf-to-excel。
- 日期：2026-05-24。

## 背景

早期目标允许“命令行程序或网页端上传工具”，最终选择 Web。

## 选项

| 方案 | 优点 | 缺点 |
|------|------|------|
| Web | 用户友好、账号/历史记录易呈现 | 部署复杂，需要服务端，批处理较弱 |
| CLI | 易自动化、批量、本地集成 | 对非技术用户不友好，账号/历史 UI 弱 |

## 决策

v1 选择 Flask Web 应用 + 浏览器界面，以交互体验优先。

## 后果

- Web 路线强化了账号、上传、历史记录等产品体验。
- 也引入部署、鉴权、安全和路由分层成本。
- 如果未来需要批处理，CLI 应复用同一 service 层，而不是复制 Web 路由里的业务逻辑。

## Cost

中等：需要持续承担 Web 部署与安全成本；新增 CLI 时还需要先把业务逻辑从 routes 中抽离。

## 标签

#product #cli-vs-web #decision