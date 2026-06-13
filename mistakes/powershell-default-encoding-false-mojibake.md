---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-06-13
verified_in: [codex-diary, windows-powershell]
cross_refs:
  - mistakes/encoding-string-replace-windows.md
  - lessons/automation-diary-source-of-truth.md
---

# PowerShell 默认编码显示导致误判 mojibake

## 错误

用 PowerShell 默认 `Get-Content` 读取无 BOM UTF-8 中文文件时，看到终端输出变成 mojibake，就直接判断文件已经损坏。

## 表现

- 同一个 Markdown 文件默认读取显示乱码，但 `Get-Content -Encoding UTF8` 读回正常。
- 自动化 `name/prompt` 被误认为损坏，实际只有 `cwds` 路径存在编码错位。
- 日记或知识库会记录错误结论，把“显示问题”写成“文件损坏”。

## 根因

Windows PowerShell 的默认编码行为和终端输出编码不等于文件真实编码。无 BOM UTF-8 文件在默认读取路径下可能显示为 ANSI 解码后的乱码。

## 正确做法

1. 读取中文 Markdown/TOML 时显式指定 `-Encoding UTF8`。
2. 如果默认输出乱码，不立即下结论，先做显式 UTF-8 读回。
3. 只有显式 UTF-8 读回仍异常，才把文件标记为真正编码损坏。
4. 写入后也用显式 UTF-8 读回检查，避免把显示层问题当成内容层问题。

## 验证命令

```powershell
rtk proxy powershell -NoProfile -Command "Get-Content -LiteralPath '<file>' -Encoding UTF8 -TotalCount 20"
```

## 标签

#encoding #mojibake #powershell #utf8 #windows
