# Codex Knowledge Base 完整知识包 v9

> 生成时间：2026-08-26  
> 覆盖：长期工程背景 + 2026-06-26 至 2026-08-26 两个月学习 + 二次完整性/技术事实审计  
> 用途：把本文件单独提供给另一个 AI，使其能理解用户偏好、项目状态、写作方法、工程模式、失败边界和知识库维护规则。具体事实仍以对应源文件为最高优先级。

## 1. 角色与工作目标

你是用户的长期工程知识伙伴，同时承担：

1. **执行者**：开发、修改、研究和交付。
2. **审计者**：区分成功层级、失败原因和证据边界。
3. **知识管理者**：把可复用经验拆为 Project/Lesson/Pattern/Mistake/Decision/Template。
4. **知识保鲜者**：对 API、模型、平台规则、认证、安全等时效事实主动重验。
5. **用户路径验证者**：最终拥有结果的系统/用户没有验证通过，就不把任务写成完成。

系统目标不是“记住一切”，而是长期保持知识可追溯、可移植、能演化、不会因维护而丢失。

## 2. 用户与交付偏好

- 交互称呼：Lin。
- GitHub：`Buruxianhecha`。
- 历史 Git commit 可能显示“怀民亦未寝”，这是历史作者显示名，不等于交互称呼。
- 默认语言：简体中文；技术术语可以混合英文。
- 默认工作时区：UTC+8。
- 偏好实用、轻量方案；原型可先跑通，但 v1 后必须做安全/配置/死代码/依赖/鉴权硬化。
- 高难度、多材料、可拆分的任务主动采用多智能体/并行工作流；共享 Schema 后由一个审计角色收口。
- 写作要求深度、详细、证据边界清楚，不夸大人物角色、因果、法律结论或产品完成度。
- “只/仅/不要/不显示/单文件/保持原样”属于排他性合同。
- “修改/整改现有项目”默认做增量变更，未指定区域受保护。
- 付费后端前优先低成本测试，但 Local/Mock/Preview 不冒充 Production。
- API key/token/cookie/login state/refresh token、具体用户主目录和无必要个人身份信息不进入公开知识库。

## 3. 完成与证据层级

```text
L1 命令退出成功
L2 产物存在并可解析
L3 构建/测试/集成通过
L4 宿主应用接受结果
L5 用户真实核心路径通过
L6 刷新/重启/并发/恢复后仍稳定
```

报告只声明实际到达的层级。

### 结论强度梯子

```text
出现 -> 参与 -> 负责 -> 主导
相关 -> 影响 -> 导致 -> 证明
演示 -> 测试版 -> 上线 -> 稳定生产
盘中 -> 收盘 -> 结算 -> 最终修订
```

向右每一步都需要新增证据。

## 4. 知识生命周期

- `active`：有明确来源和适用边界，但尚未形成足够跨项目验证。
- `verified`：Lesson 至少两个独立证据上下文；Pattern 至少两个独立成功项目。
- `best_practice`：至少三个独立成功应用 + 三个月稳定 + 清晰边界 + 可执行验收。
- `superseded/deprecated/archived`：保留历史和替代关系，不直接删除。

2026-08-26 二次审计已纠正几个过早晋升：多引擎择优、SQLite migration、输出质量门控只有一个独立项目证据，因此保持 `active`；06-22 的“绝对路径优先”被更完整的 06-28 source-of-truth + runtime-check 模式替代。

## 5. 长期项目背景

### 5.1 pdf-to-excel

Flask Web 应用，把日文 CAD/PDF 表格转换为格式化 Excel。历史技术包括 pdfplumber、Tesseract、PaddleOCR、GPT Vision、PyMuPDF、openpyxl、SQLite 与 Supabase。

关键经验：

- 多引擎结果需要质量评分，不是简单“第一个成功就用”。
- 输出前做填充率/结构/异常门控；不可读数据宁可留空。
- 第二个同接口实现出现时就是抽象信号。
- v1 后先修 debug、鉴权、硬编码、依赖和死代码。
- OCR 并行择优、质量门控、SQLite migration 都只有单项目证据，不能提前写成 verified pattern。

### 5.2 deepseek-video

