param(
  [int]$HoldSeconds = 180,
  [string]$ScreenshotPath = "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-window-verification.png",
  [string]$ReportPath = "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-window-verification.json"
)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath "D:\") {
  $env:TEMP = "D:\Codex-Knowledge\tmp"
  $env:TMP = "D:\Codex-Knowledge\tmp"
  New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
}

$drawScript = "D:\Codex-Knowledge\artifacts\paint-pikachu\pika-draw-app.ps1"
$screenshotScript = Join-Path $env:USERPROFILE ".codex\skills\screenshot\scripts\take_screenshot.ps1"

if (-not (Test-Path -LiteralPath $drawScript)) {
  throw "Draw script not found: $drawScript"
}
if (-not (Test-Path -LiteralPath $screenshotScript)) {
  throw "Screenshot script not found: $screenshotScript"
}

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class PikaWindowNative {
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT {
    public int Left;
    public int Top;
    public int Right;
    public int Bottom;
  }
  [DllImport("user32.dll")]
  public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
}
"@

$args = @(
  "-NoProfile",
  "-ExecutionPolicy", "Bypass",
  "-File", $drawScript,
  "-HoldSeconds", $HoldSeconds.ToString()
)

$proc = Start-Process -FilePath "powershell.exe" -ArgumentList $args -PassThru

$handle = [IntPtr]::Zero
$title = ""
$deadline = (Get-Date).AddSeconds(20)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Milliseconds 300
  $proc.Refresh()
  if ($proc.MainWindowHandle -ne 0) {
    $handle = $proc.MainWindowHandle
    $title = $proc.MainWindowTitle
    break
  }
}

if ($handle -eq [IntPtr]::Zero) {
  Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
  throw "Could not find Pikachu verification window handle."
}

Start-Sleep -Seconds 2

$rect = New-Object PikaWindowNative+RECT
if (-not [PikaWindowNative]::GetWindowRect($handle, [ref]$rect)) {
  Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
  throw "Could not get Pikachu verification window rectangle."
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ScreenshotPath) | Out-Null
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $screenshotScript -Path $ScreenshotPath -WindowHandle ([int]$handle) | Out-Null

$windowWidth = $rect.Right - $rect.Left
$windowHeight = $rect.Bottom - $rect.Top
$result = [ordered]@{
  processId = $proc.Id
  windowTitle = $title
  windowHandle = ([int]$handle)
  windowLeft = $rect.Left
  windowTop = $rect.Top
  windowWidth = $windowWidth
  windowHeight = $windowHeight
  expectedFormWidth = 1120
  expectedFormHeight = 820
  expectedCanvasWidth = 1000
  expectedCanvasHeight = 700
  screenshotPath = $ScreenshotPath
  screenshotBytes = (Get-Item -LiteralPath $ScreenshotPath).Length
  status = if ($windowWidth -ge 1080 -and $windowWidth -le 1160 -and $windowHeight -ge 780 -and $windowHeight -le 860) { "pass" } else { "needs_visual_review" }
}

$json = $result | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($ReportPath, $json, [System.Text.UTF8Encoding]::new($false))

Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Write-Output $json
