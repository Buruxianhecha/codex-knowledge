---
status: verified
confidence: 0.97
reuse_count: 2
last_used: 2026-08-25
verified_in:
  - cassell-open-world-simulator
  - jianlai-life-simulator
expires_after: none
cost:
  token_cost: low
  latency: fast
  complexity: moderate
  maintenance: medium
  scalability: moderate
cross_refs:
  - lessons/browser-state-is-a-versioned-database.md
  - lessons/interactive-systems-need-semantic-invariants.md
  - projects/2026-08-25-browser-simulator-release-series.md
---

# 单调存档事务模式

## 适用场景

浏览器多标签页、本地优先应用、游戏存档、离线表单或任何可能收到重复/旧动作的状态系统。

## 核心字段

```ts
type Snapshot = {
  runId: string;
  activatedAt: number;
  storyCursor: number;
  actionSeq: number;
  turn: number;
  eventSeq: number;
};
```

同一 `runId` 按业务进度优先，再按动作版本和回合比较。不同 `runId` 只有在用户显式激活后，新的 `activatedAt` 才能覆盖旧运行。

## 提交顺序

```text
1. read persisted snapshot
2. compare persisted vs rendered snapshot
3. reject if persisted is newer
4. apply pure transition
5. reject no-op or regression
6. persist synchronously
7. update UI state
```

伪代码：

```ts
function commit(storage, rendered, transition) {
  const stored = parse(storage.getItem(KEY));
  const base = stored && compare(stored, rendered) > 0 ? stored : rendered;
  if (base !== rendered) return { status: "newer-stored", state: base };

  const next = transition(base);
  if (next === base || compare(next, base) < 0) {
    return { status: "rejected", state: base };
  }

  storage.setItem(KEY, serialize(next));
  return { status: "applied", state: next };
}
```

## 为什么同步写入

持久化延迟到 `useEffect` 会让页面更新和磁盘状态短暂分裂。移动端挂起、立即刷新或跨标签页切换时，最后一步可能丢失。

## 必测性质

- 同一 `sceneId/actionSeq` 重复提交是 no-op。
- 更晚存储快照阻止旧标签页动作。
- 业务进度深的快照不能被“回合多但主线浅”的快照覆盖。
- 新运行只有显式激活后才能压过旧运行。
- 序列化/反序列化保持比较结果。
- 损坏新版本不会阻止读取有效旧版本。

## 标签

#local-first #transaction #state-machine #concurrency #browser-storage
