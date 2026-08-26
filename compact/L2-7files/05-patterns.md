# Patterns 压缩版 v6

## 状态与选择

- 多引擎择优：active；单项目证据，成本/质量一起评分。
- 输出质量门控：active；阈值来自 bad case，不是假通用常数。
- SQLite migration：active；幂等只是起点，复杂迁移仍需事务/回滚。

## 长期状态

- Auth Provider Boundary。
- Monotonic Archive Transaction。
- Activity-Gated Time Accounting。
- Evidence Ledger + Claim Calibration。
- Multi-Agent Shared Schema。
- Single-file VM Test Harness。

## Windows / Microsoft identity

Device Code 仅用于 public client；使用自己的 App Registration client ID；`/token` 轮询包含同一 `client_id`；refresh token 按敏感凭据安全轮换。可接受依赖时优先 MSAL/token cache。
