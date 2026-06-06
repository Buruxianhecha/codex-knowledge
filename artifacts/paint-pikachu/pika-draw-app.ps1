param(
  [string]$OutputPath = "D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-handdrawn-gui.png",
  [int]$HoldSeconds = 90,
  [switch]$NoWindow
)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath "D:\") {
  $env:TEMP = "D:\Codex-Knowledge\tmp"
  $env:TMP = "D:\Codex-Knowledge\tmp"
  New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$canvasWidth = 1000
$canvasHeight = 700
$bitmap = New-Object System.Drawing.Bitmap($canvasWidth, $canvasHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([System.Drawing.Color]::White)

function New-Pen {
  param(
    [System.Drawing.Color]$Color,
    [float]$Width
  )
  $pen = New-Object System.Drawing.Pen($Color, $Width)
  $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
  $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
  $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
  return $pen
}

function Draw-Path {
  param(
    [System.Drawing.Drawing2D.GraphicsPath]$Path,
    [System.Drawing.Pen]$Pen
  )
  $graphics.DrawPath($Pen, $Path)
}

function Draw-Curve {
  param(
    [System.Drawing.Point[]]$Points,
    [System.Drawing.Pen]$Pen,
    [float]$Tension = 0.45
  )
  $graphics.DrawCurve($Pen, $Points, $Tension)
}

function Save-Canvas {
  $parent = Split-Path -Parent $OutputPath
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
}

$black = [System.Drawing.Color]::FromArgb(24, 24, 22)
$yellow = [System.Drawing.Color]::FromArgb(255, 218, 48)
$yellow2 = [System.Drawing.Color]::FromArgb(255, 231, 96)
$red = [System.Drawing.Color]::FromArgb(235, 54, 54)
$brown = [System.Drawing.Color]::FromArgb(96, 57, 26)
$white = [System.Drawing.Color]::White

$outline = New-Pen $black 6
$thin = New-Pen $black 4
$tailPen = New-Pen $black 6

$yellowBrush = New-Object System.Drawing.SolidBrush($yellow)
$yellow2Brush = New-Object System.Drawing.SolidBrush($yellow2)
$redBrush = New-Object System.Drawing.SolidBrush($red)
$blackBrush = New-Object System.Drawing.SolidBrush($black)
$brownBrush = New-Object System.Drawing.SolidBrush($brown)
$whiteBrush = New-Object System.Drawing.SolidBrush($white)

try {
  # Tail behind body.
  $tail = New-Object System.Drawing.Drawing2D.GraphicsPath
  $tail.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(690, 430),
    [System.Drawing.Point]::new(820, 340),
    [System.Drawing.Point]::new(775, 425),
    [System.Drawing.Point]::new(910, 425),
    [System.Drawing.Point]::new(790, 510),
    [System.Drawing.Point]::new(860, 615),
    [System.Drawing.Point]::new(690, 525)
  ))
  $graphics.FillPath($yellow2Brush, $tail)
  $graphics.DrawPath($tailPen, $tail)

  # Body.
  $body = New-Object System.Drawing.Drawing2D.GraphicsPath
  $body.AddEllipse(295, 175, 390, 470)
  $graphics.FillPath($yellowBrush, $body)
  $graphics.DrawPath($outline, $body)

  # Head.
  $head = New-Object System.Drawing.Drawing2D.GraphicsPath
  $head.AddEllipse(270, 90, 445, 330)
  $graphics.FillPath($yellowBrush, $head)
  $graphics.DrawPath($outline, $head)

  # Ears with black tips.
  $leftEar = New-Object System.Drawing.Drawing2D.GraphicsPath
  $leftEar.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(330, 128),
    [System.Drawing.Point]::new(215, 15),
    [System.Drawing.Point]::new(390, 85)
  ))
  $graphics.FillPath($yellowBrush, $leftEar)
  $graphics.DrawPath($outline, $leftEar)

  $leftTip = New-Object System.Drawing.Drawing2D.GraphicsPath
  $leftTip.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(215, 15),
    [System.Drawing.Point]::new(292, 46),
    [System.Drawing.Point]::new(268, 76)
  ))
  $graphics.FillPath($blackBrush, $leftTip)

  $rightEar = New-Object System.Drawing.Drawing2D.GraphicsPath
  $rightEar.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(632, 126),
    [System.Drawing.Point]::new(795, 24),
    [System.Drawing.Point]::new(702, 184)
  ))
  $graphics.FillPath($yellowBrush, $rightEar)
  $graphics.DrawPath($outline, $rightEar)

  $rightTip = New-Object System.Drawing.Drawing2D.GraphicsPath
  $rightTip.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(795, 24),
    [System.Drawing.Point]::new(748, 104),
    [System.Drawing.Point]::new(718, 73)
  ))
  $graphics.FillPath($blackBrush, $rightTip)

  # Face.
  $graphics.FillEllipse($blackBrush, 382, 215, 42, 48)
  $graphics.FillEllipse($blackBrush, 590, 215, 42, 48)
  $graphics.FillEllipse($whiteBrush, 396, 225, 12, 12)
  $graphics.FillEllipse($whiteBrush, 604, 225, 12, 12)
  $graphics.FillEllipse($redBrush, 320, 290, 78, 62)
  $graphics.FillEllipse($redBrush, 622, 290, 78, 62)

  $nose = New-Object System.Drawing.Drawing2D.GraphicsPath
  $nose.AddPolygon([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(500, 282),
    [System.Drawing.Point]::new(483, 300),
    [System.Drawing.Point]::new(518, 300)
  ))
  $graphics.FillPath($blackBrush, $nose)
  $graphics.DrawLine($thin, 500, 300, 500, 318)
  $graphics.DrawArc($thin, 470, 306, 62, 54, 25, 120)
  $graphics.DrawArc($thin, 500, 306, 62, 54, 35, 120)

  # Body details.
  $graphics.DrawArc($thin, 388, 370, 230, 125, 8, 164)
  Draw-Curve ([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(305, 388),
    [System.Drawing.Point]::new(226, 435),
    [System.Drawing.Point]::new(252, 505),
    [System.Drawing.Point]::new(325, 486)
  )) $thin
  Draw-Curve ([System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(677, 388),
    [System.Drawing.Point]::new(755, 435),
    [System.Drawing.Point]::new(728, 505),
    [System.Drawing.Point]::new(650, 486)
  )) $thin

  $graphics.FillEllipse($yellowBrush, 330, 588, 115, 55)
  $graphics.DrawEllipse($outline, 330, 588, 115, 55)
  $graphics.FillEllipse($yellowBrush, 555, 588, 115, 55)
  $graphics.DrawEllipse($outline, 555, 588, 115, 55)

  # Three small back stripes.
  $graphics.DrawLine($thin, 300, 238, 260, 205)
  $graphics.DrawLine($thin, 298, 270, 252, 250)
  $graphics.DrawLine($thin, 692, 238, 738, 205)
  $graphics.DrawLine($thin, 695, 270, 748, 248)

  # Hand-drawn label.
  $font = New-Object System.Drawing.Font("Arial", 64, [System.Drawing.FontStyle]::Bold)
  $graphics.DrawString("PIKA!", $font, $blackBrush, 58, 585)
  $font.Dispose()

  Save-Canvas
} finally {
  $outline.Dispose()
  $thin.Dispose()
  $tailPen.Dispose()
  $yellowBrush.Dispose()
  $yellow2Brush.Dispose()
  $redBrush.Dispose()
  $blackBrush.Dispose()
  $brownBrush.Dispose()
  $whiteBrush.Dispose()
}

