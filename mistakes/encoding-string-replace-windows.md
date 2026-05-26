---
status: active
confidence: 0.95
reuse_count: 1
last_used: 2026-05-26
cross_refs:
  - projects/2026-05-26-morning-briefing.md
  - lessons/rest-over-sdk-windows.md
---

# PowerShell 字符串替换导致编码损坏

## 错误

用 `[System.IO.File]::WriteAllText` + `.Replace()` 修改 UTF-8 PowerShell 脚本，导致文件编码被破坏。

## 表现

- 中文注释变成乱码（如 `趺｡蝨ｰ` 代替 `生成`）
- Markdown 表格分隔符 `|---|---|` 中的 `-` 被错误转义
- PowerShell 解析器报 `Missing ')' in function parameter list` 等语法错误

## 根因

```powershell
# 错误的做法
$content = Get-Content $path -Raw -Encoding UTF8
$content = $content.Replace($old, $new)   # .NET 内部使用 UTF-16
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
```

`WriteAllText` 配合 `UTF8Encoding($false)`（无 BOM）写入时，.NET 的内存字符串（UTF-16）转 UTF-8 的路径与原始文件不一致，导致多字节字符（中文、特殊符号）在转换时错位。

## 修复

```powershell
# 正确的做法：保持 PowerShell 原生管道
$content | Out-File $path -Encoding UTF8

# 或者确保一致的编码路径
$utf8 = New-Object System.Text.UTF8Encoding $true  # 带 BOM
[System.IO.File]::WriteAllText($path, $content, $utf8)
```

## 预防规则

1. **PowerShell 脚本修改优先用 `Out-File -Encoding UTF8`，而非 `[System.IO.File]` 方法**
2. **如必须用 .NET 方法，始终带 BOM（`$true`）以确保编码一致性**
3. **修改后立即用 `Get-Content -TotalCount 5` 验证文件可读性**
4. **对含有中文/日文/特殊符号的文件，修改后做语法检查：`Get-Command -Syntax (Get-Content $path -Raw)`**

## 关联

- projects/2026-05-26-morning-briefing.md (本错误发生场景)
- anti-patterns/hardcoded-configuration.md (另一类路径相关错误)

## 标签
#powershell #encoding #utf8 #string-manipulation #windows #chinese
