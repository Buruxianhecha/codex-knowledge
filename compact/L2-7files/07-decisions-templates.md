# Decisions and Templates 压缩版 v5

## 关键决策

- OCR/多引擎：质量择优，但通用 pattern 目前仍 active。
- Provider：名称与真实来源/网关角色分离。
- 拾光：付费后端前先本地测试；Mock/Real 用 Provider 切换。
- 自动化：先核验 source of truth，再用绝对路径和当前 shell 能力。
- 复杂任务：先依赖 DAG，独立节点并行，共享 Schema 后统一审计。
- 知识库：主事实源更新后必须同步 Index/Memory/Bundle/Compact，并由 CI 审计。

## 必用清单

`user-acceptance`、`public-repository-release`、`browser-state-app`、`evidence-research`、`real-reading-time`、`reference-preservation`、`bim-structure-input`。

决策记录写背景、采用理由、替代方案、后果、成本、适用边界和重新评估条件。
