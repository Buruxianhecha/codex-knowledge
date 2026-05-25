# Knowledge Index — 完整知识索引 v2

> 最后更新: 2026-05-25 | 总条目: 23 | 全部有效

---

## 项目 (1)

| 文件 | 项目 | 日期 | 状态 |
|------|------|------|------|
| [projects/2026-05-24-pdf-to-excel.md](projects/2026-05-24-pdf-to-excel.md) | PDF→Excel 转换工具 | 2026-05-24 | v1 完成 |

## 经验 (5)

| 文件 | 经验 | 状态 | 关联 |
|------|------|------|------|
| [lessons/multi-engine-parallel-select.md](lessons/multi-engine-parallel-select.md) | 多引擎并行择优 > 串行降级 | `active` | → patterns/ + decisions/ |
| [lessons/quality-gating.md](lessons/quality-gating.md) | 质量门控比完美解析更重要 | `active` | → patterns/ + empty-over-fake |
| [lessons/empty-over-fake.md](lessons/empty-over-fake.md) | 空值优于假数据 | `active` | → quality-gating |
| [lessons/abstract-on-second.md](lessons/abstract-on-second.md) | 第二个实现时就该抽象 | `active` | → mistakes/copy-paste |
| [lessons/v1-hardening.md](lessons/v1-hardening.md) | v1 完成后的债务清理 | `active` | → mistakes/flask-debug + download-no-auth + dead-code |

## 模式 (4)

| 文件 | 模式 | 状态 | 关联 |
|------|------|------|------|
| [patterns/multi-engine-parallel-select.md](patterns/multi-engine-parallel-select.md) | 多引擎并行择优 | `verified` | ← lessons/ + decisions/ + templates/ |
| [patterns/sqlite-migration.md](patterns/sqlite-migration.md) | 渐进式 Schema 迁移 | `verified` | ← templates/code/ |
| [patterns/lightweight-supabase-client.md](patterns/lightweight-supabase-client.md) | 轻量 Supabase REST 客户端 | `active` | ← decisions/local-remote |
| [patterns/output-quality-gate.md](patterns/output-quality-gate.md) | 输出质量门控 | `verified` | ← lessons/quality-gating + empty-over-fake |

## 错误 (6)

| 文件 | 错误 | 根因 | 关联 |
|------|------|------|------|
| [mistakes/copy-paste-ocr-engines.md](mistakes/copy-paste-ocr-engines.md) | OCR 引擎复制粘贴 | decisions/ocr-parallel | → lessons/abstract-on-second |
| [mistakes/dead-code-orphan.md](mistakes/dead-code-orphan.md) | 死代码残留 | 重构遗漏 | → lessons/v1-hardening |
| [mistakes/hardcoded-paths.md](mistakes/hardcoded-paths.md) | 硬编码路径 | 原型期习惯 | → decisions/libs-vendoring |
| [mistakes/gpt-system-prompt-unused.md](mistakes/gpt-system-prompt-unused.md) | GPT prompt 未生效 | 缺少集成测试 | → lessons/abstract-on-second |
| [mistakes/flask-debug-true.md](mistakes/flask-debug-true.md) | Flask debug 上线 | 无部署检查 | → lessons/v1-hardening + download-no-auth |
| [mistakes/download-no-auth.md](mistakes/download-no-auth.md) | 下载无鉴权 | 开发遗漏 | → lessons/v1-hardening + flask-debug-true |

## 决策 (4)

| 文件 | 决策 | 后果 | 关联 |
|------|------|------|------|
| [decisions/ocr-parallel-vs-sequential.md](decisions/ocr-parallel-vs-sequential.md) | OCR 并行择优 | ✅准确率高 ❌成本高 | → patterns/ + mistakes/copy-paste |
| [decisions/libs-vendoring.md](decisions/libs-vendoring.md) | vendoring vs pip | ❌环境无法复现 | → mistakes/hardcoded-paths |
| [decisions/local-remote-dual-write.md](decisions/local-remote-dual-write.md) | SQLite+Supabase 双写 | ✅离线 ❌同步不全 | → patterns/lightweight-supabase |
| [decisions/web-vs-cli.md](decisions/web-vs-cli.md) | Web vs CLI | ✅友好 ❌不能批量 | — |

## 模板 (6)

| 文件 | 类型 | 来源 |
|------|------|------|
| [templates/code/flask-session-secret.py](templates/code/flask-session-secret.py) | 代码 | pdf-to-excel |
| [templates/code/multi-engine-extractor.py](templates/code/multi-engine-extractor.py) | 代码 | patterns/multi-engine-parallel-select |
| [templates/code/sqlite-migration-helper.py](templates/code/sqlite-migration-helper.py) | 代码 | patterns/sqlite-migration |
| [templates/config/env-web-app.env](templates/config/env-web-app.env) | 配置 | 通用 |
| [templates/config/gitignore-python-web](templates/config/gitignore-python-web) | 配置 | 通用 |
| [templates/structure/python-web-project.md](templates/structure/python-web-project.md) | 结构 | 通用 |

## 系统文件 (4)

| 文件 | 用途 |
|------|------|
| [SYSTEM.md](SYSTEM.md) | 四层身份定义 |
| [FRESHNESS.md](FRESHNESS.md) | 知识保鲜规则 |
| [.value-rules.md](.value-rules.md) | 价值判断 + 交叉引用 + 演化 |
| [.codex-instructions.md](.codex-instructions.md) | AI 自举指南 |

---

## 标签索引

| 标签 | 条目数 | 相关文件 |
|------|--------|----------|
| #architecture | 4 | multi-engine-parallel-select, abstract-on-second, local-remote-dual-write, ocr-parallel-vs-sequential |
| #security | 2 | flask-debug-true, download-no-auth |
| #ocr | 5 | multi-engine-parallel-select, copy-paste-ocr-engines, gpt-system-prompt-unused, quality-gating, ocr-parallel-vs-sequential |
| #technical-debt | 4 | copy-paste-ocr-engines, abstract-on-second, v1-hardening, libs-vendoring |
| #quality | 3 | quality-gating, empty-over-fake, output-quality-gate |
| #database | 2 | sqlite-migration, local-remote-dual-write |
| #refactoring | 3 | copy-paste-ocr-engines, dead-code-orphan, abstract-on-second |
| #engineering | 1 | v1-hardening |
| #portability | 2 | hardcoded-paths, libs-vendoring |
