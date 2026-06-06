param(
  [string]$ImagePath = "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-handdrawn-gui.png",
  [string]$ReportPath = "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-verification.json"
)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath "D:\") {
  $env:TEMP = "D:\Codex-Knowledge\tmp"
  $env:TMP = "D:\Codex-Knowledge\tmp"
  New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
}

Add-Type -AssemblyName System.Drawing

if (-not (Test-Path -LiteralPath $ImagePath)) {
  throw "Image not found: $ImagePath"
}

$image = [System.Drawing.Bitmap]::FromFile($ImagePath)
try {
  $width = $image.Width
  $height = $image.Height
  $sampleStep = 4
  $black = 0
  $yellow = 0
  $red = 0
  $white = 0
  $other = 0

  for ($y = 0; $y -lt $height; $y += $sampleStep) {
    for ($x = 0; $x -lt $width; $x += $sampleStep) {
      $c = $image.GetPixel($x, $y)
      if ($c.R -lt 55 -and $c.G -lt 55 -and $c.B -lt 55) {
        $black++
      } elseif ($c.R -gt 235 -and $c.G -gt 235 -and $c.B -gt 235) {
        $white++
      } elseif ($c.R -gt 210 -and $c.G -gt 150 -and $c.B -lt 90) {
        $yellow++
      } elseif ($c.R -gt 180 -and $c.G -lt 100 -and $c.B -lt 100) {
        $red++
      } else {
        $other++
      }
    }
  }

  $result = [ordered]@{
    imagePath = $ImagePath
    width = $width
    height = $height
    aspectRatio = [Math]::Round($width / [double]$height, 3)
    fileBytes = (Get-Item -LiteralPath $ImagePath).Length
    lastWriteTime = (Get-Item -LiteralPath $ImagePath).LastWriteTime.ToString("s")
    sampledPixels = $black + $yellow + $red + $white + $other
    blackSamples = $black
    yellowSamples = $yellow
    redSamples = $red
    whiteSamples = $white
    otherSamples = $other
    expectedFeatures = @(
      "large rounded yellow body",
      "two pointed ears with black tips",
      "black eyes",
      "red cheeks",
      "small mouth",
      "zigzag lightning tail",
      "PIKA label"
    )
    status = if ($width -ge 900 -and $height -ge 650 -and $yellow -gt 500 -and $red -gt 20 -and $black -gt 200) { "pass" } else { "needs_visual_review" }
  }

  $json = $result | ConvertTo-Json -Depth 4
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReportPath) | Out-Null
  [System.IO.File]::WriteAllText($ReportPath, $json, [System.Text.UTF8Encoding]::new($false))
  Write-Output $json
} finally {
  $image.Dispose()
}