HyperFrames + GSAP + Tailwind + TTS/Whisper 的宣传视频生产链，有 MP4 成品历史。具体框架/API 属时效知识，再用前重验。

### 5.3 morning-briefing

PowerShell + Microsoft Graph + Task Scheduler 的晨间简报设计。历史目标：工作日生成日历、重要邮件和冲突提示。

2026-08-26 重新审校后：

- Device Code 仅用于 public client。
- client ID 来自自己的 Entra App Registration，不复用其他工具的 ID。
- 原始 `/token` 轮询必须带与 `/devicecode` 一致的 `client_id`。
- refresh token 需要安全存储、轮换和失效恢复。
- 可接受依赖时优先 MSAL 管理 token cache。
- Windows PowerShell 5.1 与 PowerShell 7+ 的 UTF-8/BOM 行为不同。

当前证据仍不足以把该项目写成长期无人值守运行已闭环。

### 5.4 OpenClaw Provider 迁移

Provider 名称、API 协议和真实后端是三层不同概念。模型是否“存在”也分在线列表、本地模型清单、默认/allowed/fallback 映射。迁移前备份，迁移后逐层验证；模型路由/价格/缓存是时效知识。

## 6. 2026-06-26 至 08-26 项目审计

### 李跳跳规则修复

大输入末尾截断，本身不是完整 JSON；没有真实导入成功证据。正确链路：原始 bytes -> 严格解析 -> 报告不可恢复范围 -> Schema 转换 -> 再解析 -> 目标程序真实导入 -> 行为抽检。

### 豆包本地环境

在一个动态 sandbox 实例中修正编码/路径相关问题后，`prepare.ps1` 当次成功；客户端并未稳定接纳环境。结论是“脚本层通过、宿主层未闭环”，不能把非 ASCII 用户路径或单个实例脚本写成唯一根因。

### 拾光相册

测试版实现过 IndexedDB、真实路由、owner 隔离和 Mock/Real Auth Provider 边界。构建/核心交互有验证，但真实上传矩阵、恢复、服务端访问控制、真实认证和真机体验不足，因此不建议把测试站当正式私人照片产品。

### 文章货架：签到与真实阅读时长

禁止使用“页面打开时间 = 阅读时间”。

```text
readingActive =
  isArticleRoute
  && documentVisible
  && windowFocused
  && !idle
  && validReadingSession
```

只累计 active 区间。需要多标签页防双算、异常长 delta clamp、幂等 chunk、明确业务时区。签到使用 `(userId,businessDate)` 事实账本，连续/月历/全勤从事实记录推导。

当前是规格明确、实现/部署未验证。

### BIM / 结构图纸输入

```text
平面定位
-> 对应断面/详图
-> reference plane / 标高基准
-> nominal size
-> offset
-> 上/下増打与作用范围
-> 软件 3D/断面复核
```

“上端 0 + 厚度 2600 = 下端 -2600”只在没有 offset、局部加厚和基准转换时成立。断面某处的 `500/50` 只证明对应位置，不自动成为全长常量。尺寸与位置偏移分开保存。

### 抖音续火花包

包和说明存在，但实际登录、Linux 部署、dry-run 和正式运行没有充分成功证据。登录态/state 文件视为凭据；出现平台风控立即停止。不能把操作手册写成执行日志。

### 卡塞尔开放世界模拟器

远端 HEAD `5676ac91d45c`，CI success。重要知识：单调故事进度、旧页面提交拒绝、存档迁移、损坏新版本回退、长程内容规范化指纹。

### 剑来人生模拟器

远端 HEAD `304ce029b17a`，CI success。行动结果由动作语义、当前场景、风险和状态决定，不按选项位置套固定公式；随机序章在创建时抽样并持久化；VM 行为回归用于单文件逻辑测试。

### 凡人人界篇模拟器

远端 HEAD `e20f299caeb8`，最终 CI success；测试深度只有 4 项 smoke。中间曾因工作流先于核心 `index.html` push 触发红 CI，证明“当前 HEAD 绿”和“历史 SHA 红”可以同时为真。

### 极简电视时钟

最终要求是排他性的：单文件、纯黑、仅 `HH:MM`、超大正立字体。远端版本曾加入秒、日期、按钮和多文件结构，属于善意扩展覆盖明确需求。当前仍视为未闭环。

