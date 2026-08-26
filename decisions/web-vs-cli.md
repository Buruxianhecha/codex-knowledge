---
status: active
confidence: 0.88
reuse_count: 1
last_used: 2026-05-25
verified_in: [pdf-to-excel]
expires_after: none
next_step: v2 评估增加 CLI 批处理入口
cost:
  token_cost: none
  latency: moderate
  complexity: medium
  maintenance: medium
  scalability: moderate
cross_refs:
  - projects/2026-05-24-pdf-to-excel.md
---
# Web 应用 vs CLI 工具

`pdf-to-excel` v1 选择 Flask Web 以获得上传、账户和历史记录体验；代价是部署、鉴权和批处理复杂度更高。未来如需要批量本地自动化，再增加 CLI 作为同一核心服务的入口。
