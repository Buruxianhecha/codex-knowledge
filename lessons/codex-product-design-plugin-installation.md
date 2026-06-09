---
status: active
confidence: 0.92
reuse_count: 0
last_used: 2026-06-09
verified_in: [codex-desktop]
cross_refs:
  - lessons/knowledge-portability.md
  - patterns/
---

# Codex Product Design 插件安装经验

## 来源
- 日期: 2026-06-09
- 场景: 用户要求安装 `Product Design` 插件，并最终要求推送到 GitHub 日记仓库

## 结论
- `openai-curated` 默认市场里没有 `product-design`，直接安装会失败。
- 官方可用来源是独立市场 `role-specific-plugins`。
- 先将官方仓库镜像作为本地 marketplace 接入，再执行 `codex plugin add product-design@role-specific-plugins`，即可成功安装。

## 验证链
1. `codex plugin list --marketplace openai-curated` 未出现 `product-design`。
2. `codex plugin add product-design@openai-curated` 返回插件不存在。
3. 克隆官方仓库 `openai/role-based-plugins` 到 `D:\Codex-Knowledge\codex-plugin-mirrors\role-based-plugins`。
4. 确认其 `.agents/plugins/marketplace.json` 中包含 `product-design` 条目。
5. `codex plugin marketplace add "D:\Codex-Knowledge\codex-plugin-mirrors\role-based-plugins"` 成功。
6. `codex plugin add product-design@role-specific-plugins` 成功，安装路径为 `C:\Users\吴\.codex\plugins\cache\role-specific-plugins\product-design\0.1.41`。
7. `codex plugin list --marketplace role-specific-plugins` 显示 `installed, enabled`。

## 适用经验
- 安装插件时，不要只看名称，要同时确认：
  - 插件 ID
  - marketplace 名称
  - 本机是否已同步该市场快照
- 当默认市场找不到目标插件时，优先检查是否存在独立市场，而不是反复尝试同一个 `openai-curated` 安装命令。
- 可持久化的市场镜像应放在 `D:`，便于后续复用和审计。

## 标签
#codex #plugin #marketplace #product-design #installation #role-specific-plugins
