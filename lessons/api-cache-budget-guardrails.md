---
status: active
confidence: 0.85
reuse_count: 0
last_used: 2026-05-30
verified_in: [openclaw-config, tavily-skill-validation]
cross_refs:
  - lessons/user-perspective-verification.md (成本异常也需要真实验证)
  - FRESHNESS.md (API 模型、价格、缓存行为属于高时效知识)
  - templates/checklist/llm-provider-migration-checklist.md (迁移时加入成本检查)
---

# API 缓存与额度消耗要有护栏

> API 调用能成功不代表配置健康；缓存未命中、模型路由错误或高级检索参数可能让额度异常消耗。

## 来源

- OpenClaw 配置排查中，用户特别要求关注缓存配置、代理配置、环境变量冲突、模型路由异常。
- Tavily 验证中曾主动使用 `advanced` 与 raw content 模式进行高消耗测试，说明“成本行为”需要显式记录和验证。

## 经验

任何会消耗 API 额度的工具，至少区分三种运行模式：

| 模式 | 用途 | 成本策略 |
|------|------|----------|
| smoke test | 验证连接和认证 | 最小输入、一次调用、记录模型 |
| functional test | 验证完整链路 | 限定样本、限定并发、检查输出 |
| stress/deep test | 验证深度能力 | 只有用户确认后才扩大深度或原文抓取 |

缓存相关字段也要视为配置的一部分，而不是运行时细节。尤其要关注：

- 是否命中 provider 支持的 prompt/cache 机制。
- 模型价格是否区分 input、output、cache read、cache write。
- 网关是否正确回传 usage。
- 流式输出时 usage 是否可用。
- 代理或 fallback 是否把请求路由到了更贵模型。

## 检查清单

- 连接测试前记录目标 provider 和模型。
- 使用最小 prompt 做 smoke test，不用大上下文。
- 对比 usage 中 input/output/cache 字段是否合理。
- 查询本地 usage/cache 文件是否持续增长异常。
- 高消耗测试前明确用户目的：验证质量、验证深度、还是消耗额度压力测试。
- 报告中写“成本风险”和“是否命中缓存”，但不写密钥和账单私密数据。

## 标签

#api #cache #quota #cost-control #llm #verification
