# QUALITY.md — 知识质量管理框架

> 知识库的目标不是无限增长，而是长期保持干净、稳定、可演化、不腐烂、不失控。

---

## 一、知识条目质量标准

每条知识必须携带以下元数据：

```yaml
---
status: active | verified | best_practice | deprecated | superseded
confidence: 0.0-1.0          # 对这条知识的信心
reuse_count: 0               # 被复用次数
last_used: 2026-05-25         # 最后使用日期
verified_in: [project-a]      # 验证过此条目的项目列表
expires_after: 2026-11-25     # 预计过期时间(涉及时效性技术的条目必填)
replaced_by: file.md          # 被哪个条目替代
supersedes: file.md           # 替代了哪个旧条目
conflicts_with: file.md       # 与哪个条目冲突
---
```

## 二、过期验证时间表

| 知识类别 | 有效期 | 过期后动作 |
|----------|--------|------------|
| AI 模型 API | 3 个月 | 联网重验证 |
| Web 框架 | 6 个月 | 检查 breaking changes |
| Python 生态 | 6 个月 | 检查依赖版本 |
| 第三方服务 | 6 个月 | 检查定价/API 变更 |
| 安全实践 | 3 个月 | 检查新 CVE |
| 部署方案 | 6 个月 | 检查平台变更 |
| 算法/设计模式 | 无过期 | 仅在新方案出现时标记 superseded |
| 工程原则 | 无过期 | 仅在被明确推翻时标记 |
| 用户偏好 | 无过期 | 仅用户主动更新 |

## 三、演化关系

知识不是孤岛，必须记录演化链：

```
旧知识 ──supersedes──→ 当前知识 ──replaced_by──→ 新知识
                            │
                      conflicts_with ←→ 冲突知识
```

**规则**：
- 新旧方案共存时：两者都保留，新标记 `supersedes`，旧标记 `superseded`
- 方案互相矛盾时：标记 `conflicts_with` + 各自的适用场景
- 永远不删除旧条目——保留"为什么以前这样做"的历史

## 四、知识分层蒸馏管道

```
raw knowledge (原始经验)
    │  过滤：是否可复用？是否有演化价值？
    ▼
distilled knowledge (蒸馏知识, lessons/patterns/)
    │  验证：是否多项目验证？是否 3 月以上稳定？
    ▼
hot memory (热记忆, high confidence + high reuse)
    │  积累：是否 ≥3 项目验证 + ≥3 月无推翻？
    ▼
best practice (最佳实践, 最高级别)
```

每层有明确的晋升标准，不达标的停留在当前层。

## 五、反模式库 (anti-patterns/)

不仅记录"发生过什么错误"，更要记录"哪些设计长期一定会导致系统腐烂"：

| 反模式 | 症状 | 腐烂路径 | 首次发现 |
|--------|------|----------|----------|
| copy-paste architecture | 同一逻辑三处实现 | 改一处漏两处 → 行为不一致 | pdf-to-excel |
| hidden global state | sys.path.insert | 导入行为依赖执行顺序 | pdf-to-excel |
| hardcoded config | D:\OCR\tesseract.exe | 换环境即炸 | pdf-to-excel |
| silent exception | except: pass | 问题被隐藏，排查无门 | pdf-to-excel |
| business logic in routes | main.py 410行 | 无法测试、无法复用 | pdf-to-excel |

## 六、失败案例库 (bad-cases/)

从真实失败中提炼质量门控规则：

```
失败案例 → 根因分析 → 门控规则 → 预防措施
```

每个 bad-case 记录：输入特征、期望输出、实际输出、根因、门控规则。

## 七、热度系统

```
hotness = confidence × log(1 + reuse_count) × recency_bonus

recency_bonus:
  30天内使用 → 1.0
  90天内使用 → 0.8
  180天内使用 → 0.5
  超过180天 → 0.2 (降权但不删除)
```

高热度条目优先在回答中引用，低热度条目在检索时降权。

## 八、架构代价追踪

每个设计决策必须记录成本维度：

```yaml
cost:
  token_cost: low | medium | high     # API 调用 token 消耗
  latency: fast | moderate | slow     # 响应延迟
  complexity: simple | moderate | complex  # 代码复杂度
  maintenance: low | medium | high    # 维护负担
  scalability: limited | moderate | scalable  # 可扩展性
```

## 九、复杂度控制

以下内容**不进核心库**：
- 一次性命令/配置值
- 不可复用的项目特定细节
- 已被完全替代且无历史价值的条目
- 纯信息查询（文档可查）

**定期修剪**：每 5 个项目或每月检查 — 合并重复、降权冷门、归档无价值。

## 十、核心原则

```
系统的目标：
  不是"无限增长"
  不是"记住一切"

而是长期保持：
  ✅ 干净 — 无垃圾
  ✅ 稳定 — 经验证
  ✅ 可演化 — 有状态、有链接
  ✅ 不腐烂 — 定期保鲜
  ✅ 不失控 — 有准入、有修剪
```
