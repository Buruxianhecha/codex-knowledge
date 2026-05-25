---
status: active (v2 将改为 pip)
since: 2026-05-25
consequence: 环境无法复现
related: mistakes/hardcoded-paths.md (同为可移植性问题)
---
# 依赖管理：vendoring vs pip/venv

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
项目依赖 30+ Python 库，有两种管理方式：
- pip + venv（标准做法）
- 把库文件直接复制到 libs/（vendoring）

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| pip + venv | 标准做法，版本清晰，可复现 | 需要 pip install 步骤 |
| libs/ vendoring | 免安装，复制即用 | 版本混乱，800+ 文件，无法复现 |

## 决策
选择了 vendoring（所有依赖放在 libs/），但 requirements.txt 只写了 5 个库。

## 后果
环境无法复现。建议 v2 切换为标准 venv 方案，`pip freeze > requirements.txt`。

## 标签
#dependency-management #python #technical-debt

