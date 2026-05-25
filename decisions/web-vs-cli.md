---
status: active (v2 计划加 CLI)
since: 2026-05-25
consequence_good: 用户友好，有账号系统
consequence_bad: 部署复杂，不能批量处理
---
# Web 应用 vs CLI 工具

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
goals.md 中写着"命令行程序 或 网页端上传工具"，最终选择了 Web。

## 决策
选择了 Flask Web 应用 + 浏览器界面。

## 后果
- 优点：用户友好，有账号系统和历史记录
- 缺点：部署复杂，需要服务器，不能批量处理本地文件
- 建议：v2 加一个 CLI 模式（`python -m app.cli input.pdf -o output.xlsx`）

## 标签
#product #cli-vs-web

