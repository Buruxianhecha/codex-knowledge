---
status: active
confidence: 0.96
reuse_count: 0
last_used: 2026-08-26
verified_in: [morning-briefing-design]
expires_after: 2026-11-30
cross_refs:
  - projects/2026-05-26-morning-briefing.md
  - lessons/rest-over-sdk-windows.md
  - mistakes/encoding-string-replace-windows.md
---

# PowerShell + Microsoft identity platform Device Code 认证模式

> 2026-08-26 按 Microsoft 官方协议文档重新核验。原条目中的通用 client ID、token 轮询参数和 refresh-token 缓存示例存在问题，本版已纠正。

## 适用场景

Device Code Flow 主要适合：

- CLI、IoT 或没有方便内嵌浏览器的 public client。
- 需要用户委托权限访问 Microsoft Graph。
- 首次认证允许用户在另一个浏览器/设备上完成交互。

它**不是**通用后台 daemon 认证方案，也不等于永远无人值守。真正的服务端 daemon 应评估 confidential client / application permissions。

## 前置条件

1. 在 Microsoft Entra 中为自己的应用创建 App Registration。
2. 记录自己的 Application (client) ID。
3. 根据目标账户选择合适 tenant (`organizations`、`common`、`consumers` 或具体 tenant)。
4. 将应用配置为允许 public client flow。
5. 请求最小必要 scope；需要 refresh token 时包含 `offline_access`。

不要复制别的 Microsoft 工具或第三方应用的 client ID 当作自己的通用 client ID。

## 协议结构

```text
POST /{tenant}/oauth2/v2.0/devicecode
  client_id=<YOUR_APP_CLIENT_ID>
  scope=<SCOPES>
        |
        v
显示 verification_uri + user_code
        |
用户在浏览器交互登录
        |
客户端按 interval 轮询
        v
POST /{tenant}/oauth2/v2.0/token
  grant_type=urn:ietf:params:oauth:grant-type:device_code
  client_id=<同一个 client_id>
  device_code=<device_code>
        |
        v
access_token (+ 可能的 refresh_token)
```

`/token` 轮询中的 `client_id` 是必需字段，而且必须和初始 `/devicecode` 请求一致。

## PowerShell 协议骨架

```powershell
$tenant = "common"
$clientId = "<YOUR_APP_CLIENT_ID>"
$scopes = "offline_access Mail.Read Calendars.Read"

$device = Invoke-RestMethod `
  -Uri "https://login.microsoftonline.com/$tenant/oauth2/v2.0/devicecode" `
  -Method Post `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{ client_id = $clientId; scope = $scopes }

Write-Host $device.message
```

轮询请求的**必需字段骨架**：

```powershell
$body = @{
  grant_type  = "urn:ietf:params:oauth:grant-type:device_code"
  client_id   = $clientId
  device_code = $device.device_code
}
```

直接用 `Invoke-RestMethod` 实现完整轮询时，还必须正确处理非 2xx 的 `authorization_pending`、`slow_down`、`authorization_declined`、`expired_token` 等状态，并遵守服务端返回的 `interval`。因此在可接受依赖的项目中，优先让 MSAL 实现协议和 token cache。

## Refresh token

如果授权响应提供了 refresh token：

- 它和 access token 一样属于敏感凭据。
- 使用 refresh token 换 access token 时同样要发送自己的 `client_id`。
- 刷新成功如果返回新的 refresh token，应安全替换缓存中的旧值。
- 不把“90 天”理解成绝对保证；token 可因生命周期、撤销、策略或账户变化而失效，应用必须能回到交互授权。

示意：

```powershell
$refreshBody = @{
  client_id     = $clientId
  grant_type    = "refresh_token"
  refresh_token = $cached.refresh_token
  scope         = $scopes
}
```

## Token 存储

最低要求：

- 不写入 Git 仓库。
- 不输出到普通调试日志。
- 限制本地文件权限，或使用 OS 凭据存储/token cache。
- 记录 token 类型、过期时间等非敏感元数据即可。
- 支持凭据失效后的显式重新登录路径。

## Device Code 与其他流程

| 场景 | 更常见选择 |
|------|------------|
| CLI / 无浏览器 public client | Device Code |
| 有系统浏览器的桌面 public client | Interactive + MSAL / WAM 等 |
| 代表应用本身的后台服务 | Confidential client / client credentials（按业务权限审查） |
| 用户名密码直接换 token | 不推荐；不要用 ROPC 作为默认方案 |

## 验收清单

- [ ] client ID 来自自己的应用注册。
- [ ] public client 配置与 tenant/account 类型匹配。
- [ ] scope 是最小必要权限。
- [ ] `/token` 轮询包含 `client_id`。
- [ ] 遵守 `interval` 并处理预期轮询错误。
- [ ] refresh token 更新/失效路径已测试。
- [ ] token cache 不进入仓库或日志。
- [ ] 用户撤销授权后能正确回到未认证状态。

## 标签

#powershell #microsoft-graph #oauth #device-code #public-client #automation #windows