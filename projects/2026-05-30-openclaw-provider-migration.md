---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-30
verified_in: [openclaw-config]
cross_refs:
  - lessons/provider-alias-vs-model-namespace.md (核心经验：provider 名称不等于真实后端)
  - mistakes/model-mapping-lost-during-provider-migration.md (本次排障中的主要踩坑)
  - decisions/llm-provider-namespace-separation.md (后续配置命名决策)
  - templates/checklist/llm-provider-migration-checklist.md (可复用迁移清单)
---

# OpenClaw Provider 迁移与模型映射排障

## 基本信息

- **日期**: 2026-05-29 ~ 2026-05-30
- **状态**: 配置已迁移，经验已沉淀
- **范围**: OpenClaw 本地配置、Provider、模型清单、Chat/Agent/Codex/Fallback 映射
- **敏感信息处理**: 不记录真实 API Key、bot token、GitHub token、私有令牌

## 背景

用户要求调整 OpenClaw 的 API Provider 配置：先检查现有 OpenAI、DeepSeek、Qwen/DashScope、Claude、Gemini、OpenRouter 等来源，再备份配置、删除不需要的旧 OpenAI/Qwen 相关配置，并接入新的 OpenAI-compatible API 网关。

迁移后用户发现原先常用的 `deepseek-v4-pro` 不再显示。排查确认：该模型没有从本地模型清单中被删除，而是默认模型映射和 allowed models 从旧的 `openai/deepseek-v4-pro` 切到了新网关模型，导致 UI/默认入口不再展示旧模型。

## 关键事实

- 修改前，`openai` provider 名称实际承载的是 DeepSeek-compatible endpoint。
- 修改后，`openai` provider 指向新的 OpenAI-compatible 网关。
- `deepseek-v4-pro` 仍存在于本地 `models.json` 的 `deepseek` provider 下。
- 模型消失的直接原因不是模型文件被删，而是 `agents.defaults.model.primary`、fallbacks 和 allowed models 改变。
- Qwen/DashScope/阿里云百炼相关插件配置应先禁用或删除引用，再全文检查残留。
- 配置文件中同时存在模型配置、插件配置、渠道 token、网关 token 等敏感字段，归档时必须只记录模式，不记录值。

## 本次可复用结论

1. Provider 迁移前先做快照，至少保存当前配置、模型清单和默认映射。
2. Provider 名称不能当作真实后端来源；`openai/*` 可能只是“OpenAI-compatible API 格式”。
3. 模型是否“存在”要分三层看：在线模型列表、本地 `models.json`、UI/默认映射。
4. 删除 provider 或插件前，要先列出启用状态、引用关系和 fallback 链路。
5. 配置完成不等于可用；必须测试连接、获取模型列表、验证 Chat/Agent/Codex/Fallback 指向真实模型。
6. API 网关、模型列表、价格和缓存行为都有时效性，不能长期相信旧截图或旧配置。

## 后续规则

- OpenClaw/LLM Provider 配置变更必须执行 `templates/checklist/llm-provider-migration-checklist.md`。
- 迁移报告只展示脱敏后的 `provider name`、`base URL host/type`、模型 ID、启用状态和映射关系。
- 如果旧模型要继续使用，应把默认映射改为真实 provider 命名空间，例如 `deepseek/deepseek-v4-pro`，而不是继续依赖历史上的 `openai/deepseek-v4-pro` 偶然可用状态。

## 标签

#openclaw #codex #llm #provider #model-routing #configuration #verification
