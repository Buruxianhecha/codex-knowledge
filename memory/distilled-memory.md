# Distilled Memory — 可移植版 (v3)

> 从 Codex 记忆系统蒸馏出的关键信息，不依赖原始记忆格式。

## 用户速查

| 属性 | 值 |
|------|-----|
| 偏好名称 | 怀民亦未寝 |
| GitHub | Buruxianhecha |
| 知识库仓库 | Buruxianhecha/codex-knowledge |
| 主力 AI | DeepSeek API |
| 主力语言 | Python |
| 时区 | UTC+8 |

## 核心交付原则

| 原则 | 内容 |
|------|------|
| 未验证 = 未完成 | 代码生成成功 ≠ 任务完成，用户视角验证通过才是 |
| 用户优先 | 理论与实际冲突时，优先相信用户实际体验 |
| 优先复现 | 用户反馈问题 → 先复现，再分析代码 |
| 不假设用户错误 | 排查顺序：环境 → 兼容性 → 缓存 → 编码 → 路径 → 运行时 |
| 知识自蒸馏 | 知识库必须自包含、可移植，换 AI 也能自举 |

## 活跃项目

### pdf-to-excel (v1 已完成)
- **路径**: D:\Projects\pdf-to-excel
- **描述**: Web 应用，PDF 表格→格式化 Excel，支持日语 CAD 图纸
- **技术栈**: Flask + pdfplumber + PaddleOCR + GPT-4o + Tesseract + Supabase
- **状态**: v1 可用，存在安全/工程问题待 v2 修复
- **详细**: 见 `projects/2026-05-24-pdf-to-excel.md`

### deepseek-video (v1 已完成)
- **路径**: D:\Projects\deepseek-video
- **描述**: DeepSeek API 平台宣传视频，HyperFrames 自动化生成
- **技术栈**: HyperFrames + GSAP + Tailwind v4 + Kokoro TTS + Whisper
- **状态**: v1 完成，有 MP4 渲染输出
- **详细**: 见 `projects/2026-05-25-deepseek-video.md`


### morning-briefing (v1 已完成)
- **路径**: %USERPROFILE%\Documents\Codex\briefings\
- **描述**: 工作日晨间简报自动化，从 Outlook (Microsoft Graph) 拉取日历+邮件生成 Markdown 简报
- **技术栈**: PowerShell + Microsoft Graph REST API + Windows Task Scheduler
- **状态**: v1 完成，等待用户完成首次 Graph 授权
- **详细**: 见 projects/2026-05-26-morning-briefing.md

## 长期目标

### G1: PDF → Excel 完整转化工具
- **目标**: PDF 表格精确转为可编辑 Excel，保留格式
- **形态**: CLI 或 Web
- **状态**: v1 完成 (D:\Projects\pdf-to-excel)
- **创建**: 2026-05-24

## 经验速查

| 经验 | 一句话 |
|------|--------|
| 多引擎并行择优 | 多个方案时全跑一遍，按可量化评分选最优 |
| 质量门控 | AI 输出必须过质量检查，宁可少输出不错输出 |
| 空值优于假数据 | 不可读 → 留空 ""，不做猜测填充 |
| 第二实现即抽象 | 同一接口第二次写 → 必须抽公共层 |
| v1 硬化 | v1 完成后先加固（安全、配置、死代码），不加功能 |
| 用户视角验收 | 交付前从真实用户角度验证，不凭代码推断结果 |
| 知识自蒸馏 | 知识库要能给另一个 AI 直接自举，单文件可读 |

## 错误速查

| 错误 | 预防 |
|------|------|
| OCR 引擎复制粘贴 | 第二个引擎时抽基类 |
| 死代码残留 | 删除路由时检查函数体 |
| 硬编码路径 | 统一走配置/环境变量 |
| GPT prompt 未生效 | API 调用加集成测试 |
| Flask debug=True 上线 | 环境变量区分 dev/prod |
| 下载接口无鉴权 | 所有用户数据路由加 @login_required |
| 假设用户操作失误 | 先复现、先排查环境，不争论 |
| 快捷方式图标不对 | 安装后从用户视角检查桌面快捷方式 |

## 设计决策速查

| 决策 | 选择 | 原因 |
|------|------|------|
| OCR 策略 | 并行择优 | 准确率优先 |
| 依赖管理 | libs/ vendoring (v1) | 快速原型，v2 改 pip |
| 本地+云端 | SQLite + Supabase 双写 | 离线可用 + 跨设备 |
| Web vs CLI | Web (v1) | 用户友好，v2 加 CLI |
| 交付标准 | 用户验证 > 代码正确 | 防止"理论上OK实际不行" |
| 知识备份 | GitHub + 单文件 bundle | 可移植 > 格式精美 |

---

*蒸馏自: Codex 记忆系统 + 用户质量原则 + 2026-05-26 会话*
