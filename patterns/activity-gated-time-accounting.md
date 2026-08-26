---
status: active
confidence: 0.97
reuse_count: 1
last_used: 2026-08-26
verified_in:
  - article-shelf-requirement
expires_after: none
cross_refs:
  - projects/2026-08-20-article-shelf-engagement-system.md
  - lessons/engagement-time-must-measure-active-state.md
  - templates/checklist/real-reading-time-checklist.md
---

# Pattern：Activity-Gated Time Accounting

## 适用场景

阅读时长、学习时长、专注时长、任务工时、有效观看时长等“只有在用户真正参与时才计时”的产品。

## 状态

```ts
active =
  contextValid &&
  visibility === 'visible' &&
  hasFocus &&
  !idle &&
  sessionValid
```

## 核心状态机

```text
INACTIVE
  -- all gates true --> ACTIVE
ACTIVE
  -- any gate false --> INACTIVE + flush(delta)
ACTIVE
  -- heartbeat --> flush(delta) + continue
ANY
  -- session end --> FINALIZE
```

## 累计伪代码

```ts
let activeSince: number | null = null
let pending = 0

function reconcile(now: number) {
  const shouldBeActive = computeActive()

  if (shouldBeActive && activeSince === null) {
    activeSince = now
    return
  }

  if (!shouldBeActive && activeSince !== null) {
    pending += clamp(now - activeSince)
    activeSince = null
    flushPending()
  }
}

function heartbeat(now: number) {
  if (activeSince === null) return
  pending += clamp(now - activeSince)
  activeSince = now
  flushPending()
}
```

## 幂等持久化

每段累计使用唯一 `chunkId/sessionId/sequence`：

```text
ReadingChunk
- chunkId
- sessionId
- userId
- articleId
- from
- to
- seconds
```

服务端对 `chunkId` 去重。不要只发送“总秒数 + delta”，否则网络重试容易双加。

## 多标签页

同一逻辑实体需要 leader/lease 或服务端去重，避免两个前台标签页双计。可选机制：

- `BroadcastChannel` 协调 leader。
- localStorage lease + 过期时间。
- 服务端按重叠区间做 union。

## 防异常

- delta 设置合理上限。
- `pagehide` / `visibilitychange` 立即结算。
- system sleep 后不补算整段休眠。
- 客户端时钟回拨时拒绝负 delta。
- 统计按明确业务时区切日。

## 标签

#pattern #time-accounting #analytics #state-machine