---
status: active
confidence: 1.0
reuse_count: 0
last_used: 2026-08-26
verified_in:
  - codex-knowledge-audit
expires_after: none
risk: high
cross_refs:
  - lessons/source-knowledge-must-survive-maintenance.md
  - templates/checklist/knowledge-maintenance-diff-checklist.md
  - QUALITY.md
---

# 知识审计把事实源过度压缩

## 症状

2026-08-26 的知识库二次审计最初正确发现了生命周期、断链、隐私和 Compact 漂移问题，但后续为了统一文档，又把多个详细事实源改写成短摘要。

典型信号包括：

- `KNOWLEDGE_BUNDLE.md` 一次减少 200+ 行。
- `user-perspective-verification.md`、`FRESHNESS.md`、`SYSTEM.md` 等源文件大量删行。
- `morning-briefing`、豆包环境、插件安装等项目细节被压成几段。
- Compact 变短本来合理，却把同样的压缩策略错误应用到源知识。

## 根因

1. 把“格式统一”与“知识蒸馏”混为一谈。
2. 只检查新内容是否正确，没有检查旧内容被删除了多少。
3. 没把事实源与派生层定义成不同保护等级。
4. 批量提交前没有设置负向 diff 门槛。
5. 自动审计当时能检查统计、链接、隐私，却不能发现语义资产被大量删除。

## 为什么危险

知识库仍可能 `CI green`、统计完全正确，却已经失去真正的经验细节。机器完整性通过不等于知识完整性通过。

这与“代码构建成功不等于用户路径成功”是同一种验证层级错误。

## 修复

- 用审计前 commit `97fdd7a...` 与当前 master 做逐文件 compare。
- 先恢复 SYSTEM/FRESHNESS/Value Rules/User Profile 等长期源文件。
- 再恢复 Project/Lesson/Pattern 的详细正文。
- 涉及隐私的文件不直接回滚旧 Blob，而是做“详细恢复 + 路径脱敏”。
- 对认证/编码等旧技术结论顺便做事实复核，而不是把错误一并恢复。
- 新增源文件缩水 CI 门控。

## 预防

- 批量维护前明确 Source / Derived / Compact 三层。
- 修改源文件后执行行数/内容 diff。
- 大幅删除必须解释“为什么删”和“知识迁移到哪里”。
- 恢复历史时也不能盲目回滚：隐私、错误事实和已 superseded 状态仍要保留新修正。

## 标签

#mistake #knowledge-base #destructive-edit #audit #regression #high-risk