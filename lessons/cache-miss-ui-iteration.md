---
status: active
confidence: 0.95
reuse_count: 1
last_used: 2026-05-27
cross_refs:
  - projects/2026-05-26-morning-briefing.md
---

# 迭代 UI 开发中的上下文膨胀与缓存失效

## 一句话

长对话中反复小改 UI 会导致上下文暴涨、缓存全部失效，单次请求消耗数倍额度。

## 真实案例

2026-05-26 构建每周回顾仪表板 (019e64c2-cfa7)：
- 多轮迭代：卡片互动、时间线、配色、文字溢出
- 每次 Agent 重新读取整个文件（HTML/CSS/JS 混在单文件）
- 上下文膨胀到 **239 万 token**，超出模型上限 104 万
- 对话被多次 compaction 仍然无法挽回
- **结果**：大量额度被消耗，且最终仍有关键 bug 未修复

## 为什么会发生

```
Turn 1: 读取 500 行 HTML 文件 → 上下文 5K token
Turn 2: 修改 3 行 CSS → 上下文 10K token（含历史）
Turn 3: 修改 2 个事件处理 → 上下文 15K token
...
Turn 20: 修改 1 个颜色 → 上下文 200K+ token，缓存全部失效
```

每次 Agent 都需要完整的文件内容 + 完整对话历史才能理解上下文，小改动并不能减少 token 消耗。

## 预防策略

### 对话层面
1. **单文件 → 多文件拆分**：HTML/CSS/JS 分文件，Agent 只读需修改的文件
2. **超过 10 轮迭代时主动提醒**：上下文可能已膨胀，建议开新对话
3. **批量修改**：积累 3-5 个小改动一次性提出，而非一个一个来
4. **用 Git diff 而非全文重读**：告诉 Agent "只改了第 42 行的颜色"

### Agent 行为准则
1. 迭代 UI 超过 5 轮时，主动提示用户当前上下文规模
2. 如果文件 >300 行，只用 `apply_patch` 做精确修改，不要重新读取整个文件
3. 发现 compaction 事件时，立即告知用户"建议开新对话继续"

## 关联

- preferences/c-drive-safety-rule.md (另一条用户硬规则)
- anti-patterns/god-file.md (单文件过大是缓存失效的放大器)

## 标签
#cache #token-efficiency #ui-iteration #context-management #cost
