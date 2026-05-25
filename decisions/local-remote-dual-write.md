---
status: active
since: 2026-05-25
consequence_good: 离线可用，本地速度快
consequence_bad: 合并去重简陋，编辑/删除不同步
pattern: patterns/lightweight-supabase-client.md
---
# 本地 SQLite + 云端 Supabase 双写

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
历史记录需要跨设备同步。选择：
- 纯云端（Supabase only）
- 双写（SQLite 本地 + Supabase 云端）

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯 Supabase | 架构简单，无同步问题 | 离线不可用，每次查询走网络 |
| 双写 | 离线可用，本地速度快 | 同步逻辑复杂，可能不一致 |

## 决策
双写——本地 SQLite 为主，后台同步到 Supabase。查询时合并去重。

## 后果
离线体验好，但合并去重逻辑简陋（只按 token 去重），编辑/删除不同步。

## 标签
#architecture #sync #database

