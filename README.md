# Codex Knowledge Base — 可移植工程经验库 v2

> 自包含、可蒸馏、可演化、保鲜的工程经验体系。
> 任何 AI 实例只需阅读本文件即可了解全局。

---

## 快速开始（给 AI 读的）

如果你是 AI 助手，第一次打开这个库：

### 必读（10 分钟）
1. **`SYSTEM.md`** — 理解你的四层身份
2. **`README.md`** — 你在这里
3. **`preferences/user-profile.md`** — 用户编码风格和偏好

### 选读（按需）
4. **`KNOWLEDGE_INDEX.md`** — 完整知识索引 + 标签体系
5. **`memory/distilled-memory.md`** — 项目和经验速查表

### 参考
6. **`FRESHNESS.md`** — 什么时候需要联网验证
7. **`.value-rules.md`** — 什么值得记录
8. **`.codex-instructions.md`** — 完整操作手册

## 库的四个支柱

| 支柱 | 文件 | 含义 |
|------|------|------|
| 🧠 积累 | `projects/` `lessons/` `patterns/` `mistakes/` `decisions/` | 从项目中提取可复用知识 |
| 🔗 关联 | 条目间交叉引用 | 错误↔决策↔经验↔模式 可追溯 |
| 🔄 演化 | `.value-rules.md` | 知识有生命周期，会过时、被验证、被替代 |
| 🕐 保鲜 | `FRESHNESS.md` | API/框架/模型变动时主动验证，稳定知识不浪费资源 |

## 目录结构

```
D:\Codex-Knowledge\
├── README.md                    ← 库入口
├── SYSTEM.md                    ← 角色定义（四层身份）
├── FRESHNESS.md                 ← 知识保鲜规则
├── KNOWLEDGE_INDEX.md           ← 完整索引 + 标签
├── .codex-instructions.md       ← 给 AI 的自举指南
├── .value-rules.md              ← 价值判断 + 交叉引用 + 演化
├── preferences/                 ← 用户画像
├── memory/                      ← 蒸馏记忆
├── projects/       (1)          ← 项目总结
├── lessons/        (5)          ← 长期经验
├── patterns/       (4)          ← 可复用模式
├── mistakes/       (6)          ← 错误教训
├── decisions/      (4)          ← 设计决策
└── templates/      (6)          ← 代码/配置/结构模板
```

## 知识状态一览

| 状态 | 数量 | 说明 |
|------|------|------|
| `active` | 14 | 当前有效 |
| `verified` | 3 | 多项目验证（多引擎择优、SQLite迁移、质量门控） |
| `best_practice` | 0 | 尚无（需要 3+ 验证 + 3 月稳定） |
| `deprecated` | 0 | 尚无过时条目 |

## 用户速写

- 中文为主，Python 技术栈
- 实用主义编码风格，快速原型 → v1 硬化迭代
- OCR/文档处理方向，Flask Web 应用
- 不装 C 盘，在意品牌一致性

---

*最后更新: 2026-05-25 20:37 | 项目: 1 | 条目: 23 | 状态: 全部有效*

---

## 分享指南：按对方能力选格式

| 对方工具 | 文件限制 | 用这个 | 操作 |
|----------|----------|--------|------|
| ChatGPT / Claude 网页版 | 无文件读取 | `KNOWLEDGE_BUNDLE.md` | 复制粘贴全部文本 |
| Kimi / DeepSeek | 无文件读取 | `KNOWLEDGE_BUNDLE.md` | 上传或粘贴 |
| AI 工具(≤5 文件限制) | 3-5 个 | `compact/L1-3files/` | 上传 3 个文件 |
| AI 工具(≤10 文件限制) | 7-10 个 | `compact/L2-7files/` | 上传 7 个文件 |
| Codex / Cursor | 无限制 | 整个文件夹 | 直接打开目录 |
| GitHub Copilot | 无限制 | GitHub 仓库 | 给链接 |

### 告诉 AI 怎么用

```
"先读 01-START-HERE.md，然后帮我 [你的任务]"
```


