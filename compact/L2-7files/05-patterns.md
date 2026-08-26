# Patterns 压缩版 v5

- 多引擎择优：active，只有一个独立项目证据。
- SQLite 渐进迁移：active，只有一个独立项目证据。
- 输出质量门控：active，只有一个独立项目证据。
- 绝对路径 v1：superseded；当前使用“source of truth + 绝对路径 + 运行时能力检查”。
- Auth Provider：UI -> AuthService -> Mock/Real Provider。
- 单调存档事务：读取更晚快照 -> 拒绝旧页 -> 纯转换 -> 同步持久化。
- Activity-Gated Time：route/visible/focused/idle/session 门控 + 幂等 chunk。
- Evidence Ledger：材料表 + 命题表 + 冲突裁决。
- Multi-Agent Shared Schema：按 DAG 并行独立任务，共用 facts/evidence/conflicts/verification/unresolved 后统一审计。
