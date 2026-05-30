# Codex Knowledge — 快速启动

> 这是用户的工程经验库压缩版。3 个文件覆盖全部知识。

## 用户画像

- 中文为主，Python 技术栈，Windows 系统
- 编码风格: 实用主义，短变量名可接受，快速原型 → v1 硬化迭代
- 关键偏好: 软件不装 C 盘，快捷方式图标匹配真实品牌
- 核心原则: 空值优于假数据，第二个实现时必须抽象，配置变更先审计
- Codex/OpenClaw 默认使用简体中文；敏感 Key/token 不进知识库

## 活跃项目

### pdf-to-excel (v1 完成, D:\Projects\pdf-to-excel)
Web 应用，PDF 表格 → 格式化 Excel。Flask + pdfplumber + PaddleOCR + GPT-4o + Tesseract + Supabase。

### OpenClaw Provider 迁移经验 (2026-05-30)
Provider/API 网关配置和模型映射排障。重点：Provider 名称不等于真实模型来源，模型是否存在要同时看在线列表、本地清单和 UI/默认映射。

## 阅读顺序

1. 本文件 — 快速了解全局
2. `02-core-knowledge.md` — 全部经验/模式/错误/决策
3. `03-templates.md` — 可复用代码和配置

## 何时联网验证

- OpenAI API / GPT 模型 / Provider 映射 (参数、定价、模型列表、缓存)
- Flask / Python 依赖 (版本 breaking changes)
- PaddleOCR / Tesseract (新版本)
- Supabase (免费额度、API 变更)
- 安全相关 (CVE、最佳实践)

稳定知识直接用，变动技术要验证。
