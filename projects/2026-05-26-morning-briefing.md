---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-05-26
verified_in: []
cross_refs:
  - patterns/powershell-graph-devicecode-auth.md (核心认证模式)
  - lessons/rest-over-sdk-windows.md (轻量 REST 优先原则)
  - mistakes/encoding-string-replace-windows.md (编码坑)
---

# morning-briefing — Codex 晨间简报自动化

## 基本信息
- **创建日期**: 2026-05-26
- **状态**: v1 完成（需用户完成首次 Graph 授权）
- **路径**: %USERPROFILE%\Documents\Codex\briefings\

## 项目目标
在每个工作日早上 08:00 自动生成一份晨间简报 Markdown 文件，包含：
1. 今日 Outlook 日历安排（会议、状态、冲突检测）
2. 重要未读邮件（高重要性标记）
3. 需要关注的事项（时间冲突、紧急邮件）

## 技术栈

| 层次 | 技术 | 用途 |
|------|------|------|
| 数据源 | Microsoft Graph REST API | 读取日历 + 邮件 |
| 认证 | OAuth 2.0 Device Code Flow | 首次授权 + 静默刷新 |
| 脚本 | PowerShell (纯内置，零依赖) | 数据获取 + Markdown 生成 |
| 调度 | Windows Task Scheduler | 工作日 08:00 触发 |
| 输出 | Markdown 文件 | 可读简报 |

## 实现方案

### 认证流程
```
首次运行: Device Code → 浏览器授权 → 缓存 refresh_token
后续运行: refresh_token → 静默获取 access_token → 调用 Graph API
```

使用 Microsoft 公共 client_id (14d82eec-204b-4c2f-b7e8-296a70dab67e)，
`common` tenant，scope: `Mail.Read Calendars.Read offline_access`。

### 数据流
1. 获取今日日历 (`/me/calendarView` 按 `start/dateTime` 排序)
2. 获取未读邮件 (`/me/messages?$filter=isRead eq false` 按重要性分类)
3. 处理时区转换 (UTC → 本地 UTC+8)
4. 冲突检测 (时间段重叠判断)
5. 生成 Markdown 表格

### 调度
```powershell
Register-ScheduledTask -TaskName "Codex Morning Briefing" `
  -Trigger (Weekly Mon-Fri 08:00) `
  -Action (powershell -File MorningBriefing.ps1)
```

## 关键教训

### 1. REST > SDK 原则
最初尝试安装 Microsoft.Graph PowerShell 模块（150MB+ 依赖），遇到 NuGet provider、模块路径、中文编码等多重问题。改用纯 `Invoke-RestMethod` 调用 Graph REST API 后，脚本从依赖沉重变零依赖，反而更可靠。

### 2. Device Code Flow 适合个人自动化
个人脚本不需要 client secret 或证书。Device Code Flow 首次浏览器授权后，用 `refresh_token` 静默续期，完美适合 Task Scheduler 场景。

### 3. 中文 Windows 编码坑
`[System.IO.File]::WriteAllText` + `.Replace()` 会破坏文件编码（中文注释变成乱码，管道符 `|` 被错误转义）。必须用 `Out-File -Encoding UTF8` 写入。

### 4. 路径必须用字面量
当 `$env:USERPROFILE` 包含中文用户名（吴）时，`Start-Process` 的 `RedirectStandardOutput` 会把 `*>&1` 拼接到路径中。使用 `'C:\Users\吴\...'` 字面量避免。

## 关联
- patterns/powershell-graph-devicecode-auth.md (可复用认证模式)
- lessons/rest-over-sdk-windows.md (轻量 REST 优先原则)
- mistakes/encoding-string-replace-windows.md (编码替换陷阱)
- anti-patterns/hardcoded-configuration.md (token 路径应走环境变量)

## 标签
#automation #powershell #microsoft-graph #outlook #task-scheduler #oauth #device-code #briefing
