---
status: active
confidence: 0.98
reuse_count: 0
last_used: 2026-08-25
verified_in:
  - cassell-open-world-simulator
  - jianlai-life-simulator
  - fanren-human-world-simulator
  - minimal-tv-clock
expires_after: 2026-11-25
cross_refs:
  - projects/2026-08-26-two-month-learning-audit.md
  - lessons/browser-state-is-a-versioned-database.md
  - lessons/interactive-systems-need-semantic-invariants.md
  - lessons/verification-claims-must-match-test-depth.md
  - lessons/minimal-requirements-are-a-contract.md
  - patterns/monotonic-archive-transaction.md
  - decisions/targeted-single-file-fix-before-refactor.md
  - mistakes/fanren-ci-triggered-on-incomplete-snapshot.md
  - mistakes/minimal-tv-clock-requirement-drift.md
  - templates/checklist/public-repository-release-checklist.md
---

# 浏览器模拟器与静态网页发布系列

## 范围

2026-08-24 至 2026-08-25 连续整理并发布了三个浏览器游戏仓库和一个电视时钟仓库：

| 仓库 | 形态 | 当前远端证据 | 验证强度 |
|------|------|--------------|----------|
| [cassell-open-world-simulator](https://github.com/Buruxianhecha/cassell-open-world-simulator) | React/TypeScript 状态机 + Sites 部署 | HEAD `5676ac91d45c`，CI success | 深层状态机、存档、180 轮压力和渲染约束 |
| [jianlai-life-simulator](https://github.com/Buruxianhecha/jianlai-life-simulator) | 1.5 MB 单文件离线 HTML | HEAD `304ce029b17a`，CI success | Node VM 行为回归、存档、静态离线检查 |
| [fanren-human-world-simulator](https://github.com/Buruxianhecha/fanren-human-world-simulator) | 115,051 字节单文件 HTML | HEAD `e20f299caeb8`，CI success | 4 项结构/规则锚点/语法 smoke test |
| [minimal-tv-clock](https://github.com/Buruxianhecha/minimal-tv-clock) | 静态 HTML/CSS/JS | HEAD `45fc84dbf3b8`，无 CI | 仓库可读，但不符合随后确认的最终最小需求 |

同一天的多个“仓库已上传”不能统一写成同等质量。每个项目的测试范围、用户验收和需求符合度不同。

## 一、卡塞尔：把剧情游戏当状态机

### 原问题

长程游玩出现“鬼打墙”：内容表面上有轮次变化，实质标题、正文和选项重复；旧标签页或重复点击还可能让状态倒退。

### 模块边界

- `game-data.ts`：静态世界数据。
- `story-data.ts`：五卷主线节点。
- `game-engine.ts`：纯状态转换，不访问浏览器 API。
- `game-archive.ts`：存档选择、迁移和同步提交。
- `game-ui.tsx`：只展示，不直接写存档。
- `page.tsx`：浏览器事件、流程和引擎的编排层。

这种边界使状态规则可以在 Node 环境直接测试，不需要依赖完整浏览器。

### 单调进度

同一 `runId` 中至少四个字段不能倒退：

1. `storyCursor`：已解决的剧情节拍。
2. `actionSeq`：已接受的动作版本。
3. `turn`：世界回合。
4. `eventSeq`：可见事件编号。

所有界面动作携带渲染时的 `sceneId` 和 `actionSeq`。页面已经过期或动作已经提交时，重复请求严格成为 no-op。

### 同步提交事务

一次动作按固定顺序执行：读取可能更晚的存储快照、拒绝旧页面、执行纯转换、拒绝无变化/倒退结果、同步写入、再更新 React 状态。

把持久化推迟到 `useEffect` 会产生窗口：移动端挂起、立即刷新或标签页切换时，最新动作可能尚未落盘。

### 内容真实性测试

自由世界事件由基础现场、异常修饰、持续线索链、调查阶段与外部压力组合。测试先去除末尾轮次编号，再对标题、正文和选项做规范化指纹；五卷各跑 180 轮，不允许实质重复。

这比“生成了 900 个不同 ID”更强，因为 ID 不同并不能证明内容不同。

### 存档恢复

- 有效 v2 优先。
- v2 损坏时继续尝试 v1。
- 成功读取 v1 后立即迁移到 v2。
- 同一档案旧导入不能覆盖新进度。
- 显式读取另一个 `runId` 时，通过激活时间让新选择压过旧标签页。

### 验证

CI 依次执行格式、Lint、构建、状态机/存档回归、五卷长程内容压力测试和关键响应式约束。HEAD 对应工作流已完成并成功。

## 二、剑来：在不大改架构的前提下修正玩法语义

### 为什么保留单文件

可恢复基线 v4.6 已含 UI、剧情和多存档。发布前同时拆分 1.5 MB HTML 会扩大回归面，因此采用定向修复：保留单文件，把身份序章层和动作语义结算层嵌入现有状态机，并用测试保护。

这不是宣称“上帝文件更好”，而是一个发布阶段的风险决策。后续长期维护仍可拆分，但不应把重构和用户刚要求的玩法修复绑在同一次发布。

### 固定位置公式的问题

旧逻辑把第一项固定解释为本心、第二项固定掉血、第三项固定加练气/线索。结果是文案看似具体，计算却只认位置。

修复后，统一判定器读取：

- 具体动作标题和提示。
- 当前章节、事件正文和人物。
- 风险标签。
- 当前身份与已有状态。

帮助、练拳、学剑、读书、调查、交易和违法行为拥有不同规则；同一动作可多规则命中。“找宁姚学剑”同时影响宁姚关系和剑道/练气，不误改当前场景里的无关人物。

### 未识别自由行动

无法完全识别的输入不弹“无效”，也不发固定奖励。系统保存原文、消耗一个回合、留下因果标记，并允许后续剧情消费该意图。这同时满足尊重输入和“空值优于假数据”。

### 随机开场

五种身份各有至少三个独立序章。随机选择只发生在新建角色时，所选序章和主线入口写入存档；读取时保持不变，真正重开才重新抽样。

### 单文件测试方法

测试从 HTML 提取最后一个内联脚本，用 `node:vm` 执行，并提供最小 `document`、Element、ClassList、`localStorage`、计时器和可控制的 `Math.random`。这让旧式单文件应用也能验证真实函数，而不是只搜索字符串。

回归覆盖：五身份序章、同身份重开变化、动作语义、组合自由行动、未知意图、危险判断、多存档、死亡结局、旧档读取和离线约束。

### 远端状态

仓库保留设计、计划、测试壳、随机序章、语义结算、发布资料与后续修复的分阶段提交。最终 HEAD CI 成功，且最后两个提交专门修复浏览器 QA 和完成/死亡旅程在重载后的保持。

## 三、凡人：正确区分 smoke test 与行为验证

项目是可离线打开的 115,051 字节 HTML，人界篇边界清楚，包含本地存档和文本导入/导出。四个 Node 测试检查：

1. 中文 HTML 结构和标题。
2. 人界境界/寿元规则锚点存在。
3. 四向选择和本地存档关键代码存在。
4. 所有内联脚本能被 JavaScript 解析。

这些测试能证明“文件完整、关键文案存在、脚本语法可解析”，不能证明突破、寿元、战斗、死亡、导入恢复等运行行为都正确。未来报告应称“4 项 smoke tests 通过”，而不是泛化为“玩法已全面验证”。

### 中间红 CI

工作流和测试先于 `index.html` 推送，GitHub Actions 在提交 `da2bbae3de79` 上执行，因找不到根目录 `index.html` 报 `ENOENT`。完整项目随后推送，HEAD `e20f299caeb8` 的工作流成功。

教训不是隐藏第一条失败，而是：

- CI 结论必须绑定 SHA。
- 发布依赖密切的一组文件前，本地先形成可运行快照。
- 连续小提交如果逐个推送，会让中间不完整状态触发外部系统。
- 最终绿灯不能改写中间失败原因，中间失败也不能否定最终 HEAD。

## 四、极简电视时钟：需求符合度高于功能丰富度

用户最初确认的合同是：

- 单文件。
- 纯黑背景。
- 只显示 `HH:MM`。
- 字体超大、正立。
- 不显示秒、日期、全屏按钮或其他信息。

远端仓库当前仍包含 `index.html`、`style.css`、`script.js` 和图标；页面显示 `HH:MM:SS`、日期、全屏按钮、Wake Lock 和防烧屏漂移。它在一般产品设计上有合理性，但违反了排他需求。

用户在 10:52 明确要求“重来，最开始的要求你忘了吗”，10:56 再次确认最终四条要求。仓库最后 push 在 10:45，因此截至审计时，远端仓库不是最终接受版本。

这一案例验证：增加功能不是默认的质量提升。用户明确说“只”“单文件”“不加”时，额外功能本身就是缺陷。

## 五、GitHub 发布证据

### 推荐证据链

```text
本地完整测试
  -> 提交完整快照
  -> 推送默认分支
  -> 回读 HEAD 与远端文件树
  -> 比较 commit/blob
  -> 读取该 HEAD 的 CI
  -> 打开真实入口做用户路径验收
```

“Git push 成功”只证明对象被远端接受，不证明文件内容、默认分支、CI 或用户入口正确。

### 当前限额（2026-08-26 已核验）

| 层级 | 官方限制/建议 | 对本期项目的意义 |
|------|---------------|------------------|
| 浏览器上传 | 单文件 25 MiB | 1.5 MB 剑来 HTML 可上传，但大文件仍应使用 Git 流程 |
| 普通 Git 对象 | 强制 100 MiB，推荐不超过 1 MiB | 大单文件虽未超硬限制，但频繁修改会膨胀历史 |
| 单次 push | 强制 2 GiB | 大仓库首次上传需拆分 push 或治理历史 |
| 仓库总体 | 理想低于 1 GB，强烈建议低于 5 GB | 图片、构建产物和大 HTML 应持续监测 |
| GitHub Pages | 站点不超过 1 GB，软带宽 100 GB/月 | 静态站适用，但仍需构建与流量意识 |

限额来源是 GitHub 官方文档，属于时效知识，过期后必须重新验证。

## 六、跨项目结论

1. 浏览器游戏首先是状态机，其次才是文案集合。
2. 交互动作必须携带场景和版本，旧页面请求要成为 no-op。
3. 本地存档需要版本迁移、损坏回退、并发裁决和完成态保持。
4. 随机值在创建时抽样，在存档中固定。
5. “内容不同”要用去装饰后的语义指纹证明。
6. 单文件应用仍可通过 VM + 最小浏览器替身做行为测试。
7. 测试结论必须写清是 smoke、行为、压力、构建还是用户路径。
8. CI 只对具体提交有效。
9. 最小需求中的排他词是验收红线。
10. 远端回读比“命令成功”更接近交付证据，但仍不能替代真实用户路径。

## 标签

#game #state-machine #browser-storage #testing #github #ci #single-file #requirements
