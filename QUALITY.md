# QUALITY.md — 知识质量管理框架 v4

> 目标不是无限增长，而是长期保持干净、稳定、可演化、不腐烂、不失控，并且维护过程本身不得破坏事实源。

## 一、分层与事实源

```text
Source knowledge
  projects / lessons / patterns / mistakes / decisions
  anti-patterns / preferences / SYSTEM / FRESHNESS / QUALITY
        |
        v
Derived knowledge
  KNOWLEDGE_INDEX / memory / KNOWLEDGE_BUNDLE
        |
        v
Compact distribution
  compact/L1 / compact/L2
```

事实源负责保留可复用细节；派生层负责索引、蒸馏和单文件接管；Compact 负责 token/文件数受限场景。

**维护时不得把 Compact 的压缩目标反向施加到事实源。**

## 二、新条目统一 Schema

新建 `projects / lessons / patterns / mistakes / decisions / anti-patterns` 时使用：

```yaml
---
status: active | verified | best_practice | deprecated | superseded | archived
confidence: 0.0-1.0
reuse_count: 0
last_used: 2026-08-26
verified_in: [project-a]
expires_after: none | YYYY-MM-DD
cross_refs:
  - lessons/example.md
replaced_by: patterns/new.md     # 需要时
supersedes: patterns/old.md      # 需要时
conflicts_with: lessons/x.md     # 需要时
---
```

新条目不得继续制造元数据债务。

## 三、Legacy Metadata Registry

旧文件若早于统一 Schema，可暂时在 `metadata/legacy-overrides.json` 补缺失字段。

规则：

- 文件内 frontmatter 永远优先。
- registry 只允许补缺失字段，禁止覆盖已有值。
- 每个 override 必须写 reason。
- 旧文件下次被实质修改时，优先内联元数据并删除对应 override。
- CI 单独打印 override 数量，因此 `warnings=0` 不等于“历史已全部重写”。

## 四、生命周期

```text
raw -> active -> verified -> best_practice
          |          |
          +-> deprecated / superseded / archived
```

### Lesson

至少两个独立证据上下文后再评估 `verified`。

### Pattern

至少两个独立项目成功应用后再评估 `verified`。

### Best practice

至少满足：

- 3 个独立成功应用。
- 稳定至少 3 个月。
- 适用边界清楚。
- 有可执行验收方法。

不达标就停留在当前层，不为了“库看起来成熟”提前晋升。

## 五、过期与保鲜

| 类别 | 建议复核周期 | 过期后 |
|------|--------------|--------|
| AI/API/模型/价格/限额 | 约 3 个月或更短 | 联网重验 |
| OAuth/认证/平台插件 | 约 3 个月 | 官方文档 + 运行时重验 |
| Web/Python/第三方 SDK | 约 6 个月 | 查 breaking changes |
| 安全实践 | 约 3 个月 | 查官方安全更新/CVE |
| 部署/托管规则 | 约 3-6 个月 | 官方文档重验 |
| 设计模式/工程原则 | 无固定过期 | 出现反证时演化 |
| 用户偏好 | 无固定过期 | 以用户最新明确指令为准 |

`expires_after` 表示“需要重新验证”，不是到期自动删除。

## 六、演化关系

旧方案不因新方案出现而抹掉：

```text
旧知识 --superseded by--> 当前知识
  ^                         |
  +----- historical why ---+
```

- 有替代方案：旧条目标 `superseded` + `replaced_by`。
- 有冲突：双方记录 `conflicts_with` 和各自适用范围。
- 事实纠错：保留“旧结论为什么错”的审计价值。

## 七、证据门槛

| 声明 | 最低证据 |
|------|----------|
| 文件存在 | 产物可回读 |
| 已上传 | 远端 commit/Blob/文件回读 |
| CI 通过 | 当前目标 SHA 的成功 run |
| 功能可用 | 核心用户路径通过 |
| 稳定可用 | 刷新/重启/并发/恢复后仍成立 |
| 正式上线 | 安全、恢复、权限、容量、真机、运维边界通过 |
| 研究事实 | 可定位来源 + 冲突检查 |
| 主导/因果/法律认定 | 直接支持该强度措辞的证据 |

命令成功和文档正确都只是局部证据。

## 八、失败案例与反模式

错误记录“这一次为什么错”；anti-pattern 记录“长期这样做会怎样腐烂”；bad-case 把真实输入失败转化为门控规则。

```text
失败 -> 根因 -> 规则 -> Checklist/Test -> 下次自动预防
```

## 九、热度与复用

可使用概念性热度：

```text
hotness ~ confidence × log(1 + reuse_count) × recency
```

热度只影响检索优先级，不改变事实真伪。低热度条目降权而不是删除。

## 十、Decision 成本

新 Decision 应显式记录成本。推荐维度：

```yaml
cost:
  token_cost: low | medium | high
  latency: low | medium | high
  complexity: low | medium | high
  maintenance: low | medium | high
  scalability: limited | moderate | scalable
```

历史 Decision 可暂时在 registry 的 `decision_costs` 记录定性成本，下一次实质编辑时内联。

## 十一、事实源防破坏

任何批量维护都要遵循 `templates/checklist/knowledge-maintenance-diff-checklist.md`。

CI 额外运行 `scripts/check_source_regression.py`：

- 比较 `HEAD^ -> HEAD`。
- 保护 Source knowledge 与 templates/preferences。
- 20+ 行文件若一次保留不足 60%，默认失败。
- 删除受保护源文件默认失败。
- 真正经过人工审查的压缩，提交信息可带 `[allow-source-compaction]` 明确放行。

该 guard 不判断语义是否正确，只负责发现“可能把知识删没了”的高风险变化；仍需人工 diff。

## 十二、隐私

公开库禁止保存：

- API key/token/cookie/login state/refresh token。
- 具体 Windows/macOS/Linux 用户主目录名。
- 无必要真实姓名、账号私密信息。

路径用 `%USERPROFILE%`、`$HOME`、`<WORKSPACE>` 等表达；即使示例是假用户名，也尽量不用 `C:\Users\name` 形式，避免误入扫描器和复制到真实配置。

## 十三、复杂度控制

以下内容一般不进核心层：

- 一次性命令或临时配置值。
- 可随时从官方文档获取、没有项目经验增量的基础事实。
- 无复用价值的单点截图。
- 没有证据链的推测。

但“未完成/失败项目”如果产生高复用经验，可以进入核心库，状态必须真实。

## 十四、自动审计

`scripts/validate_knowledge.py` 检查：

- README/Index/Bundle 与真实文件树计数。
- 元数据与生命周期。
- registry 冲突。
- cross_refs / Markdown 链接。
- Compact 源版本。
- 用户主目录与常见 secret 模式。

`errors=0` 是合并硬门槛；warning 表示仍有未登记债务。`check_source_regression.py` 再补“结构正确但知识被删”的盲区。

## 十五、核心目标

```text
不是记住一切
不是无限增长
不是为了绿 CI 改规则

而是：
  干净      — 少垃圾、无私密凭据
  稳定      — 证据匹配声明
  可演化    — 有状态和替代链
  可追溯    — 能回到来源/项目/commit
  不腐烂    — 时效知识会复核
  不失控    — 有准入、成本和自动审计
  不丢知识  — 维护不能破坏事实源
```
