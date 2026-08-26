---
status: verified
confidence: 0.96
reuse_count: 4
last_used: 2026-08-26
verified_in:
  - shiguang-album-redesign-prompt
  - article-shelf-reading-time-spec
  - jianlai-life-simulator
  - fanren-human-world-simulator
expires_after: none
cross_refs:
  - patterns/multi-agent-shared-schema-orchestration.md
  - projects/2026-08-20-article-shelf-engagement-system.md
  - projects/2026-08-25-browser-simulator-release-series.md
  - templates/checklist/evidence-research-checklist.md
---

# 复杂提示词是可执行规格，不是长作文

> 对开发、整改、研究、多智能体和互动世界任务，提示词的价值不在字数，而在是否把状态、边界和验收条件写成可执行合同。

## 一个成熟提示词至少包含

1. **目标**：最终用户要获得什么。
2. **现状**：这是新建、修改现有项目还是二次审校。
3. **输入事实**：已有文件、代码、视觉参考、原著设定、数据源。
4. **必须项**：不可缺失的功能/内容。
5. **禁止项**：不可主动扩展或改变的内容。
6. **状态模型**：长期任务中的时间、进度、存档、身份、权限或 Session。
7. **不变量**：绝不能回退/重复/双算/泄露的规则。
8. **输出契约**：文件、目录、格式、命名和交付位置。
9. **验证矩阵**：如何证明实现有效。
10. **失败处理**：证据不足、工具缺失、数据不完整时如何降级。
11. **变更策略**：是在原项目增量修改，还是允许重构。
12. **收口机制**：多智能体结果如何统一事实、去重和审计。

## 互动模拟器的额外要求

长程世界不能只写世界观，还要写：

- 单调时间和进度。
- 寿元/资源真实扣减。
- 事件触发与冷却。
- 随机结果创建时固化。
- 角色与世界状态持久化。
- 失败/死亡/结局不可刷新复活。
- 原著事实与程序生成内容分层。

否则文字再长，也容易出现时间倒退、鬼打墙和“换皮重复”。

## 工程整改的额外要求

需要明确“修改现有项目，不是重建”、保护已有功能/数据，并把视觉参考定义为约束来源而不是待嵌入素材。

## 多智能体的额外要求

不要只写“创建多个智能体”。应指定职责、共享 Schema、依赖关系、冲突裁决和最终审计角色。否则只是并行生成更多不可合并的文本。

## 标签

#prompt-engineering #specification #requirements #state-machine #multi-agent