if ($NoWindow) {
  $graphics.Dispose()
  $bitmap.Dispose()
  Write-Output $OutputPath
  return
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Codex Canvas - Pikachu Verified Final"
$form.Width = 1120
$form.Height = 820
$form.StartPosition = "CenterScreen"
$form.BackColor = [System.Drawing.Color]::FromArgb(28, 31, 36)
$form.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::None

$status = New-Object System.Windows.Forms.Label
$status.Text = "Verified final drawing | window=1120x820 | canvas=1000x700 | saved=$OutputPath"
$status.ForeColor = [System.Drawing.Color]::White
$status.BackColor = [System.Drawing.Color]::FromArgb(28, 31, 36)
$status.AutoSize = $false
$status.Left = 20
$status.Top = 12
$status.Width = 1060
$status.Height = 28
$form.Controls.Add($status)

$canvas = New-Object System.Windows.Forms.PictureBox
$canvas.Left = 40
$canvas.Top = 55
$canvas.Width = $canvasWidth
$canvas.Height = $canvasHeight
$canvas.BackColor = [System.Drawing.Color]::White
$canvas.BorderStyle = [System.Windows.Forms.BorderStyle]::FixedSingle
$canvas.Image = $bitmap
$form.Controls.Add($canvas)

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = [Math]::Max(1, $HoldSeconds) * 1000
$timer.Add_Tick({
  $timer.Stop()
  $form.Close()
})
$timer.Start()

$form.Add_FormClosed({
  Save-Canvas
  $graphics.Dispose()
  $bitmap.Dispose()
})

Write-Output $OutputPath
[System.Windows.Forms.Application]::Run($form)
