# pdf-to-excel 压缩项目档案 v5

2026-05-24 的 Flask + OCR + Excel 项目。核心方法：多 OCR/解析结果择优、输出质量门控、空值优于假值、SQLite 渐进迁移、v1 后安全/依赖/鉴权硬化。

重要生命周期修正：多引擎择优、SQLite migration、输出质量门控目前都只有 `pdf-to-excel` 一个独立项目应用证据，因此属于 `active`，不能写成跨项目 `verified`。

历史问题：debug 上线、下载鉴权缺失、硬编码路径、SYSTEM_PROMPT 定义未传入、死代码和依赖可复现性不足。
