---
status: active
confidence: 0.98
reuse_count: 1
last_used: 2026-08-26
verified_in:
  - article-shelf-requirement
expires_after: none
cross_refs:
  - projects/2026-08-20-article-shelf-engagement-system.md
  - patterns/activity-gated-time-accounting.md
  - templates/checklist/real-reading-time-checklist.md
---

# 行为时长必须测量“有效状态”，不能测量墙钟时间

> 页面存在多久、程序运行多久、窗口打开多久，都不自动等于用户真正执行行为多久。

## 核心公式

先定义行为是否有效：

```text
active = contextValid && visible && focused && !idle && sessionValid
```

只有 `active=true` 的时间片才能累计。

不同业务替换 `contextValid`：

- 阅读：当前路由是文章详情。
- 学习：题目/课程处于有效学习 Session。
- 视频：播放器在播放且页面/设备满足计时口径。
- 工时：目标任务已激活且用户没有进入长期 idle。

## 为什么“开始时间到结束时间”错误

墙钟差会把以下时间全部误计入：

- 切到其他标签页。
- 浏览器失焦去做别的事。
- 打开页面后离开电脑。
- 系统睡眠。
- 路由已离开但组件未及时销毁。
- 多标签页重复计时。

## 正确模型

1. 明确定义所有 gating 条件。
2. 监听条件变化。
3. active 开始时记录起点。
4. active 结束时只结算本段 delta。
5. 定期 flush，写入幂等 chunk。
6. 对冻结/睡眠后的异常长 delta 做 clamp。
7. 原始 Session 与派生统计分层保存。

## 行动规则

任何“真实时长”需求先问：**什么条件下这一秒才算有效？** 如果答案只是“页面还开着”，设计还没有完成。

## 标签

#analytics #time-accounting #engagement #state-machine #idle