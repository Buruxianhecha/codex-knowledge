---
status: active
confidence: 0.92
reuse_count: 1
last_used: 2026-08-12
verified_in: [shiguang-album-test-site]
expires_after: 2026-11-12
cost:
  token_cost: low
  latency: fast
  complexity: moderate
  maintenance: medium
  scalability: limited
cross_refs:
  - projects/2026-08-12-shiguang-album.md
  - lessons/browser-state-is-a-versioned-database.md
  - patterns/auth-provider-mode-boundary.md
---

# 付费后端前先做本地优先测试版

## 背景

用户明确要求“先不付费做满意测试版”。相册产品在 UI、信息架构和核心操作尚未验证前，直接购买对象存储、短信、数据库和域名会增加成本，却不能保证产品方向正确。

## 决策

测试版使用 IndexedDB 保存原图、缩略图和元数据；用 Mock 手机/微信身份验证账户流程；通过稳定接口预留未来真实 Provider。

## 好处

- 零或低后端费用验证产品体验。
- 可快速重做数据模型和路由。
- 能用两个本地账户验证 owner 隔离。
- 真实后端接入前先形成迁移需求和容量样本。

## 代价

- 不能跨设备同步或恢复。
- 清理浏览器数据可能丢失。
- 本地 owner 过滤不是安全授权。
- 真实文件、容量和移动端边界仍需专门验收。

## 升级门槛

只有在核心流程得到用户认可，并完成数据迁移设计后，才进入付费后端阶段。升级时必须明确告知本地数据边界，不能把测试站包装成云相册。

## 标签

#decision #local-first #prototype #cost-control #backend
