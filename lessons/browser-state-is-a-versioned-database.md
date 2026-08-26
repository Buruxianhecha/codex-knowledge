---
status: verified
confidence: 0.97
reuse_count: 3
last_used: 2026-08-25
verified_in:
  - shiguang-album-test-site
  - cassell-open-world-simulator
  - jianlai-life-simulator
expires_after: none
cross_refs:
  - projects/2026-08-12-shiguang-album.md
  - projects/2026-08-25-browser-simulator-release-series.md
  - patterns/monotonic-archive-transaction.md
  - templates/checklist/browser-state-app-checklist.md
---

# 浏览器状态是一个版本化数据库

> 一旦本地状态承载用户照片、角色进度、收藏或完成态，就必须按数据库而不是缓存处理。

## 常见误区

- 把 `localStorage` 当成可以随时覆盖的全局变量。
- 新字段缺失就清空旧档。
- 只保存 UI 当前需要的部分状态。
- 读取时重新随机或重新推导历史选择。
- 以“当前标签页内存”覆盖可能更新的持久化快照。
- 账号切换后只换头像，不给业务记录加 owner。

## 最小数据模型

每条长期状态至少需要：

- `schemaVersion`：结构版本。
- `ownerId` / `runId`：归属。
- `createdAt`、`updatedAt`。
- 单调版本，如 `actionSeq` / `revision`。
- 业务完成态，如 `finished`、`deletedAt`、`openingDone`。
- 可选来源版本和迁移记录。

## 迁移规则

1. 读取新版本并验证完整性。
2. 新版本存在但损坏时，仍尝试有效旧版本。
3. 旧版本迁移应幂等，并立即保存为新版本。
4. 未知字段尽量保留，避免向后写入时丢数据。
5. 迁移失败时保留原始数据和错误，不能静默清空。

卡塞尔验证了“损坏 v2 -> 有效 v1 -> 迁移 v2”；剑来验证了未完成序章、手动存档和死亡结局在读取后保持；拾光相册验证了账户迁移不能靠清空 IndexedDB。

## 隔离与授权

本地数据按 `ownerId` 查询是多账户测试的必要条件，但它只约束应用代码。用户可以修改浏览器数据，因此它不是可信安全边界。

正式云端产品还需要：

- 服务端认证和授权。
- 对象存储/数据库行级所有权。
- 访问审计和撤销。
- 备份、恢复和配额。

## 随机性与历史

随机序章、抽卡、推荐结果等必须遵循“创建时随机、读取时确定”。保存的是抽样结果或随机种子，而不是每次加载重新执行 `Math.random()`。

## 删除与结束

回收站、永久删除、死亡、完成和封存都属于业务状态。刷新或迁移后不能因为默认值缺失而复活。测试应覆盖：

- 删除 -> 刷新 -> 仍在回收站。
- 恢复 -> 刷新 -> 正常出现。
- 死亡/完成 -> 读取 -> 不再出现可继续动作。
- 旧档迁移 -> 结束态不丢失。

## 行动规则

- 任何持久化变更都写迁移测试。
- 任何账号功能都用两个账户做读写隔离测试。
- 任何随机状态都做保存/读取一致性测试。
- 任何完成或删除状态都做刷新后测试。
- 在宣传“备份/同步”前，真实执行恢复演练。

## 标签

#browser-storage #indexeddb #localstorage #migration #data-integrity #local-first
