# Checklist：知识库维护 / 批量审计 Diff 保护

## 开始前

- [ ] 区分事实源、派生层、Compact。
- [ ] 冻结本次允许改变的目标：元数据、隐私、事实纠错、索引还是正文重构。
- [ ] 记录维护前 commit SHA。
- [ ] 批量操作前确认没有凭据和未提交私密文件。

## 事实源保护

- [ ] `projects/lessons/patterns/mistakes/decisions/anti-patterns/preferences` 默认不得为了缩短而批量重写。
- [ ] SYSTEM / FRESHNESS / QUALITY / Value Rules 等治理源文件同样受保护。
- [ ] 补 YAML 时不顺手改正文。
- [ ] 隐私脱敏只替换敏感片段，不删除无关上下文。
- [ ] 修错误事实时保留“旧结论为什么错”的审计价值。

## Diff 门控

- [ ] 比较维护前 SHA 与候选 commit。
- [ ] 检查每个源文件 additions/deletions。
- [ ] 20+ 行文件若减少 40%+，逐篇人工解释。
- [ ] 大幅缩短必须说明知识迁移到哪个事实源；没有去向则不得合并。
- [ ] 新增内容之外，同时检查“什么内容消失了”。

## 派生层

- [ ] Index 与真实文件树数量一致。
- [ ] Memory 只做高密度速查，不冒充事实源。
- [ ] Bundle 保持单文件可接管能力，不能退化成几十行目录摘要。
- [ ] Compact 可以显著压缩，但必须声明 Index/Memory/Bundle 源版本。

## 生命周期与证据

- [ ] Pattern 至少两个独立成功项目才 `verified`。
- [ ] Best practice 至少三个成功应用 + 三个月稳定 + 可执行验收。
- [ ] `superseded` 保留替代链，不删除历史。
- [ ] 测试/CI 声明绑定具体 SHA 和测试深度。

## 安全

- [ ] 扫描 API key/token/cookie/login state。
- [ ] 扫描具体用户主目录。
- [ ] 示例也尽量使用 `%USERPROFILE%` / `$HOME`，不要伪造看似真实的用户名路径。

## 提交前

- [ ] `scripts/validate_knowledge.py` 通过。
- [ ] `scripts/check_source_regression.py` 通过。
- [ ] 候选 commit compare 没有无法解释的删除。
- [ ] 推送后读取 master HEAD。
- [ ] 当前 HEAD 的 Knowledge Audit 为 success。

只有经过人工审查确实需要大幅压缩源知识时，提交信息才可加入 `[allow-source-compaction]`；该标记必须伴随明确的迁移/重构说明。