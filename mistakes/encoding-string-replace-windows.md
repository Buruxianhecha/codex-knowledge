---
status: active
confidence: 0.98
reuse_count: 1
last_used: 2026-08-26
verified_in: [morning-briefing]
expires_after: 2026-11-30
cross_refs:
  - projects/2026-05-26-morning-briefing.md
  - lessons/rest-over-sdk-windows.md
  - mistakes/powershell-default-encoding-false-mojibake.md
---

# PowerShell 读改写流程中的编码损坏：不要把根因错归给 .NET 字符串

> 本条在 2026-08-26 重新审校。历史事故是真实的，但旧版把“`.NET` 内部 UTF-16 字符串转 UTF-8”直接写成乱码根因，这个因果不成立；正常的 Unicode 转码本身不会天然把中文破坏。

## 历史症状

一次 PowerShell 脚本自动修改后出现：

- 中文注释乱码。
- Markdown/脚本中的特殊字符发生异常变化。
- 随后出现 PowerShell 语法错误。

事故与一次 read-modify-write / string replacement 流程相关，但当时证据不足以把问题唯一归因于 `WriteAllText`、`.Replace()` 或 BOM 中的任何单项。

## 为什么旧根因过强

```text
.NET String (UTF-16 logical characters)
-> Encoding.UTF8.GetBytes / WriteAllText(... UTF8Encoding ...)
```

这是正常的 Unicode 编码过程。只要输入字符串里的字符本身正确，转成 UTF-8 不会因为“内存里是 UTF-16”就自动乱码。

真正应该调查的是整个字节链：

```text
原始 bytes
-> 用什么 encoding 解码
-> 内存中的字符是否已经损坏
-> replacement 是否改变了转义/语法
-> 用什么 encoding 重编码
-> BOM 是否符合目标 PowerShell 版本
-> 下游程序用什么 encoding 再读取
```

## PowerShell 版本差异

### Windows PowerShell 5.1

- 各 cmdlet 默认编码并不一致。
- `-Encoding UTF8` 表示 UTF-8 with BOM。
- 对含非 ASCII 字符的 `.ps1`，UTF-8 without BOM 可能被 Windows PowerShell 误按旧 ANSI code page 解释。

### PowerShell 6/7+

- 默认文本输出趋向 `utf8NoBOM`。
- `-Encoding utf8` 在现代 PowerShell 中通常是 UTF-8 without BOM。
- 可显式选择 `utf8BOM` / `utf8NoBOM`。

所以“始终带 BOM”与“始终无 BOM”都不是跨环境最佳规则。

## 更可靠的修复模式

### 1. 先确定源编码

如果文件来源已知，读入时显式指定与源文件一致的编码；未知时不要仅凭控制台肉眼判断。

### 2. 修改前后保留字节证据

```powershell
Get-FileHash $path -Algorithm SHA256
Format-Hex $path | Select-Object -First 4
```

必要时保存备份并比较字节/BOM。

### 3. 选择目标运行时需要的编码

PowerShell 7+ 跨平台脚本通常可使用 UTF-8 no BOM；需要兼容 Windows PowerShell 5.1 且含非 ASCII 时，UTF-8 BOM 往往更安全。最终由**目标运行时**决定，而不是固定教条。

### 4. 不把文本替换与语法替换混为一谈

`.Replace()` 本身只做字符串替换，但 replacement 内容可能改变引号、反引号、管道符、换行或 here-string 边界。语法损坏与字符编码损坏需要分别验证。

### 5. 用解析器验证脚本

```powershell
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  $path,
  [ref]$tokens,
  [ref]$errors
) | Out-Null
$errors
```

这比用与文件解析无关的命令去“猜”语法是否正确更可靠。

## 预防规则

1. 明确 PowerShell 5.1 还是 7+。
2. 读、改、写每一步明确 encoding。
3. 自动修改前备份/哈希，修改后比较。
4. 对非 ASCII 样本做真实回归测试。
5. 同时验证字符内容与 PowerShell AST/Parser 错误。
6. 不把 BOM 当成万能修复，也不把 `.NET String=UTF-16` 当作乱码根因。

## 标签

#powershell #encoding #utf8 #bom #string-manipulation #windows #postmortem