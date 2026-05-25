---
status: active
since: 2026-05-25
cross_refs:
  - mistakes/flask-debug-true.md (硬化清单第2项)
  - mistakes/download-no-auth.md (硬化清单第4项)
  - mistakes/dead-code-orphan.md (硬化清单第1项)
---
# 快速原型的工程债务清单

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
v1 快速跑通后，代码里留下了大量原型期痕迹。

## 经验
v1 完成后应该立即做一轮"原型债务清理"：
1. 删除死代码
2. 关掉 debug 模式
3. 统一配置管理（消灭硬编码路径）
4. 补鉴权遗漏
5. 整理依赖管理

## 行动指南
每完成一个可用的 v1，做一次"硬化"——不是加功能，是加固现有代码。

## 标签
#engineering #technical-debt #v1-hardening

