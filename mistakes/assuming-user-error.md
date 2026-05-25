---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-25
verified_in: []
cross_refs:
  - lessons/user-perspective-verification.md (验收核心原则)
  - mistakes/flask-debug-true.md (同类型：未检查生产配置)
  - anti-patterns/deliver-without-verification.md (根因反模式)
---

# 错误：先假设用户操作失误

## 来源
- 日期: 2026-05-25
- 项目: codex-knowledge 知识库建设

## 错误模式

用户反馈问题时，开发者的本能反应：
```
用户: 图标还是老样子
开发者: 是不是你没刷新？是不是你看错了？
```

## 为什么这是错误的

1. **信任破坏**：暗示用户"你不懂"，打击协作信任
2. **延迟修复**：真正的问题被忽略，拖延解决时间
3. **重复发生**：同样的 bug 被反复报告但未修复

## 正确模式

用户反馈问题时的排查优先级：
```
1. 环境     — 操作系统、依赖版本、PATH
2. 兼容性   — 浏览器/运行时差异
3. 缓存     — 浏览器缓存、编译缓存、CDN
4. 编码     — 字符集、换行符、BOM
5. 路径     — 硬编码路径、相对路径、空格/中文
6. 运行时   — 实际报错、日志、进程状态
```

## Root Cause

开发者天然倾向"自我辩护"——相信代码是正确的，用户操作有问题。但事实上：
- 环境差异是最大 bug 来源
- 真实运行结果才是唯一真理
- 用户反馈是免费的 QA

## 行动指南

1. 用户反馈问题 → 立即重新检查最终结果，不争论
2. 按排查优先级逐项检查
3. 复现问题后才讨论解决方案
4. 修复后自己按验收清单验证

## 标签
#mistake #user-feedback #debugging #mindset
