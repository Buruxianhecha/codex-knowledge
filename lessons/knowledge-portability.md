---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-26
verified_in: [codex-knowledge]
cross_refs:
  - lessons/user-perspective-verification.md (同样的"用户实际可用"哲学)
  - patterns/ (所有模式都应遵循此原则)
---

# 知识自蒸馏与可移植性

> 知识库的价值不在于"存了多少"，而在于"换一个大脑还能不能用"。

## 来源
- 日期: 2026-05-25 ~ 2026-05-26
- 触发: 用户要求知识库能"随时拿过来给其他电脑的 Codex（或其他 AI）读取，立刻完整学习所有内容"

## 核心原则

### 一：自举（Self-Bootstrapping）
任何 AI 实例读取知识库后，应在 5-10 分钟内理解：
- 用户是谁、偏好什么
- 做过什么项目
- 犯过什么错误
- 用什么模式
- 做过什么决策

### 二：分层紧凑化
不是所有 AI 都能一次读 30 个文件。需要分层：
```
L0: 单文件入口 (KNOWLEDGE_BUNDLE.md) → 任何 AI 都能读
L1: 3 文件 (START-HERE + core-knowledge + templates) → 15 分钟
L2: 7 文件 (完整画像) → 30 分钟
Full: 完整目录 → 不限
```

### 三：自包含（Self-Contained）
知识库必须在以下场景独立可用：
- 离线环境
- 不同 AI 平台（ChatGPT / Claude / Codex / Cursor）
- 新电脑
- 账号丢失后

**具体措施**：
- `.codex-instructions.md` — 给未来 AI 的自举指南
- `SYSTEM.md` — 角色定义
- `memory/distilled-memory.md` — 不依赖原始记忆格式
- `KNOWLEDGE_BUNDLE.md` — 单文件完整包
- `compact/` — 分层精简版

### 四：蒸馏优先
原始对话 → 蒸馏 → 结构化条目 → 索引。
不是把聊天记录搬进知识库，而是：
1. 提取可复用模式
2. 建立交叉引用
3. 标记生命周期
4. 去重

### 五：Git 备份
- 推送到 GitHub 确保不丢失
- `git push` 作为会话结束的标准动作
- 用户手机端可通过 GitHub 查看

## 知识库结构设计

```
D:\Codex-Knowledge\
├── .codex-instructions.md   ← AI 自举入口（第一个读）
├── SYSTEM.md                ← 角色定义
├── README.md                ← 人类阅读
├── KNOWLEDGE_BUNDLE.md      ← 单文件完整包（关键！）
├── KNOWLEDGE_INDEX.md       ← 完整索引
├── QUALITY.md               ← 质量管理框架
├── FRESHNESS.md             ← 保鲜规则
├── .value-rules.md          ← 价值判断标准
├── memory/
│   └── distilled-memory.md  ← 不依赖原始记忆的蒸馏版
├── compact/                 ← 分层精简版
│   ├── L1-3files/           ← 最小集（3文件）
│   └── L2-7files/           ← 标准集（7文件）
├── projects/                ← 项目档案
├── lessons/                 ← 长期经验
├── patterns/                ← 可复用模式
├── mistakes/                ← 错误库
├── anti-patterns/           ← 反模式库
├── bad-cases/               ← 失败案例
├── decisions/               ← 设计决策
├── templates/               ← 代码/配置/清单模板
└── preferences/             ← 用户画像
```

## 使用方式

### 给其他 AI 读取
1. **最快**：直接给 `KNOWLEDGE_BUNDLE.md`（一个文件包含一切）
2. **标准**：给 `compact/L2-7files/`（7 个文件，30 分钟）
3. **最小**：给 `compact/L1-3files/`（3 个文件，15 分钟）

### 备份恢复
```bash
git clone https://github.com/Buruxianhecha/codex-knowledge.git D:\Codex-Knowledge
```

## 关键决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 单文件 bundle | ✅ 维护 KNOWLEDGE_BUNDLE.md | 任何 AI 一次读取 |
| 分层精简 | ✅ L1(3files) + L2(7files) | 适应不同 token 限制 |
| Git 备份 | ✅ GitHub | 防丢失 + 跨设备 |
| 蒸馏不复制 | ✅ 结构化条目 | 避免聊天记录污染 |

## 标签
#knowledge-management #portability #self-distillation #backup #architecture
