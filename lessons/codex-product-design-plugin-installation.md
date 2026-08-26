---
status: active
confidence: 0.92
reuse_count: 0
last_used: 2026-08-26
verified_in: [codex-desktop]
expires_after: 2026-09-09
cross_refs:
  - lessons/knowledge-portability.md
  - lessons/2026-06-28-runtime-verification-before-workflow-claims.md
  - patterns/duplicate-repo-source-of-truth-check.md
---

# Codex Product Design 插件安装经验（2026-06-09 历史快照）

> 插件生态与 marketplace 名称变化很快。本条保留当时真实排障链，用于复用“如何判断插件来自哪个市场”的方法；再次执行前必须重新读取当前官方文档和本机运行时列表。

## 来源

- 日期：2026-06-09。
- 场景：目标 `Product Design` 插件在当时默认市场中没有按预期出现，需要定位真实 marketplace 并安装。

## 当时观察到的链路

1. 在默认 `openai-curated` 市场列表中没有找到预期的 `product-design`。
2. 直接按默认市场安装返回目标不存在。
3. 检查到另一个官方插件/角色市场仓库，其 marketplace manifest 中存在对应条目。
4. 将该市场镜像到一个持久工作区，例如 `<WORKSPACE>\codex-plugin-mirrors\<marketplace>`。
5. 使用客户端当时支持的 marketplace add 命令注册镜像。
6. 从对应 marketplace 安装目标插件。
7. 最后再次读取插件列表，确认目标显示为 installed/enabled。

历史命令形态曾类似：

```text
codex plugin marketplace add <LOCAL_MARKETPLACE_PATH>
codex plugin add product-design@<MARKETPLACE_ID>
codex plugin list --marketplace <MARKETPLACE_ID>
```

这些命令和 marketplace ID 只表示 2026-06-09 的运行时快照，不应在未来版本里无验证照抄。

## 为什么这类问题容易误判

“插件名存在”至少分四层：

```text
用户看到的展示名
-> plugin ID
-> marketplace ID / manifest
-> 当前客户端已同步并启用的 marketplace snapshot
```

任何一层不一致，都可能表现成“明明有这个插件却安装不了”。

## 安装前检查

- 目标名称和真实 plugin ID 是否一致。
- 当前客户端支持哪些 marketplace 命令。
- 默认市场里是否真的有该 ID。
- 独立 marketplace 是否来自可信/官方来源。
- 本地镜像是不是最新且 manifest 可解析。
- 是否存在同名旧缓存或旧版本。

## 安装后验证

不要以“安装命令退出成功”为终点：

1. 回读插件列表。
2. 确认 installed/enabled 状态。
3. 读取实际安装/缓存路径时只在本机日志使用；公开知识库写 `%USERPROFILE%\.codex\...` 或 `<CODEX_HOME>`。
4. 实际触发一次该插件的核心能力，确认宿主客户端能够加载。
5. 客户端重启后再次检查是否仍启用。

## 可复用结论

- 不要只看插件展示名；同时确认 plugin ID 与 marketplace ID。
- 默认市场找不到目标时，先调查 marketplace，而不是重复同一失败命令。
- 文档中存在的市场/命令不等于当前客户端已经具备该运行时能力。
- 本地镜像要有明确 source of truth，避免多个旧副本。
- 公开知识不记录本机用户名或私有路径。

## 标签

#codex #plugin #marketplace #product-design #installation #runtime-verification