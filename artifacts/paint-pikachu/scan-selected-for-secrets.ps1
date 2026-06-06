$ErrorActionPreference = "Stop"

$paths = @(
  "D:\Codex-Knowledge\codex-plugin-operations\computer-use-paint-wechat-plugin-audit-2026-06-06.md",
  "D:\Codex-Knowledge\codex-plugin-operations\codex-plugin-runtime-verification-workflow.md",
  "D:\Codex-Knowledge\artifacts\paint-pikachu\pika-draw-app.ps1",
  "D:\Codex-Knowledge\artifacts\paint-pikachu\verify-pikachu.ps1",
  "D:\Codex-Knowledge\artifacts\paint-pikachu\show-and-capture-pika.ps1",
  "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-verification.json",
  "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-window-verification.json"
)

$patterns = @(
  "gho_",
  "github_pat_",
  "BeYr",
  "SECRET",
  "TOKEN"
)

$matches = foreach ($path in $paths) {
  if (Test-Path -LiteralPath $path) {
    Select-String -LiteralPath $path -Pattern $patterns -SimpleMatch -ErrorAction SilentlyContinue
  }
}

if ($matches) {
  $matches | ForEach-Object {
    "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line
  }
  exit 2
}

Write-Output "No selected-file secret patterns found."
