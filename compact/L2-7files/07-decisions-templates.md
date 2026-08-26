# Decisions and Templates 压缩版 v6

## 关键决策

- OCR：并行择优换更高质量，但成本高，未来可用质量阈值跳过昂贵引擎。
- pdf-to-excel v1：Web 优先；CLI 未来复用 service 层。
- Provider：命名表达真实来源/网关角色。
- 拾光：付费后端前先本地测试版；Mock/Real 用 Provider 边界。
- 自动化：真实 Git/source of truth 高于记忆路径；运行时验证高于文档假设。
- 知识维护：事实源大幅压缩必须 diff 审核，Compact 才负责强压缩。

## 必用清单

- `templates/checklist/user-acceptance-checklist.md`
- `templates/checklist/public-repository-release-checklist.md`
- `templates/checklist/browser-state-app-checklist.md`
- `templates/checklist/evidence-research-checklist.md`
- `templates/checklist/real-reading-time-checklist.md`
- `templates/checklist/reference-preservation-checklist.md`
- `templates/checklist/bim-structure-input-checklist.md`
- `templates/checklist/knowledge-maintenance-diff-checklist.md`

Decision 记录背景、方案、理由、未选方案、后果、成本、适用边界和重新评估条件。
