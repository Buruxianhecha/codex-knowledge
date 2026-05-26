# Knowledge Index — 完整知识索引 v4

> 最后更新: 2026-05-26 | 总条目: 31 | 全部有效

---

## 项目 (2)

| 文件 | 项目 | 日期 | 状态 |
|------|------|------|------|
| [projects/2026-05-24-pdf-to-excel.md](projects/2026-05-24-pdf-to-excel.md) | PDF→Excel 转换工具 | 2026-05-24 | v1 完成 |
| [projects/2026-05-25-deepseek-video.md](projects/2026-05-25-deepseek-video.md) | DeepSeek 宣传视频 | 2026-05-25 | v1 完成 |

## 经验 (7)

| 文件 | 经验 | 状态 | 关联 |
|------|------|------|------|
| [lessons/user-perspective-verification.md](lessons/user-perspective-verification.md) | 用户视角验收原则（6大核心原则） | `best_practice` | → templates/checklist/ + anti-patterns/ + mistakes/ |
| [lessons/knowledge-portability.md](lessons/knowledge-portability.md) | 知识自蒸馏与可移植性 | `active` | → KNOWLEDGE_BUNDLE.md + compact/ |
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

## 错误 (7)

| 文件 | 错误 | 根因 | 关联 |
|------|------|------|------|
| [mistakes/assuming-user-error.md](mistakes/assuming-user-error.md) | 假设用户操作失误 | 开发者自我辩护本能 | → lessons/user-perspective-verification |
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

## 反模式 (6)

| 文件 | 反模式 | 腐烂路径 |
|------|--------|----------|
| [anti-patterns/deliver-without-verification.md](anti-patterns/deliver-without-verification.md) | 未验证即交付 | 交付信任崩塌 |
| [anti-patterns/copy-paste-architecture.md](anti-patterns/copy-paste-architecture.md) | 复制粘贴架构 | 改一处漏两处 |
| [anti-patterns/hardcoded-configuration.md](anti-patterns/hardcoded-configuration.md) | 硬编码配置 | 换环境即炸 |
| [anti-patterns/business-logic-in-routes.md](anti-patterns/business-logic-in-routes.md) | 路由含业务逻辑 | 无法测试复用 |
| [anti-patterns/silent-exception-swallowing.md](anti-patterns/silent-exception-swallowing.md) | 静默吞异常 | 问题被隐藏 |
| [anti-patterns/god-file.md](anti-patterns/god-file.md) | 上帝文件 | main.py 410行 |

## 模板 (7)

| 文件 | 类型 | 来源 |
|------|------|------|
| [templates/checklist/user-acceptance-checklist.md](templates/checklist/user-acceptance-checklist.md) | 验收清单 | lessons/user-perspective-verification |
| [templates/code/flask-session-secret.py](templates/code/flask-session-secret.py) | 代码 | pdf-to-excel |
| [templates/code/multi-engine-extractor.py](templates/code/multi-engine-extractor.py) | 代码 | patterns/multi-engine-parallel-select |
| [templates/code/sqlite-migration-helper.py](templates/code/sqlite-migration-helper.py) | 代码 | patterns/sqlite-migration |
| [templates/config/env-web-app.env](templates/config/env-web-app.env) | 配置 | 通用 |
| [templates/config/gitignore-python-web](templates/config/gitignore-python-web) | 配置 | 通用 |
| [templates/structure/python-web-project.md](templates/structure/python-web-project.md) | 结构 | 通用 |

## 失败案例 (3)

| 文件 | 案例 | 门控规则 |
|------|------|----------|
| [bad-cases/gpt-system-prompt-unused.md](bad-cases/gpt-system-prompt-unused.md) | GPT prompt 定义但未传入 API | 所有 LLM 调用必须加集成测试 |
| [bad-cases/multi-line-numbers-false-positive.md](bad-cases/multi-line-numbers-false-positive.md) | 多行数字被误判为噪音 | OCR 噪音过滤必须考虑表格上下文 |
| [bad-cases/tesseract-japanese-char-limit.md](bad-cases/tesseract-japanese-char-limit.md) | Tesseract 截断 >10 字符 | OCR 输出需二次验证完整性 |

## 系统文件 (4)

| 文件 | 用途 |
|------|------|
| [SYSTEM.md](SYSTEM.md) | 五层身份定义（v4） |
| [FRESHNESS.md](FRESHNESS.md) | 知识保鲜规则 |
| [.value-rules.md](.value-rules.md) | 价值判断 + 交叉引用 + 演化 |
| [.codex-instructions.md](.codex-instructions.md) | AI 自举指南 |

---

## 标签索引

| 标签 | 条目数 | 相关文件 |
|------|--------|----------|
| #delivery | 3 | user-perspective-verification, user-acceptance-checklist, deliver-without-verification |
| #verification | 3 | user-perspective-verification, user-acceptance-checklist, deliver-without-verification |
| #user-experience | 2 | user-perspective-verification, user-acceptance-checklist |
| #quality | 5 | quality-gating, empty-over-fake, output-quality-gate, user-perspective-verification, user-acceptance-checklist |
| #architecture | 4 | multi-engine-parallel-select, abstract-on-second, local-remote-dual-write, ocr-parallel-vs-sequential |
| #security | 2 | flask-debug-true, download-no-auth |
| #ocr | 5 | multi-engine-parallel-select, copy-paste-ocr-engines, gpt-system-prompt-unused, quality-gating, ocr-parallel-vs-sequential |
| #technical-debt | 4 | copy-paste-ocr-engines, abstract-on-second, v1-hardening, libs-vendoring |
| #database | 2 | sqlite-migration, local-remote-dual-write |
| #refactoring | 3 | copy-paste-ocr-engines, dead-code-orphan, abstract-on-second |
| #engineering | 2 | v1-hardening, hardcoded-paths |
| #portability | 3 | hardcoded-paths, libs-vendoring, knowledge-portability |
| #mindset | 2 | assuming-user-error, user-perspective-verification |
| #video | 1 | deepseek-video |
| #animation | 1 | deepseek-video |
| #knowledge-management | 1 | knowledge-portability |