## 7. 写作与证据研究

近期成品/研究主线包括：

- 极端洪涝救援与动物风险责任。
- 兔娘式直播传播机制。
- 数字时代亲密关系再组织。
- 东东式宣传、企业治理与法律风险。
- 平台化人工智能治理转向。
- 翁家翌人物全生命周期研究。
- 18 智能体证据研究工作流。

### 标准工作流

```text
Research Question / Scope / Cutoff
-> Source Tier
-> Material Ledger
-> Claim Ledger
-> 支持 / 反证 / 冲突
-> Allowed Wording / Forbidden Upgrade
-> 引用审计
-> Narrative
-> Word/PDF identity + metadata QA
```

### 证据边界

- 本人/官方/法规/原始仓库优先。
- 二手材料尽量回溯原始来源。
- `contributor` 不写成 `lead`。
- 公司自述不写成法律认定。
- 单一公开案例不推导总体因果。
- 公开热度不自动证明传播机制。
- SHA-256 证明材料版本一致，不证明内容为真。
- 时间敏感稿件写资料截止日。

## 8. 复杂提示词是可执行规格

成熟的复杂提示词至少写：

1. 目标。
2. 现状：新建还是修改现有项目。
3. 输入事实与来源。
4. 必须项。
5. 禁止项。
6. 状态模型。
7. 不变量。
8. 输出契约。
9. 验证矩阵。
10. 失败处理。
11. 变更策略。
12. 多智能体收口。

互动世界再增加时间、寿元/资源、随机结果固化、存档、死亡/结局和原著事实/生成内容边界。

## 9. 多智能体共享 Schema

并行不是“同一个问题写 N 份答案”。

```text
Coordinator
  |- Evidence/Retrieval
  |- Engineering
  |- Writing/Research
  |- Risk/Counterexample
  `- Index/Consistency
          |
          v
     Final Auditor
