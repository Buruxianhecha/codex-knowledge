---
status: active
confidence: 0.99
reuse_count: 1
last_used: 2026-08-26
verified_in: [windows-workflows]
expires_after: none
---
# C 盘安全规则

未经明确确认，不擅自删除或覆盖 Windows 系统盘上的系统文件、程序目录和用户配置。

公开知识库不得记录具体 Windows 用户名路径。需要表达用户工作区时使用 `%USERPROFILE%\Documents\Codex\`、`%LOCALAPPDATA%\Temp\` 或当前任务中明确且已核验的工作目录。

即使使用环境变量，删除/覆盖前也要检查解析后的真实目标。
