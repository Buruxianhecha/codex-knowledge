# pdf-to-excel

## 基本信息
- **创建日期**: 2026-05-24
- **状态**: v1 已完成
- **路径**: D:\Projects\pdf-to-excel

## 项目目标
将 PDF（日文 CAD/工程图纸）中的表格精确还原为格式化 Excel。

## 技术栈
| 层次 | 技术 | 用途 |
|------|------|------|
| Web 框架 | Flask 3.x | 路由、Session、模板 |
| PDF 解析 | pdfplumber | 文本型 PDF 表格检测 |
| OCR | Tesseract (TSV) | 图像型 PDF 文字识别 |
| OCR | PaddleOCR (japan) | 高精度日语 OCR |
| OCR | GPT-4o Vision | AI 兜底复杂表格 |
| PDF 渲染 | PyMuPDF (fitz) | 页面→PNG |
| Excel | openpyxl | 写入+样式控制 |
| 本地 DB | SQLite WAL | 用户、历史 |
| 云端 | Supabase REST | 跨设备同步 |
| 认证 | werkzeug | 密码哈希 |

## 实现方案
多级 OCR 降级链：pdfplumber → Tesseract → PaddleOCR → GPT-4o。
三路并行跑，按列数最多的结果输出（智能选择而非简单降级）。
Web 端上传→处理→下载，带账号系统和云端历史同步。

## 最终结果
- v1 可用，能处理日文 PDF 表格
- 多种 OCR 引擎协同工作
- 账号系统 + Supabase 云端同步完整
- 已知问题：安全漏洞（debug模式、无鉴权下载）、代码冗余、GPT prompt 未生效

## 关联
- lessons/multi-ocr-fallback.md
- lessons/quality-gating.md
- lessons/empty-over-fake.md
- patterns/multi-engine-parallel-select.md
- patterns/sqlite-migration.md
- mistakes/copy-paste-engines.md
- mistakes/dead-code-orphan.md
- mistakes/hardcoded-paths.md
- decisions/ocr-fallback-vs-select.md
- decisions/libs-vendoring.md
