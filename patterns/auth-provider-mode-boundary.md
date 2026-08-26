---
status: active
confidence: 0.92
reuse_count: 1
last_used: 2026-08-12
verified_in: [shiguang-album-test-site]
expires_after: 2026-11-12
cost:
  token_cost: low
  latency: fast_in_mock_network_in_real
  complexity: moderate
  maintenance: medium
  scalability: scalable
cross_refs:
  - projects/2026-08-12-shiguang-album.md
  - lessons/browser-state-is-a-versioned-database.md
  - templates/checklist/browser-state-app-checklist.md
---

# 认证 Provider 模式边界

## 适用场景

产品测试版先使用 Mock 登录，正式版再接短信、微信 OAuth 或其他真实身份提供方。目标是让 UI、Session 和业务数据不依赖具体认证实现。

## 结构

```text
Login UI
  -> AuthService
      -> AuthStore / Session
      -> AuthProvider interface
          -> MockProvider
          -> RealProvider
```

UI 只调用稳定接口：

```ts
interface AuthProvider {
  login(input?: unknown): Promise<AuthResult>;
  restore(): Promise<UserSession | null>;
  logout(): Promise<void>;
}
```

启动时由单一配置选择 Provider：

```ts
const provider = AUTH_MODE === "real"
  ? realWechatProvider
  : mockWechatProvider;
```

禁止在页面和组件中散落 `if (isTest)`。

## 统一身份

不同登录方式最终映射到统一模型：

```text
External identity (phone/openid/unionid)
  -> IdentityBinding
  -> internal userId
  -> UserSession
```

业务照片、相册和收藏只绑定内部 `userId`，不直接绑定手机号或 `openid`。这样认证方式变化时，不需要重写业务表。

## Mock 的要求

Mock 不是一个永远成功的按钮。至少覆盖：

- 两个稳定测试账户。
- 取消、失败、重试和重复点击。
- 刷新恢复和退出。
- 账户间读写隔离。
- Session 过期模拟。

## Real 的要求

- OAuth code 交换只在服务端。
- `AppSecret` 和 token 不进入浏览器、URL、日志或 Git。
- callback 校验 state、时效和重放。
- 外部身份与内部用户绑定可审计、可撤销。
- 切换模式前完成旧本地用户迁移。

## 失败模式

- UI 直接读取 Mock 用户常量。
- `AUTH_MODE=real` 只换按钮文案，没有服务端交换。
- 每种登录方式创建一套不同 User 数据。
- 上线时清空 IndexedDB 逃避迁移。
- 把本地 owner 过滤宣传成安全授权。

## 标签

#auth #provider #mock #oauth #architecture #migration
