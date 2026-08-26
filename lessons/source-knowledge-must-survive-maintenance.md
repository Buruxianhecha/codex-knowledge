---
status: active
confidence: 0.99
reuse_count: 1
last_used: 2026-08-26
verified_in:
  - codex-knowledge-audit
expires_after: none
cross_refs:
  - mistakes/knowledge-audit-overcompressed-source-content.md
  - templates/checklist/knowledge-maintenance-diff-checklist.md
  - lessons/knowledge-portability.md
  - QUALITY.md
---

# 事实源知识必须在维护中存活，压缩只属于派生层

> 知识库维护的目标是提高可检索性和可信度，不是把原始经验改写成越来越短的摘要。

## 触发事件

2026-08-26 对 `codex-knowledge` 做完整性审计时，为统一生命周期、隐私和 Compact 层，一度把多个事实源文件同步压缩。机器统计和 YAML 更干净了，但 `projects/`、`lessons/`、`patterns/`、用户画像和长期系统文档丢失了大量可复用上下文。

随后通过 commit-to-commit diff 才发现：有些文件不是“整理”，而是一次删除几十到上百行。

## 核心分层

```text
事实源层
  projects / lessons / patterns / mistakes / decisions
  preferences / SYSTEM / FRESHNESS / QUALITY
       |
       | 蒸馏，不覆盖
       v
Index / Memory / Bundle
       |
       | 进一步压缩
       v
Compact
```

事实源允许：纠错、补证据、补边界、合并真正重复内容。

事实源不允许：为了统一风格、减少 token 或让索引更漂亮，未经 diff 审核大幅删减历史上下文。

## 为什么摘要不能替代事实源

摘要通常会丢失：

- 当时失败是怎样发生的。
- 哪一层验证通过、哪一层没通过。
- 代码/配置示例和适用边界。
- 反例和错误尝试。
- 用户为什么改变需求。
- 后来结论是如何修正的。

这些内容恰恰是下一次排障最有价值的部分。

## 维护不变量

1. **Source stays rich**：事实源保留能重建判断过程的上下文。
2. **Derived may shrink**：Memory/Bundle/Compact 可以按用途蒸馏。
3. **Diff before replace**：批量重写源文件前必须看增删规模。
4. **Large shrink is suspicious**：20 行以上源文件一次减少 40%+，默认视为高风险。
5. **Correction beats deletion**：错误知识优先改为 `superseded/deprecated` 或纠正正文，不靠删历史解决。
6. **Privacy sanitization is surgical**：隐私修复只替换敏感部分，不顺便压缩整篇。
7. **Schema migration is non-destructive**：补 YAML/元数据不能成为重写正文的理由。

## 自动防护

仓库使用 `scripts/check_source_regression.py` 在 CI 中比较 `HEAD^ -> HEAD`。受保护事实源大幅缩水时默认失败；真正需要压缩时，必须先人工审查 diff，并在提交信息显式加入 `[allow-source-compaction]`。

这个 escape hatch 是为了支持真实重构，不是绕过审计。

## 行动规则

当“整理知识库”和“保留可复用细节”发生冲突时，优先保留事实源；把 token/阅读成本问题交给 Bundle/Compact，而不是破坏源知识。

## 标签

#knowledge-management #maintenance #source-of-truth #diff #non-destructive