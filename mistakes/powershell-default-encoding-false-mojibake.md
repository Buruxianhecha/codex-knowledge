---
status: active
confidence: 0.92
reuse_count: 2
last_used: 2026-06-15
---

# PowerShell 默认编码导致的假性正确

## 问题

在 Windows/PowerShell 场景里，路径、文本内容和控制台输出可能因为默认编码不一致而出现 mojibake。看起来“读到了内容”，实际上内容已经被误读。

## 风险

- 中文路径显示异常
- Markdown / TOML / 日记正文被误判
- 自动化写入后才发现内容乱码

## 处理建议

- 读取文本时显式指定 `-Encoding UTF8`
- 不要只看控制台显示，必要时做文件级校验
- 对关键归档文件先小范围验证，再批量处理
- 关键路径同样要显式化，不能依赖 `$env:CODEX_HOME` 这类未验证变量
