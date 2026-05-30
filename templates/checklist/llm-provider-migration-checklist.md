# LLM Provider 迁移检查清单

> 适用于 OpenClaw、Codex、OpenCode、Cursor 等使用多个 LLM Provider 的本地工具。

## 1. 迁移前快照

- [ ] 备份主配置文件。
- [ ] 备份本地模型清单，例如 `models.json`。
- [ ] 记录当前 enabled/disabled provider。
- [ ] 记录当前 primary、fallback、Chat、Agent、Codex 映射。
- [ ] 记录当前 allowed/default models。
- [ ] 只记录脱敏字段，不保存真实 API Key、token、bot token。

## 2. Provider 审计

| 检查项 | 结果 |
|--------|------|
| Provider 名称是否表达真实后端 |  |
| Base URL 是否为预期网关 |  |
| API 协议是否匹配 |  |
| Key 是否通过环境变量或安全存储引用 |  |
| 是否存在重复 provider |  |
| 是否存在禁用但仍被引用的 provider |  |

## 3. 模型清单验证

- [ ] 调用在线模型列表。
- [ ] 对比本地模型清单。
- [ ] 确认 primary 模型在线存在。
- [ ] 确认 fallback 模型在线存在。
- [ ] 确认 UI 允许列表没有过滤掉目标模型。
- [ ] 如果模型在线存在但本地不显示，检查过滤、allowed list 和 provider 绑定。

## 4. 路由验证

| 路由 | 当前模型 | 是否存在 | 是否可调用 |
|------|----------|----------|------------|
| Chat |  |  |  |
| Agent |  |  |  |
| Codex |  |  |  |
| Fallback 1 |  |  |  |
| Fallback 2 |  |  |  |

## 5. 成本与缓存

- [ ] 使用最小 prompt 做 smoke test。
- [ ] 检查 usage 是否返回 input/output。
- [ ] 检查 cache read/write 字段是否合理。
- [ ] 确认 fallback 没有路由到高价模型。
- [ ] 高消耗测试前得到用户明确确认。

## 6. 提交前安全检查

- [ ] 搜索 `sk-`、`ghp_`、`github_pat_`、`botToken`、`Bearer `。
- [ ] 搜索真实手机号、邮箱、聊天 bot token。
- [ ] 报告中只展示 Key 前缀或 `<api-key>` 占位符。
- [ ] 不把本地私有配置原样复制进知识库。

## 7. 最终报告

报告至少包含：

- 删除或禁用的配置。
- 保留的配置。
- 当前实际 provider。
- 脱敏后的 base URL 类型。
- 在线模型列表摘要。
- Chat/Agent/Codex/Fallback 映射。
- 是否可以正常调用。
- 发现并修复的冲突。
