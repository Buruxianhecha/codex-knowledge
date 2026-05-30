---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-30
verified_in: [openclaw-config]
cross_refs:
  - projects/2026-05-30-openclaw-provider-migration.md (来源项目)
  - lessons/provider-alias-vs-model-namespace.md (决策依据)
  - mistakes/model-mapping-lost-during-provider-migration.md (已知后果)
---

# LLM Provider 命名空间分离

## 背景

OpenClaw 配置中曾出现 `openai` provider 实际指向 DeepSeek-compatible endpoint 的情况。迁移到新的 OpenAI-compatible 网关后，旧的 `openai/deepseek-v4-pro` 映射失去原有语义。

## 决策

LLM 配置中，Provider 名称应尽量表达真实来源或网关角色，而不是只表达 API 协议。

## 推荐命名

| 场景 | 推荐 provider 名称 | 避免 |
|------|--------------------|------|
| 官方 OpenAI | `openai` | 把其他网关也叫 `openai` |
| DeepSeek 官方或兼容接口 | `deepseek` | `openai/deepseek-*` 长期使用 |
| 第三方聚合网关 | `openai-gateway`、`sub-gateway`、`openrouter` | 混用真实厂商名 |
| 本地代理 | `local-proxy` | 只叫 `openai` |

## 后果

- 优点：模型映射更清楚，迁移时不容易误删或误判。
- 优点：排查时能快速区分协议、provider 和真实后端。
- 代价：初次配置时需要多写几个 provider 名称和映射。

## 应用规则

- 默认模型应指向真实 provider 命名空间。
- 如果 provider 后端发生变化，必须同步检查所有模型映射。
- 配置文档只记录 provider 角色和脱敏 URL 类型，不记录密钥。

## 标签

#decision #llm #provider #namespace #configuration #openclaw
