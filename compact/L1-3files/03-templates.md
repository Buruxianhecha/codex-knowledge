# Templates 压缩版 v6

## 公开仓库发布

```text
[ ] 冻结必须项和禁止项
[ ] 本地形成完整快照
[ ] 明确测试类型/覆盖范围
[ ] 扫描 secret/登录态/私人路径/文档元数据
[ ] push 后回读 HEAD/关键 Blob
[ ] CI 必须属于当前 HEAD
[ ] 从 README/真实入口走用户路径
```

## 知识维护

```text
[ ] 区分 Source / Derived / Compact
[ ] 记录维护前 SHA
[ ] 源文件批量改动先 compare
[ ] 20+ 行事实源减少 40%+ 必须人工解释
[ ] 隐私脱敏只改敏感片段
[ ] schema migration 不顺手重写正文
[ ] Source Regression Guard 通过
```

## 浏览器状态与真实时长

```text
[ ] schemaVersion + ownerId/runId + revision
[ ] 重复/旧标签动作 no-op
[ ] 随机结果持久化
[ ] 完成/死亡/删除刷新后保持
[ ] readingActive = route && visible && focused && !idle && session
[ ] chunk 幂等，多标签防双算，sleep delta clamp
```

## 深度写作/多智能体

```text
[ ] 问题、范围、资料截止日
[ ] Material Ledger / Claim Ledger
[ ] 支持、反证、允许措辞、禁止升级
[ ] Agent 共享 Schema，不直接拼 prose
[ ] 冲突统一裁决 + 引用审计
[ ] Word/PDF 署名和隐藏元数据
```

## 修改现有作品/BIM

```text
[ ] Must change / Must preserve / May adapt
[ ] 未指定区域负向 diff
[ ] reference plane -> size -> offset -> local addition -> scope
[ ] 平面/断面/3D 三方复核
```
