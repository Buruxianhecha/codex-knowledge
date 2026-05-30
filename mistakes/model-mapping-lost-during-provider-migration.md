---
status: active
confidence: 0.95
reuse_count: 0
last_used: 2026-05-30
verified_in: [openclaw-config]
cross_refs:
  - projects/2026-05-30-openclaw-provider-migration.md (来源项目)
  - lessons/provider-alias-vs-model-namespace.md (预防经验)
  - decisions/llm-provider-namespace-separation.md (根因决策)
  - templates/checklist/llm-provider-migration-checklist.md (修复清单)
---

# Provider 迁移时丢失模型映射

## 症状

用户原先明确使用 `openai/deepseek-v4-pro` 作为 primary。Provider 迁移后，`deepseek-v4-pro` 不再显示，用户以为模型映射被删除。

## 根因

模型清单没有被删除，真正变化的是默认映射：

- primary 从旧 DeepSeek 模型切换到了新网关模型。
- fallback 链路也切换到了新网关模型。
- allowed/default models 不再包含旧 `openai/deepseek-v4-pro`。
- `openai` provider 的真实后端语义发生了变化。

## 为什么危险

这种错误很隐蔽：连接测试可能通过，新 provider 也能返回模型列表，但用户熟悉的模型入口消失。它不是“API 不通”，而是“路由语义变了”。

## 预防

迁移前后必须对比：

1. provider 列表与 base URL 类型。
2. `models.json` 中每个 provider 的模型 ID。
3. Chat/Agent/Codex/Fallback 映射。
4. allowed/default models。
5. 旧 primary 是否仍有等价的新命名空间。

## 修复原则

如果旧模型仍要保留，优先绑定真实 provider 命名空间，例如 `deepseek/deepseek-v4-pro`。不要依赖“`openai` provider 正好指向 DeepSeek”的历史状态。

## 标签

#mistake #openclaw #llm #model-routing #provider #configuration
