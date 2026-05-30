---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-05-30
verified_in: [openclaw-config]
cross_refs:
  - projects/2026-05-30-openclaw-provider-migration.md (来源项目)
  - mistakes/model-mapping-lost-during-provider-migration.md (反面案例)
  - decisions/llm-provider-namespace-separation.md (对应决策)
  - anti-patterns/hardcoded-configuration.md (同属配置语义混乱问题)
---

# Provider 名称不等于真实模型来源

> `openai` 可能表示 OpenAI-compatible 协议，而不一定表示真实调用 OpenAI 官方服务。

## 来源

- 日期: 2026-05-29 ~ 2026-05-30
- 触发: OpenClaw Provider 迁移后，原默认模型 `openai/deepseek-v4-pro` 不再显示。

## 经验

LLM 客户端里常见三层命名：

| 层次 | 例子 | 含义 |
|------|------|------|
| Provider 名称 | `openai`, `deepseek`, `codex` | 本地配置键或插件名 |
| API 协议 | `openai-completions`, `responses` | 请求/响应格式 |
| 真实后端 | DeepSeek、OpenAI-compatible 网关、自建代理 | 实际模型来源 |

这三层不能混为一谈。`openai/deepseek-v4-pro` 能工作，可能只是因为本地 `openai` provider 当时指向了 DeepSeek-compatible endpoint。后续一旦把 `openai` provider 改到真正的 OpenAI-compatible 网关，旧映射就会失效或从 UI 消失。

## 判断方法

排查模型“消失”时，按顺序确认：

1. **在线列表**: 当前 API 网关 `/models` 是否返回该模型。
2. **本地清单**: `models.json` 是否仍有该模型 ID。
3. **默认映射**: Chat/Agent/Codex/Fallback 是否还引用该模型。
4. **允许列表/过滤**: UI 是否只展示 allowed/default 中的模型。
5. **Provider 绑定**: 模型 ID 前缀是否指向真实可用 provider。

## 反模式

- 用 `openai` 作为所有 OpenAI-compatible 后端的永久命名。
- 把 `provider/model` 字符串当作不可变事实，不记录背后的 base URL 语义。
- 迁移 provider 时只测连接，不检查默认模型和 fallback。

## 稳定做法

- Provider 名称尽量表达真实来源：`deepseek`、`openai-gateway`、`openrouter`、`local-proxy`。
- 默认模型映射使用真实 provider 命名空间。
- 配置报告展示 provider、协议、后端类型、模型 ID 四列。

## 标签

#llm #provider #model-routing #configuration #openclaw
