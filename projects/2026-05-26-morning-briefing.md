---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-08-26
verified_in: []
expires_after: 2026-11-26
cross_refs:
  - patterns/powershell-graph-devicecode-auth.md
  - lessons/rest-over-sdk-windows.md
  - mistakes/encoding-string-replace-windows.md
  - anti-patterns/hardcoded-configuration.md
  - lessons/application-acceptance-over-command-success.md
---

# morning-briefing — Codex 晨间简报自动化

> 历史项目的详细恢复版。保留当时的架构和失败经验，同时修正认证、路径与编码方面后来发现的过强或不准确表述。

## 基本信息

- **创建日期**：2026-05-26
- **目标**：工作日早上自动生成一份 Markdown 晨间简报。
- **工作区表达**：`%USERPROFILE%\Documents\Codex\briefings\`；公开知识库不保存具体 Windows 用户名。
- **当前证据状态**：脚本/流程设计存在，但没有足够证据证明首次 Microsoft Graph 授权和长期无人值守运行已经完成，因此保持 `active`，不写成生产自动化已闭环。

## 项目目标

简报计划包含：

1. 当日日历安排、会议状态与时间冲突。
2. 重要未读邮件。
3. 需要人工关注的事项，例如紧急邮件或日程重叠。
4. 明确的生成时间和业务时区。

## 技术栈

| 层次 | 技术 | 用途 |
|------|------|------|
| 数据源 | Microsoft Graph REST API | 邮件与日历 |
| 认证 | Microsoft identity platform / OAuth 2.0 | 用户委托权限 |
| 首次交互 | Device Code Flow（仅 public client 场景） | 无嵌入浏览器时的登录 |
| 脚本 | PowerShell | HTTP、转换、Markdown 生成 |
| 调度 | Windows Task Scheduler | 工作日定时执行 |
| 输出 | Markdown | 本地可读简报 |

## 认证设计

推荐优先使用受支持的 MSAL/token cache；只有明确需要轻量纯 REST 实现时才直接操作 `/devicecode` 与 `/token` 协议。

```text
自己的 Entra App Registration
-> 配置为 public client
-> 首次 Device Code 交互授权
-> token cache / 安全保存 refresh token
-> 后续优先静默获取/刷新 access token
-> Graph API
```

关键边界：

- `client_id` 应来自**自己的应用注册**，不能把 Microsoft 其他工具的 client ID 当成通用公共 client。
- Device Code Flow 只适用于 public client applications。
- 原始协议轮询 `/token` 时必须携带与 `/devicecode` 请求一致的 `client_id`。
- 若请求 `offline_access` 并获得 refresh token，刷新成功后应保存新返回的 refresh token（如果有），不能假定旧 token 永久有效。
- token/refresh token 都属于凭据，不进入 GitHub、日志或知识库正文。

详见 `patterns/powershell-graph-devicecode-auth.md`。

## 数据流

```text
Task Scheduler
-> 启动 PowerShell
-> 获取有效 access token
-> calendarView 拉取目标业务日事件
-> messages 拉取需要关注的邮件
-> 统一时间/时区
-> 冲突检测 + 重要性规则
-> 生成 Markdown
-> 写入运行日志（不含 token/邮件正文等不必要敏感数据）
```

### 日历

`/me/calendarView` 应使用明确的 `startDateTime` / `endDateTime`，并明确 API 传入/返回时间的时区处理，避免把服务器 UTC、Graph 返回值和本地业务日混为一谈。

### 邮件

只请求完成任务所需最小字段和权限。若只需标题、发件人、时间、重要性，就不要把完整正文永久写入本地调试日志。

### 冲突检测

将事件标准化为统一时区后的 `[start,end)` 区间，再判断重叠；全天事件、跨日会议和不同 `showAs` 状态要单独定义产品口径。

## 调度示意

```powershell
$trigger = New-ScheduledTaskTrigger `
  -Weekly `
  -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday `
  -At 8:00AM

$action = New-ScheduledTaskAction `
  -Execute "powershell.exe" `
  -Argument '-NoProfile -File "%USERPROFILE%\Documents\Codex\briefings\MorningBriefing.ps1"'
```

这只是结构示意；实际任务要验证运行账户、工作目录、PowerShell 版本、ExecutionPolicy、网络和凭据缓存权限。

## 关键教训

### 1. REST 与 SDK 是权衡，不是绝对优劣

当时重型 SDK 安装链带来 NuGet、模块路径和版本问题，因此纯 REST 显著降低了依赖。但长期认证、token cache、重试和安全处理由 MSAL 代管通常更稳。正确原则是：**根据部署环境和维护成本选最小可靠层，而不是把 REST > SDK 写成无条件定律。**

### 2. Device Code 不是“完全无人值守认证”

它仍需要首次用户交互，token 失效/撤销后还可能再次交互。真正的后台服务若不代表某个具体用户，应该重新评估 confidential-client / application permission 架构，而不是强行复用用户委托 token。

### 3. 编码问题必须区分 PowerShell 版本

Windows PowerShell 5.1 与 PowerShell 7+ 的默认编码和 `-Encoding UTF8` 行为不同。不能用“永远带 BOM”或“永远无 BOM”作为跨版本规则；应先识别源编码、显式读写，再按目标运行时选择 UTF-8 BOM/NoBOM。详见对应 mistake。

### 4. 中文/非 ASCII 路径不应该靠写死用户目录解决

路径使用 `%USERPROFILE%`、`Join-Path` 或配置注入，并对带空格/非 ASCII 的真实路径做集成测试。把某个具体 `C:\Users\<name>` 写死只会把一次修复变成下一台机器的 bug。

## 验收矩阵

- [ ] 自己的 Entra 应用注册已配置并记录非敏感 client ID 来源。
- [ ] 首次授权真实成功。
- [ ] token 缓存权限与轮换逻辑验证。
- [ ] access token 过期后能静默恢复；不能恢复时能明确要求重新授权。
- [ ] calendarView 的业务日和时区正确。
- [ ] 邮件筛选不误把普通邮件全部拉入简报。
- [ ] Task Scheduler 在退出交互会话后仍可按预期运行。
- [ ] 脚本路径包含中文/空格时仍能运行。
- [ ] 日志不泄露 token、cookie、邮件正文或其他不必要敏感数据。
- [ ] 重启后仍能按预期工作，才可声称“无人值守运行已闭环”。

## 标签

#automation #powershell #microsoft-graph #outlook #task-scheduler #oauth #device-code #briefing