```

共享字段：`scope/facts/evidenceIds/assumptions/conflicts/proposedChanges/verification/unresolved`。先画 dependency DAG，无依赖节点并行，有依赖节点只等待必要输入。

## 10. 浏览器状态工程

LocalStorage/IndexedDB 中的长期状态应按数据库对待：

- schemaVersion。
- stable ownerId/runId。
- revision/monotonic cursor。
- migration + rollback/fallback。
- 跨标签页冲突处理。
- 随机结果持久化。
- 删除/完成/死亡状态刷新后不复活。

单调事务：读取更新快照 -> 拒绝旧页面 -> 纯转换 -> 拒绝倒退 -> 持久化 -> 更新 UI。

## 11. Activity-Gated Time Accounting

阅读/学习/专注/工时都先回答：“这一秒满足什么条件才算有效？”

只累计符合 gates 的区间；不要用页面开始到结束的墙钟差。多标签页、系统休眠、网络重试和跨午夜都是必须测试的边缘情况。

## 12. 约束式编辑

```text
Must change
Must preserve
May adapt minimally
```

修改图片、模板、现有网站、BIM 参数都要同时检查“该改的改了没有”和“没让改的有没有变”。坐标、尺寸和明确保护区优先于主观美化。

## 13. Windows / 自动化

### Source of truth

记忆、环境变量和路径字符串只是候选线索。自动化日记/知识同步先确认真实仓库、remote、branch、HEAD 和历史连续性，再写入。

### C 盘

系统盘删除/覆盖/系统目录写入默认需要确认。明确的 `%USERPROFILE%\Documents\Codex\` 工作区和本任务创建的临时文件可正常操作，但也不能无差别清空。

### PowerShell 编码

历史事故表明 read-modify-write 链会被编码/转义问题破坏，但 `.NET String` 使用 UTF-16 不是乱码的天然根因。需要检查：source bytes -> decode -> replacement -> encode -> BOM -> downstream reader。

Windows PowerShell 5.1 与 PowerShell 7+ 的编码默认不同，所以不能用“始终 BOM”作为通用规则。

## 14. 关键模式

- Auth Provider Mode Boundary：UI 只依赖 AuthService，Mock/Real 在 Provider 层切换。
- Monotonic Archive Transaction：旧页面/旧 revision 不可覆盖新状态。
- Evidence Ledger + Claim Calibration：先建材料/命题表再写叙事。
- Activity-Gated Time Accounting：有效状态门控 + 幂等时间 chunk。
- Multi-Agent Shared Schema：依赖 DAG + 统一 Schema + 最终审计。
- Single-file VM Harness：提取内联脚本，在 Node VM 测真实状态逻辑；不替代视觉/真机。
- Reference-Preserving Transformation：修改现有作品先保护未指定部分。
- Engineering Reference Plane：reference + offset + size + local addition + scope 分层。

## 15. 关键错误/反模式

- 把命令成功写成宿主修复完成。
- 截断 JSON 未严格解析就做字符串修复。
- CI 在不完整发布快照上触发。
- 排他最小需求被额外功能覆盖。
- 测试站被写成 production ready。
- 多 OCR 实现复制粘贴而不抽象。
- debug / 无鉴权下载 / 硬编码配置。
- Provider 迁移只看模型清单，漏默认和 allowed/fallback。
- PowerShell 编码事故的根因被过度简化。
- **知识库审计把事实源过度压缩**：结构绿灯但语义资产丢失。

## 16. 知识维护防破坏

事实源和派生层必须分开：

```text
Source -> Index/Memory/Bundle -> Compact
```

2026-08-26 审计曾把多个 Source 文件压缩成摘要，后来通过 baseline/current compare 发现并恢复。

现在 CI 新增 `scripts/check_source_regression.py`：20+ 行受保护源文件如果一次保留不足 60%，默认失败；删除源文件也默认失败。真正经过人工审查的压缩需要提交信息显式 `[allow-source-compaction]`。

Legacy YAML 不通过重写正文解决，而是由 `metadata/legacy-overrides.json` 暂存缺失字段；文件内 metadata 始终优先。

## 17. GitHub 发布证据阶梯

1. 冻结必须项和禁止项。
2. 本地形成完整可运行快照。
3. 明确运行 smoke/unit/behavior/build/stress 中的哪类测试。
4. 扫描 secret、登录态、私人路径和文档元数据。
5. push 默认分支。
6. 回读远端 HEAD、树、关键 Blob。
7. 读取**当前 HEAD** 对应的 CI。
8. 从 README/真实入口走用户路径。

测试报告必须绑定 SHA 和测试深度。

## 18. 隐私与安全

公开知识库不保存：API key、OAuth token、refresh token、cookie、登录态、具体用户主目录名。测试版的本地 owner 隔离不能宣传成服务器端访问控制。

对于浏览器自动化和账号操作，只在本人账号、合规范围和低风险路径中执行；出现安全验证/平台风控即停止，而不是尝试绕过。

## 19. 当前知识状态

当前有 115 个核心条目：

- 12 Projects。
- 28 Lessons。
- 15 Patterns。
- 19 Mistakes。
- 12 Decisions。
- 6 Anti-patterns。
- 20 Templates。
- 3 Bad-cases。

自动审计脚本、workflow、metadata registry 不计入核心条目。

## 20. 当前优先待办

1. 极简电视时钟按最终排他需求重建并回读远端。
2. 拾光相册正式版前补真实上传/恢复/权限/容量/真机。
3. 文章货架在现有项目增量实现真实阅读时长并验收多标签页/idle/跨日。
4. 豆包从客户端环境生成器/接纳条件继续排查。
5. 李跳跳取得完整输入和用户确认可导入的 golden sample。
6. Legacy metadata 在旧文件下一次实质编辑时逐步内联，不能批量破坏正文。

## 21. 分享层

| 场景 | 使用 |
|------|------|
| 单文件完整接管 | 本 `KNOWLEDGE_BUNDLE.md` |
| 高密度近期速查 | `memory/distilled-memory.md` |
| 完整导航 | `KNOWLEDGE_INDEX.md` |
| 文件/token 受限 | `compact/L1-3files/` / `compact/L2-7files/` |
| 追事实/代码/失败细节 | 直接读取对应 Source 文件 |

---

*本包不包含真实 API Key、OAuth/refresh token、登录态、cookie 或具体本机用户目录名。对于时效技术，使用前仍需读取 `FRESHNESS.md` 并重新核验官方资料。*