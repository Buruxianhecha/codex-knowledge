---
status: active
confidence: 0.95
reuse_count: 2
last_used: 2026-05-26
verified_in: ["projects/2026-05-26-morning-briefing.md"]
cross_refs:
  - patterns/powershell-graph-devicecode-auth.md
---

# REST > SDK — Windows 自动化脚本中的轻量优先原则

## 一句话

在 Windows PowerShell 自动化场景中，优先用纯 REST API (`Invoke-RestMethod`) 而非安装 SDK 模块。

## 场景

需要从 PowerShell 脚本访问 REST API（如 Microsoft Graph、GitHub API），面临两个选择：

| 路线 | 典型操作 | 代价 |
|------|----------|------|
| SDK 路线 | `Install-Module Microsoft.Graph` → `Connect-MgGraph` → `Get-MgUserCalendarView` | 150MB+ 依赖、NuGet provider、模块路径问题 |
| REST 路线 | `Invoke-RestMethod https://graph.microsoft.com/v1.0/me/calendarView` | 需手动处理认证（~50 行代码） |

## 为什么 REST 优先

### 这次的真实经历

1. **SDK 安装失败链**
   - `Install-Module` → NuGet provider 未安装 → 手动安装 NuGet
   - 调用 `Connect-MgGraph` → 模块未找到（安装到错误的 PowerShell 版本路径）
   - 模块导入失败 → 无明确错误信息
   - **耗时 10+ 分钟仍未成功**

2. **REST 路线**
   - 0 依赖：仅用 PowerShell 内置的 `Invoke-RestMethod`
   - 30 行认证代码复用即可
   - Device Code Flow 足够清晰，不需要 SDK 封装

### 通用权衡

| 维度 | SDK | REST |
|------|-----|------|
| 安装 | 复杂、易失败 | 零安装 |
| 依赖 | 重（NuGet + 多个子模块） | 零依赖 |
| 可移植性 | 差（依赖模块版本） | 优秀（纯脚本复制即用） |
| 调试难度 | 高（封装层多） | 低（直接 HTTP） |
| 代码量 | 少（一行调用） | 多（需写认证） |
| 类型安全 | 有（PowerShell 对象） | 无（手动解析 JSON） |

## 决策规则

```
需要访问 REST API 的 PowerShell 脚本
    │
    ├── 单次使用 / 交互式？
    │    → SDK 可以（方便）
    │
    ├── 自动化 / Task Scheduler / 分发给他人？
    │    → REST 优先
    │
    └── API 调用的 3+ 个不同端点？
         → REST 优先（认证代码复用，SDK 可能缺端点）
```

## 适用边界

- **适合 REST**: Microsoft Graph、GitHub API、任何标准 REST API
- **考虑 SDK**: Azure 资源管理（Az module）、Exchange Online（EXO module）——这些有复杂的 PowerShell 特定语义
- **不适合 REST**: 需要 Streaming 响应、需要 WebSocket 的 API

## 关联

- patterns/powershell-graph-devicecode-auth.md (具体认证实现)
- projects/2026-05-26-morning-briefing.md (实战应用)

## 标签
#powershell #rest-api #sdk #dependency-management #automation #windows
