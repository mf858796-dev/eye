param(
    [int]$Port = 8080
)

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$PidFile = Join-Path $Root "logs\eye-tracking.pid"

function Get-ListeningPortOwners($port) {
    $owners = @()
    try {
        $lines = netstat -ano -p tcp | Select-String -Pattern "[:.]$port\s"
        foreach ($line in $lines) {
            $parts = $line.Line.Trim() -split "\s+"
            if ($parts.Length -ge 5 -and $parts[1] -match "[:.]$port$" -and $parts[3] -eq "LISTENING") {
                $owners += $parts[-1]
            }
        }
    } catch {
        return @()
    }
    return @($owners | Sort-Object -Unique)
}

if (Test-Path $PidFile) {
    $pidText = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    $process = if ($pidText) { Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue } else { $null }
    if ($process) {
        Write-Host "[eye-tracking] 进程运行中：PID=$pidText，Name=$($process.ProcessName)"
    } else {
        Write-Host "[eye-tracking] PID 文件存在，但进程未运行。"
    }
} else {
    Write-Host "[eye-tracking] 未找到 PID 文件。"
}

$owners = Get-ListeningPortOwners $Port
if ($owners.Count -gt 0) {
    Write-Host "[eye-tracking] 端口 $Port 正在被使用，PID：$($owners -join ', ')"
} else {
    Write-Host "[eye-tracking] 端口 $Port 未监听。"
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:$Port/eye-tracking/" -UseBasicParsing -TimeoutSec 5
    Write-Host "[eye-tracking] HTTP 检查通过：$($response.StatusCode)"
} catch {
    Write-Host "[eye-tracking] HTTP 检查失败：$($_.Exception.Message)"
}

$outLog = Join-Path $Root "logs\server.out.log"
$errLog = Join-Path $Root "logs\server.err.log"
if (Test-Path $outLog) {
    Write-Host "`n--- server.out.log tail ---"
    Get-Content $outLog -Tail 20
}
if (Test-Path $errLog) {
    Write-Host "`n--- server.err.log tail ---"
    Get-Content $errLog -Tail 20
}
