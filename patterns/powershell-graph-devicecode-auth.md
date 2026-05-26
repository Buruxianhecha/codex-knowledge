---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-26
verified_in: ["projects/2026-05-26-morning-briefing.md"]
cross_refs:
  - lessons/rest-over-sdk-windows.md
---

# PowerShell + Microsoft Graph Device Code 认证模式

## 一句话

在 Windows 自动化脚本（Task Scheduler）中，用纯 REST API + Device Code Flow 替代重型 SDK 访问 Microsoft Graph。

## 适用场景

- 个人 Windows 自动化脚本需要访问 Microsoft 365 数据（邮件/日历/文件）
- 不希望安装 >150MB 的 Microsoft.Graph PowerShell SDK
- 需要后台静默运行（Task Scheduler / cron）
- 单用户场景，不需要 client secret 或证书

## 模式结构

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│ 首次手动运行 │ ──▶ │ Device Code   │ ──▶ │ 浏览器授权    │
│ (PowerShell) │     │ Flow 获取令牌 │     │ (一次性)      │
└─────────────┘     └──────────────┘     └──────────────┘
                            │
                    ┌───────▼────────┐
                    │ 缓存            │
                    │ access_token   │
                    │ refresh_token  │
                    │ expires_on     │
                    └───────┬────────┘
                            │
┌─────────────┐     ┌───────▼────────┐
│ 后续自动运行 │ ──▶ │ refresh_token  │
│ (Task Scheduler)│ │ 静默续期       │
└─────────────┘     └───────┬────────┘
                            │
                    ┌───────▼────────┐
                    │ Invoke-        │
                    │ RestMethod     │
                    │ Graph REST API │
                    └────────────────┘
```

## 核心代码

### Device Code 获取
```powershell
$clientId = "14d82eec-204b-4c2f-b7e8-296a70dab67e"  # 公共 client
$dc = Invoke-RestMethod "https://login.microsoftonline.com/common/oauth2/v2.0/devicecode" `
    -Body @{ client_id=$clientId; scope="$scopes" } -Method Post
# 显示 $dc.user_code，用户去 https://microsoft.com/devicelogin 输入
```

### 轮询 + 缓存
```powershell
while ($true) {
    Start-Sleep -Seconds $dc.interval
    $token = Invoke-RestMethod "https://login.microsoftonline.com/common/oauth2/v2.0/token" `
        -Body @{ grant_type="urn:ietf:params:oauth:grant-type:device_code"; device_code=$dc.device_code }
    if ($token.access_token) { break }
}
# 缓存到 JSON 文件
@{ access_token=$t; refresh_token=$t; expires_on=(Get-Date).AddSeconds($t.expires_in) } | ConvertTo-Json > token.json
```

### 静默刷新
```powershell
$cached = Get-Content token.json | ConvertFrom-Json
if ([DateTime]$cached.expires_on -lt (Get-Date).AddMinutes(10)) {
    $new = Invoke-RestMethod "https://login.microsoftonline.com/common/oauth2/v2.0/token" `
        -Body @{ grant_type="refresh_token"; refresh_token=$cached.refresh_token }
    # 更新缓存
}
```

## 为什么是 Device Code 而非其他

| 方案 | 适合个人自动化? | 原因 |
|------|:---:|------|
| Device Code | ✅ | 一次性浏览器授权，后续静默 refresh |
| Client Credentials | ❌ | 需 Azure AD 应用注册 + 证书/secret 管理 |
| ROPC (密码) | ❌ | 不安全，MFA 账户不可用 |
| Interactive (浏览器弹窗) | ❌ | Task Scheduler 下无 GUI |

## 注意事项

- 公共 `clientId` 适用于个人账户，企业账户可能需要注册自己的应用
- `refresh_token` 默认 90 天有效，需处理过期重新授权
- Token JSON 文件存储在用户目录，需设置合适的 NTFS 权限
- `authorization_pending` 是正常的轮询状态，不是错误

## 关联

- lessons/rest-over-sdk-windows.md (为什么不用 SDK)
- projects/2026-05-26-morning-briefing.md (实际应用)

## 标签
#powershell #microsoft-graph #oauth #device-code #automation #windows
