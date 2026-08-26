---
status: verified
confidence: 0.97
reuse_count: 2
last_used: 2026-08-25
verified_in:
  - cassell-open-world-simulator
  - jianlai-life-simulator
expires_after: none
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - patterns/monotonic-archive-transaction.md
  - decisions/targeted-single-file-fix-before-refactor.md
---

# 交互系统需要语义不变量

> UI 文案、按钮编号和对象 ID 都是表象；可长期验证的是状态怎样变化、什么绝不倒退、内容何时算重复。

## 三类不变量

### 状态不变量

- 同一运行内剧情进度、动作版本和可见事件序号单调增加。
- 重复动作和旧页面动作是 no-op。
- 战斗子回合不能重复推进主线。
- 完成、死亡和删除状态不能在刷新后逆转。

### 语义不变量

- 结果由具体动作和上下文决定，不由选项位置决定。
- 多意图输入可以组合生效，不使用互斥 `else-if` 抹掉第二个意图。
- 未识别输入留下上下文，但不制造无依据奖励。
- 风险只修正动作代价，不直接替代动作含义。

### 内容不变量

- 场景 ID 不同不等于内容不同。
- 去掉轮次编号、时间戳等装饰后，标题、正文和选项组合不能重复。
- 长程生成器必须在足够回合上做规范化指纹测试。

## 为什么示例测试不够

单轮演示无法发现：第 12 轮开始循环、旧标签页覆盖新档、死亡档读取后恢复、不同文案仍套同一公式。交互系统需要性质测试和长程测试，而不只是几个截图。

## 可执行断言示例

```text
after.storyCursor >= before.storyCursor
after.actionSeq == before.actionSeq + 1  # 仅有效动作
duplicate(state, same sceneId/actionSeq) == state
normalize(scene[n]) not in seen
reload(save(dead=true)).dead == true
effect("调查药铺") != fixedEffect(optionIndex=3)
```

## 适用范围

- 游戏状态机。
- 多步表单和审批流。
- 本地优先编辑器。
- 购物车、库存和任务看板。
- 带自由文本指令的 Agent/UI。
- 任何可能有重复点击、旧标签页或恢复流程的应用。

## 行动规则

1. 先用自然语言写出绝不能被破坏的性质。
2. 把状态转换和浏览器 I/O 分开。
3. 为旧提交、重复提交和并发标签页写测试。
4. 对生成内容先规范化再判重。
5. 为组合意图保留独立规则命中记录。

## 标签

#state-machine #invariant #game #semantics #property-testing #content-generation
