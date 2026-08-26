---
status: verified
confidence: 0.96
reuse_count: 7
last_used: 2026-08-19
verified_in:
  - rabbit-livestream-research
  - dongdong-publicity-research
  - ai-agent-governance-research
  - weng-jiayi-lifecycle-research
expires_after: none
cost:
  token_cost: medium
  latency: moderate
  complexity: moderate
  maintenance: medium
  scalability: scalable
cross_refs:
  - projects/2026-08-19-evidence-research-workflow.md
  - lessons/claim-strength-must-match-evidence.md
  - templates/checklist/evidence-research-checklist.md
---

# 材料账本与命题强度校准模式

## 适用场景

深度研究、人物调查、事件复盘、论文写作、产品竞品分析以及多智能体并行检索。

## 两张核心表

### Material Ledger

| 字段 | 含义 |
|------|------|
| material_id | 稳定材料编号 |
| source_tier | S1/S2/S3/S4 |
| title / author | 标题与发布主体 |
| published_at / retrieved_at | 发布与获取时间 |
| url_or_file | 原始位置 |
| sha256 | 文件版本指纹 |
| locator | 页码、时间码、段落 |
| provenance | 原始、转述、切片、评论 |

### Claim Ledger

| 字段 | 含义 |
|------|------|
| claim_id | 稳定命题编号 |
| claim | 最小可证命题 |
| support | 支持材料 ID + 定位 |
| counterevidence | 反证或冲突材料 |
| allowed_wording | 当前可用措辞 |
| forbidden_upgrade | 证据不足的更强措辞 |
| status | verified / disputed / insufficient |

## 工作流

```text
收集材料
  -> 去重与版本哈希
  -> 建材料账本
  -> 拆最小命题
  -> 绑定正反证据
  -> 校准动词与范围
  -> 引用审计
  -> 写作
  -> 再次逐句核对
```

## 多智能体规则

- 角色按证据职责拆分，不按“各写一章”拆分。
- 所有角色使用同一材料 ID 和命题格式。
- 不能把另一个 Agent 的摘要当原始来源。
- 冲突进入统一裁决队列。
- 最终稿只使用通过引用审计的命题。

## 哈希的正确含义

SHA-256 能证明“当前材料与记录时的字节一致”，不能证明内容真实、完整或来源合法。哈希是版本证据，不是事实证据。

## 标签

#research #writing #evidence-ledger #citation #multi-agent #provenance
