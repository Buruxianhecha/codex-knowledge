# OpenClaw Provider 配置模板（脱敏）

> 只作为结构参考。不要把真实 API Key、token、bot token、私有 base URL 原样写入知识库。

## Provider 模板

```json
{
  "models": {
    "providers": {
      "provider-name": {
        "baseUrl": "https://example-gateway.invalid/v1",
        "apiKey": "ENV_VAR_OR_SECRET_REF",
        "api": "openai-completions",
        "timeoutSeconds": 60,
        "models": []
      }
    },
    "mode": "merge"
  }
}
```

## Agent 默认模型模板

```json
{
  "agents": {
    "defaults": {
      "model": {
        "primary": "provider-name/model-id",
        "fallbacks": [
          "provider-name/fallback-model-1",
          "provider-name/fallback-model-2"
        ]
      },
      "models": {
        "provider-name/model-id": {},
        "provider-name/fallback-model-1": {},
        "provider-name/fallback-model-2": {}
      }
    }
  }
}
```

## 命名建议

| 后端类型 | provider 名称 |
|----------|---------------|
| OpenAI 官方 | `openai` |
| DeepSeek 官方或兼容接口 | `deepseek` |
| 第三方 OpenAI-compatible 网关 | `openai-gateway` |
| OpenRouter | `openrouter` |
| 本地代理 | `local-proxy` |

## 禁止写入知识库的字段

- 真实 API Key。
- Telegram/WeChat/Discord bot token。
- GitHub token。
- 本地 gateway token。
- 用户私有 endpoint 中带鉴权或身份信息的 URL。

## 推荐报告格式

```markdown
| Provider | Enabled | Base URL 类型 | API 协议 | Key 状态 | 模型数 |
|----------|---------|---------------|----------|----------|--------|
| openai-gateway | true | OpenAI-compatible gateway | openai-completions | 已配置，已脱敏 | 12 |
| deepseek | true | DeepSeek-compatible endpoint | openai-completions | 已配置，已脱敏 | 4 |
```
