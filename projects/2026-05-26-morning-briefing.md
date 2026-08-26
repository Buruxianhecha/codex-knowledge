---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-05-26
verified_in: []
expires_after: 2026-11-26
cross_refs:
  - patterns/powershell-graph-devicecode-auth.md
  - lessons/rest-over-sdk-windows.md
  - mistakes/encoding-string-replace-windows.md
  - anti-patterns/hardcoded-configuration.md
---
# morning-briefing — Codex 晨间简报自动化

工作区用 `%USERPROFILE%\Documents\Codex\briefings\` 表示，不保存具体用户名。

方案：Microsoft Graph REST + Device Code Flow + PowerShell + Task Scheduler。首次交互授权与后续无人值守是两个阶段；没有真实完成首次授权时，不把“认证流程写好”表述为“自动化已经无人值守运行”。

Windows 非 ASCII 用户路径应使用环境变量/`Join-Path` 构造，显式 UTF-8 读写，并对含空格/中文路径做集成测试；不再把具体用户目录字面量当通用修复方案。
