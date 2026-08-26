# QUALITY.md 知识质量管理框架 v2

> 目标：让知识库长期可用，而不是把所有文件强行塞进同一种 YAML 模板。

## 分类 Schema

| 类别 | 规则 |
|------|------|
| projects / lessons / patterns / mistakes / decisions / anti-patterns | 新条目必须有 `status/confidence/reuse_count/last_used/verified_in/expires_after/cross_refs` |
| bad-cases | `source/date/category/severity` + 输入/期望/实际/根因/门控 |
| templates | 模板自身可直接复制/执行，通过索引和来源追踪，不强制统一 YAML |
| compact | 派生分享层，必须声明源版本，不是事实源 |
| scripts / workflows | 维护工具，不计入核心条目数 |

历史条目允许有元数据债务，但必须由自动审计列为 warning；新条目不得继续制造同类债务。

## 生命周期

允许：`active / verified / best_practice / deprecated / superseded / archived`。

- Lesson：至少两个独立证据上下文后评估 `verified`。
- Pattern：至少两个独立项目成功应用后评估 `verified`。
- Best practice：至少三个独立成功应用、稳定三个月、明确适用边界并有可执行验收方式。
- 被替代条目使用 `superseded + replaced_by`，不删除历史。

`confidence` 表示对当前陈述及其边界的信心，不等于项目成功率。

## 证据门槛

| 声明 | 最低证据 |
|------|----------|
| 文件存在 | 产物可回读 |
| 已上传 | 远端 commit/Blob/文件回读 |
| CI 通过 | 当前目标 SHA 的成功 run |
| 功能可用 | 真实核心用户路径通过 |
| 正式上线 | 安全、恢复、权限、容量、真机和运维边界通过 |
| 研究事实 | 可定位来源 + 冲突检查 |
| 强因果/主导/法律认定 | 直接支持对应强动词的证据 |

## 隐私

公开库禁止保存 API key/token/cookie/登录态、真实用户主目录名和无必要个人身份信息。路径使用 `%USERPROFILE%`、`$HOME`、`<WORKSPACE>` 等表达。

## 派生层与自动审计

事实源顺序：具体条目 -> Index -> Memory -> Bundle -> Compact。Compact 的 START-HERE 必须声明 `index/memory/bundle` 源版本。

`scripts/validate_knowledge.py` 与 `.github/workflows/knowledge-audit.yml` 检查统计、生命周期、元数据债务、cross_refs/Markdown 链接、Compact 版本、隐私路径与常见 secret 模式。CI `error=0` 是硬门槛；warning 是显式历史债务。
