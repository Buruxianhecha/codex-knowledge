---
status: active
confidence: 0.97
reuse_count: 0
last_used: 2026-08-26
verified_in:
  - article-shelf-requirement
expires_after: none
cross_refs:
  - lessons/engagement-time-must-measure-active-state.md
  - patterns/activity-gated-time-accounting.md
  - templates/checklist/real-reading-time-checklist.md
  - lessons/complex-prompts-are-executable-specifications.md
---

# 文章货架：签到与真实阅读时长系统设计

> 对现有 `ARTICLE SHELF / 文章货架` 的增量需求审计。这里记录的是已明确的产品规则和工程设计，不把规格说明误写成已上线功能。

## 当前证据边界

2026-08-20 明确了两类功能：每日签到，以及类似 QQ 阅读/微信读书的真实阅读时长。需求要求修改现有网站，不是重新建站。

当前知识证据能确认“规则已定义”，不能确认远端实现、数据库迁移、生产部署或真实用户数据已经通过验收。因此本项目状态是 **设计明确、实现待验证**。

## 一、签到不是一个布尔按钮

签到页期望包含：

- 今日是否签到。
- 连续签到天数。
- 月历状态。
- 累计签到次数。
- 历史最长连续天数。
- 当月签到次数。
- 奖励/领取状态。
- 补签记录。
- 全勤进度。

可靠实现应使用签到账本，而不是只保存 `checkedIn=true`：

```text
CheckInRecord
- userId
- businessDate
- source: normal | makeup | admin
- createdAt
- rewardState
- idempotencyKey
```

`(userId, businessDate)` 应具有唯一性或等效幂等保护。连续天数、月历和全勤应从账本推导；补签是一笔有来源的历史事务，不应该静默改写原始时间。

## 二、真实阅读时间的核心不变量

禁止：

```text
进入文章 -> Date.now() -> 离开文章 -> 相减
```

页面打开时间不是阅读时间。只有同时满足以下条件才允许累计：

```text
readingActive =
  isArticleRoute
  && documentVisible
  && windowFocused
  && !idle
  && validReadingSession
```

任何条件变为 false，计时立即暂停。

### 状态来源

- 路由进入/离开文章详情。
- `visibilitychange`。
- `focus` / `blur`。
- 用户滚动、点击、按键、触摸等活动信号。
- idle 超时。
- Session 创建、恢复、结束。
- `pagehide` / 页面卸载前的最后一次 flush。

## 三、累计方式

推荐按“状态转换之间的有效 delta”累计，而不是相信固定 `setInterval` 次数：

```text
active -> 记录 activeSince
active 保持 -> 定期把 now - activeSince 写入 pendingDelta，再重置 activeSince
active -> inactive -> 立即结算最后一段 delta
```

需要：

- 对异常长 delta 设置上限，防止系统休眠/冻结造成虚增。
- `visibilitychange`、`pagehide` 时立即 flush。
- 写入操作幂等，重试不能重复加时。
- 客户端展示可乐观更新，但服务端/持久层按 session chunk 去重。

## 四、Session 与多标签页

最小 Session 至少包含：

```text
readingSessionId
userId
articleId
startedAt
lastActivityAt
lastFlushedAt
accumulatedSeconds
status
```

同一用户同一文章多标签页同时前台时，不能简单把两个标签页都加总。可通过 `BroadcastChannel`、localStorage lease 或服务端活动窗口选出计时 leader；如果业务允许跨设备同时阅读，也要明确“并集时间”还是“各设备累加”的产品口径。

## 五、日统计与时区

“今天读了多少”必须绑定明确业务时区。不要隐式使用服务器 UTC 或浏览器当前 locale 切日。

建议原始事件保存 UTC 时间戳，同时统计表带 `businessDate` 和 `timezone`。跨午夜的有效区间按业务日切分。

## 六、验收重点

- 打开文章但切到后台：不累计。
- 浏览器失焦：不累计。
- 长时间不操作：暂停；恢复活动后继续。
- 从文章页跳到书架：立即停。
- 刷新后不会重复计算刷新前已提交的秒数。
- 同一文章两个标签页不会双倍累计。
- 系统睡眠数小时后不会一次性补上数小时。
- 23:59 跨 00:00 能正确分日。
- 同一天重复签到不会生成两条有效记录。
- 补签、奖励领取和连续天数都可审计。

## 七、长期结论

“真实使用时长”属于行为计量系统，不是 UI 计时器。先定义行为状态，再累计时间；先保存事实账本，再派生 streak、月历、排行和奖励。

## 标签

#article-shelf #reading-time #engagement #state-machine #checkin #analytics