# Codex Knowledge Base 完整知识包 v8

> 生成时间：2026-08-26
> 覆盖：长期工程背景 + 2026-06-26 至 2026-08-26 两个月审计 + 知识库完整性二次审计

## 用户与交付偏好

交互称呼 Lin；GitHub `Buruxianhecha`。历史 Git 提交可能显示“怀民亦未寝”，只用于解释提交作者名。默认简体中文、UTC+8。

未验证 = 未完成；用户实际优先；结论不超过证据；排他需求是合同；修改现有项目保护未指定内容；复杂任务适合时主动多智能体并行并共享 Schema；Mock/本地/preview 不冒充 production；凭据和具体本机用户名路径不进公开知识库。

## 完成层级

```text
L1 命令成功
L2 产物存在/可解析
L3 构建/测试/集成通过
L4 宿主应用接纳
L5 用户真实路径通过
L6 刷新/重启/并发/恢复后仍稳定
```

结论不得高于验证层级。

## 生命周期

Lesson 至少两个独立证据上下文后评估 verified；Pattern 至少两个独立成功项目；Best practice 至少三个独立成功应用、稳定三个月且有可执行验收。被替代知识保留 `superseded/replaced_by`。

2026-08-26 二次审计纠正：多引擎择优、SQLite migration、输出质量门控从 verified 回到 active；06-22 绝对路径模式标记 superseded，由 06-28 “绝对路径 + 运行时检查”替代；用户视角验收原则补齐多项目证据后继续 best_practice。

## 近期项目

豆包：脚本层修复成功、客户端未稳定接纳。拾光相册：测试版可用但正式上传/恢复/授权/真机不足。文章货架：阅读只在 `articleRoute && visible && focused && !idle && validSession` 时累计，签到用幂等事实账本；实现/部署未验证。BIM：先平面/断面 -> reference plane -> 名义尺寸 -> offset -> 局部増打范围 -> 3D/断面复核。抖音续火花：包存在，实际部署/运行未验证。浏览器模拟器：卡塞尔/剑来/凡人当前 HEAD CI 有成功证据；极简电视时钟最终需求未闭环。

## 写作与研究

来源分层 -> Material Ledger -> Claim Ledger -> 冲突裁决 -> 引用审计 -> 写作 -> 文档署名/元数据检查。哈希证明版本一致，不证明事实真实。多智能体按证据职责拆分，共用账本，不把 Agent 摘要当原始来源。

大型提示词是可执行规格：目标、现状、输入事实、必须项、禁止项、状态、不变量、输出契约、验证矩阵、失败处理、变更策略和统一收口。

## 可复用模式

Auth Provider 边界；单调存档事务；Activity-Gated Time Accounting；多智能体共享 Schema；Must change/Must preserve/May adapt 约束式编辑；工程图纸 `referencePlane + offset + nominalSize + localAddition + scope` 分层。

## GitHub 与自动知识审计

发布按“完整快照 -> 测试 -> secret/隐私扫描 -> push -> 回读 HEAD/Blob -> 当前 SHA CI -> 真实入口”执行。

知识库使用 `scripts/validate_knowledge.py` + GitHub Actions 检查统计、生命周期、断链、Compact 源版本、隐私路径和常见凭据。历史 metadata/cost debt 作为 warning，不降低规则换绿灯。

## 当前待办

极简电视时钟重建；拾光正式版验证；文章货架真实阅读时长实现与多标签页验收；豆包客户端接纳条件调查；李跳跳完整输入/golden sample；逐批补 legacy YAML/cost warnings。

## 知识状态

当前有 112 个核心条目：12 项目、27 经验、15 模式、18 错误、12 决策、6 反模式、19 模板、3 失败案例。维护脚本和 CI 不计入核心条目数。

---

*本包不包含真实 API Key、OAuth token、登录态、cookie 或具体本机用户目录名。*