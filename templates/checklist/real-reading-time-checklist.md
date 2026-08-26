---
status: active
confidence: 0.98
reuse_count: 0
last_used: 2026-08-26
verified_in: [article-shelf-requirement]
expires_after: none
cross_refs:
  - projects/2026-08-20-article-shelf-engagement-system.md
  - patterns/activity-gated-time-accounting.md
---

# Checklist：真实阅读时长验收

## 状态门控

- [ ] 只有文章详情路由可计时。
- [ ] `document.visibilityState !== visible` 时暂停。
- [ ] `window` 失焦时暂停。
- [ ] 超过 idle 阈值时暂停。
- [ ] Reading Session 无效/结束时暂停。
- [ ] 任一 gating 条件恢复后可继续，而不是新开重复 Session。

## 时间正确性

- [ ] 不使用“离开时间 - 进入时间”作为最终阅读时长。
- [ ] 每个有效区间单独结算。
- [ ] 系统睡眠/页面冻结后的异常长 delta 被 clamp。
- [ ] 客户端时钟回拨不会产生负时间。
- [ ] `visibilitychange` / `pagehide` 会 flush。
- [ ] 网络重试不会重复累计同一个 chunk。

## 多实例

- [ ] 同文章两个标签页不会双算。
- [ ] 刷新不会重复提交上一段时间。
- [ ] 同账号多设备口径已明确：累加、去重或时间并集。

## 统计

- [ ] 原始 Session 与日/月汇总分层保存。
- [ ] 业务时区明确。
- [ ] 23:59 跨 00:00 正确拆分 businessDate。
- [ ] 每篇文章、每日、累计总时长能由原始记录重算。

## 签到联动

- [ ] `(userId, businessDate)` 幂等。
- [ ] 连续天数从签到账本推导。
- [ ] 补签带来源，不改写真实创建时间。
- [ ] 奖励领取具有独立幂等状态。
- [ ] 月历、累计、历史最长和全勤可由账本重算。
