---
status: active
confidence: 0.97
reuse_count: 0
last_used: 2026-06-30
verified_in: [doubao-local-environment]
cross_refs:
  - projects/2026-06-30-doubao-local-environment-repair.md
  - lessons/application-acceptance-over-command-success.md
  - lessons/2026-06-28-runtime-verification-before-workflow-claims.md
---

# 把安装脚本成功当成客户端修复完成

## 症状

修正 `postinstall.py` 的 UTF-8 读取后，`prepare.ps1` 显示成功，于是曾接近把问题判定为已解决；但豆包客户端没有稳定完成本地环境初始化，修过的环境还可能在重启后被替换。

## 根因

- 验证停在安装脚本，没有继续验证客户端接纳。
- 修改的是动态生成的实例，不是生成模板或客户端判定逻辑。
- 没有把重启保持设为完成条件。

## 影响

- 用户看到的原问题仍存在。
- 对单个临时目录的修复无法稳定复用。
- “已成功”声明会让后续排障从错误前提开始。

## 修正

本次最终按失败记录，并明确分开：脚本层通过，应用层未通过。

## 预防

1. 在任务开始时写明最终接纳者。
2. 桌面客户端修复必须验证界面状态、真实功能和重启保持。
3. 动态目录先找生成源，再决定是否修改实例。
4. 报告中分别列出命令、产物、宿主、用户路径四层结果。

## 标签

#mistake #desktop-app #verification #encoding #runtime
