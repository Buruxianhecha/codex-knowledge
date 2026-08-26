---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-06-30
verified_in: [doubao-local-environment]
expires_after: 2026-11-30
cross_refs:
  - projects/2026-08-26-two-month-learning-audit.md
  - lessons/application-acceptance-over-command-success.md
  - mistakes/doubao-script-success-without-client-acceptance.md
  - lessons/2026-06-28-runtime-verification-before-workflow-claims.md
---
# 豆包本地电脑工作环境故障处理

- 日期：2026-06-30
- 最终状态：脚本层修复成功，客户端层未闭环。
- 环境特征：Windows、`%USERPROFILE%` 含非 ASCII/中文字符、客户端动态生成 sandbox。

已确认：一个具体 sandbox 的 `postinstall.py` 存在非 ASCII 用户路径读取问题；显式 UTF-8 后 `prepare.ps1` 可成功结束，但客户端未稳定接纳，且环境实例可能被重建/替换。

结论：命令退出成功不证明宿主应用接受；动态生成目录中的手工补丁默认是临时补丁；真正根因可能位于模板、生成器、登记流程或接纳条件。
