# Decisions and Templates 压缩版 v4

## 关键决策

- OCR：并行择优，准确率优先，接受成本增加。
- pdf-to-excel v1：Web 优先；后续补 CLI 和标准依赖管理。
- Provider：名称表达真实来源/网关角色。
- 剑来发布：先在单文件基线上定向修玩法，结构重构后置；以测试作为未来迁移合同。
- 拾光相册：付费后端前先做本地优先测试版；Mock/Real 通过 Provider 切换。
- 运行时：`rtk` 不可用时回退原生 PowerShell，文档不能阻塞事实。

## 必用清单

- `templates/checklist/user-acceptance-checklist.md`
- `templates/checklist/public-repository-release-checklist.md`
- `templates/checklist/browser-state-app-checklist.md`
- `templates/checklist/evidence-research-checklist.md`
- `templates/checklist/llm-provider-migration-checklist.md`

## 决策记录要求

每个决策写背景、方案、采用理由、未选方案、后果、成本、适用边界和重新评估条件。
