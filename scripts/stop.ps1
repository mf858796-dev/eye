param(
    [int]$TimeoutSeconds = 20,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$PidFile = Join-Path $Root "logs\eye-tracking.pid"

if (!(Test-Path $PidFile)) {
    Write-Host "[eye-tracking] 未找到 PID 文件，服务可能没有运行。"
    exit 0
}

$pidText = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$pidText) {
    Remove-Item -LiteralPath $PidFile -Force
    Write-Host "[eye-tracking] PID 文件为空，已清理。"
    exit 0
}

$process = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
if (!$process) {
    Remove-Item -LiteralPath $PidFile -Force
    Write-Host "[eye-tracking] 进程不存在，已清理 PID 文件。"
    exit 0
}

Write-Host "[eye-tracking] 正在停止服务，PID=$pidText"
if (!$Force) {
    try {
        $process.CloseMainWindow() | Out-Null
    } catch {
        # Console-less Java processes often cannot receive CloseMainWindow.
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $process = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
        if (!$process) {
            Remove-Item -LiteralPath $PidFile -Force
            Write-Host "[eye-tracking] 服务已停止。"
            exit 0
        }
    }
}

Stop-Process -Id ([int]$pidText) -Force
Remove-Item -LiteralPath $PidFile -Force
Write-Host "[eye-tracking] 服务已强制停止。"
