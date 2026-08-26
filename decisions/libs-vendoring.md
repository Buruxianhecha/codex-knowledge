---
status: active
confidence: 0.90
reuse_count: 0
last_used: 2026-05-24
verified_in: [pdf-to-excel]
expires_after: none
cost: high
cross_refs:
  - mistakes/hardcoded-paths.md
  - lessons/v1-hardening.md
---

# 依赖管理：vendoring vs pip/venv

## 来源

- 项目：pdf-to-excel。
- 日期：2026-05-24。

## 背景

项目依赖 30+ Python 库，有两种管理方式：

- pip + venv（标准做法）。
- 把库文件直接复制到 `libs/`（vendoring）。

## 选项

| 方案 | 优点 | 缺点 |
|------|------|------|
| pip + venv | 标准、版本清晰、可复现 | 需要安装步骤 |
| libs/ vendoring | 免安装、复制即用 | 版本混乱、文件多、升级和安全修复困难 |

## 决策

v1 当时选择 vendoring，但 `requirements.txt` 只覆盖了少量依赖。

## 后果

- 环境复现困难。
- 依赖来源/版本难审计。
- 仓库体积和维护成本增加。
- 安全更新无法依赖标准包管理链完成。

## 后续方向

v2 优先迁移到标准 venv/锁定依赖方案。不要机械使用 `pip freeze` 作为唯一长期依赖策略；更重要的是明确直接依赖、版本约束和可复现安装流程。

## Cost

当前 vendoring 的维护成本高；迁移到标准依赖管理需要一次性整理与回归测试，但长期成本更低。

## 标签

#dependency-management #python #technical-debt #decision