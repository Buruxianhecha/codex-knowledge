---
status: active
since: 2026-05-25
lesson: lessons/v1-hardening.md (硬化清单第4项)
related: mistakes/flask-debug-true.md (同属安全问题)
---
# 下载接口无鉴权

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
`/download/<token>` 没有 `@login_required`，知道 UUID 就能下载任何用户的文件。

## 根因
开发时为了方便测试跳过了鉴权，事后忘记加回。

## 修复
加上 `@login_required` 装饰器，并验证 token 属于当前用户。

## 预防
- 所有涉及用户数据的路由都应该有鉴权
- 用装饰器统一管理而非逐路由手动检查
- 安全审计 checklist

## 标签
#security #auth #api

