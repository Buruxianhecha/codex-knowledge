---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-08-12
verified_in: [shiguang-album-test-site]
expires_after: 2026-11-12
cross_refs:
  - projects/2026-08-26-two-month-learning-audit.md
  - lessons/browser-state-is-a-versioned-database.md
  - lessons/application-acceptance-over-command-success.md
  - patterns/auth-provider-mode-boundary.md
  - mistakes/shiguang-test-site-overstated-as-production-ready.md
  - templates/checklist/browser-state-app-checklist.md
---

# 拾光相册测试版重建与上线前验收

## 基本信息

- **日期**：2026-08-10 至 2026-08-12
- **目标**：先不付费，做一个用户满意的相册测试版
- **核心需求**：手机号 + 验证码演示登录；注册用户才能上传；照片按账户隔离
- **技术栈**：React 19、Next.js 16 / Vinext、Cloudflare Worker / Sites、IndexedDB
- **最终状态**：视觉与核心交互测试版完成；深度产品验收结论为“暂不建议正式上线”

## 原始版本的问题

原版本是纯前端演示：

- 手机验证码只是演示流程，不是真实短信认证。
- 业务集中在单页和单一 Provider 中，路由更多是前端状态切换。
- 照片以 Data URL 形式放入 `localStorage`，容量、性能和大图处理能力有限。
- 没有数据库和对象存储，清除浏览器数据即可能丢失。
- 数据无法跨设备同步，账户隔离只存在于当前浏览器。

这些限制不妨碍原型演示，但不能支撑“私人相册正式产品”的安全与可靠性承诺。

## 重建后的架构

### 路由层

首页、珍藏、相册列表、相册详情、上传、照片详情、个人中心、回收站等拥有独立可刷新地址。受保护页面会检查当前 Session，并在未登录时重定向。

### 存储层

IndexedDB 统一保存：

- 原图 Blob。
- 缩略图 Blob。
- 照片元数据。
- 相册、收藏、回收站和资料数据。
- 账户与 Session 的本地表示。

这比 Data URL + `localStorage` 更适合二进制照片，但它仍然只是本机浏览器数据库，不是云端备份。

### 数据隔离

每条业务记录使用 `ownerId` / `userId` 过滤，使同一浏览器里的不同测试手机号看不到彼此数据。这能验证产品交互和数据模型，但不是防攻击的服务端访问控制。

### 功能面

已实现或在验收中实际遍历的功能包括：

- 手机号测试登录、刷新保持、退出和账户切换。
- 多图上传入口与单文件 25 MB 校验。
- 首页、相册列表/详情、新建与编辑相册。
- 添加已有照片、照片详情、原图查看和缩放。
- 收藏同步、搜索和无结果状态。
- 多选、删除确认、回收站和照片失效状态。
- 头像、昵称、简介、资料导出、隐私、存储、帮助和 404。

## 认证架构

认证没有散落成页面里的 `if (test)`。设计将 UI 固定调用 `loginWithWeChat()`，由认证服务根据 `AUTH_MODE` / `WECHAT_AUTH_MODE` 选择：

```text
UI
  -> authService / authStore
      -> mockWechatProvider
      -> realWechatProvider
```

当前完成的是 `mock`：微信登录 UI、取消/失败路径、两个 Mock 账户、Session 和刷新保持。真实微信 OAuth 尚未接入。

切换到 `real` 需要服务端完成：

1. 注册并配置微信平台、正式域名与 callback URI。
2. 提供 `/auth/wechat` 与 `/auth/wechat/callback` 等服务端入口。
3. 在服务端持有 `AppSecret`，用 `code` 换取 token。
4. 取得 `openid` / `unionid` 并绑定现有用户。
5. 让所有登录方式落到统一 `User` 和 `User Session`。

`AppSecret`、`access_token` 和真实凭据不得进入前端、URL 或 Git。

## 数据迁移约束

认证升级时不能通过“清空 IndexedDB”获得干净状态。必须保留现有照片、相册、收藏和设置，并为旧本地用户建立兼容身份，例如迁移到 `legacy_local_user` 或明确的测试账户。

至少使用两个 Mock 身份验证隔离：

- `mock_openid_001`
- `mock_openid_002`

测试内容包括登录、刷新、退出、重复点击、取消、失败重试、跨账户查询和写入隔离。

## 验证证据

**已确认**：Lint 0 错误、正式构建通过；核心登录、路由、刷新保持和收藏流程完成过交互验收。

**自动化受限**：真实系统文件选择器无法由当时的自动浏览器完整验证，因此不能据此宣称真实文件写入矩阵全部通过。

**深度验收结论**：测试站“视觉完成度较高、核心页面基本连通”，但暂不建议上线为可托付私人照片的正式产品。

## 正式上线前缺口

- 真实文件写入与刷新后持久化。
- 1/5/20/50 张混合上传。
- HEIC、超长截图、大文件、失败与重试。
- 永久删除、恢复和离线行为。
- 存储已满、Session 到期和异常恢复。
- 375/390/430 px 真机触摸与安全区。
- 3,000/10,000 张照片压力表现。
- 云端对象存储、服务端权限、备份与恢复。
- 真实短信或微信 OAuth。

## 成本与架构判断

| 维度 | 测试版选择 | 正式版变化 |
|------|------------|------------|
| 费用 | 无付费后端 | 对象存储、数据库、短信/认证产生费用 |
| 延迟 | 本地 IndexedDB 快 | 上传与缩略图需网络/后台任务 |
| 复杂度 | 单浏览器、前端隔离 | 服务端 ACL、同步、恢复、配额与审计 |
| 维护 | 原型可控 | 需要迁移、监控、备份和安全更新 |
| 扩展 | 单设备有限 | 云端可跨设备，但必须先解决一致性与成本 |

## 可复用结论

- 先做无付费测试版是有效产品决策，但必须明确能力边界。
- IndexedDB 适合原型期大对象持久化，不等于云存储。
- 本地 `ownerId` 隔离用于功能测试，不是生产授权。
- Mock/Real 的差异应封装在 Provider，而不是散落到 UI。
- 后续接真实后端时优先做兼容迁移，不能以清库代替迁移。
- 上线声明必须服从深度验收报告，不服从较早的乐观完成声明。

## 标签

#react #indexeddb #auth #oauth #local-first #photo-app #product-validation
