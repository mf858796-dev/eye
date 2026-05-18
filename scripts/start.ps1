param(
    [int]$Port = 8080,
    [string]$Profile = "",
    [string]$JavaHome = "",
    [string]$MavenHome = "",
    [switch]$Build,
    [switch]$NoBuild,
    [int]$XmsMB = 512,
    [int]$XmxMB = 1024,
    [ValidateSet("Normal", "AboveNormal", "High")]
    [string]$Priority = "AboveNormal",
    [ValidateSet("Hidden", "Minimized", "Normal")]
    [string]$WindowStyle = "Hidden",
    [int64]$AffinityMask = 0,
    [int]$MaxLogMB = 20
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$PidFile = Join-Path $Root "logs\eye-tracking.pid"
$OutLog = Join-Path $Root "logs\server.out.log"
$ErrLog = Join-Path $Root "logs\server.err.log"
$JarPath = Join-Path $Root "target\eye-tracking-system-1.0.0.jar"

function Write-Step($message) {
    Write-Host "[eye-tracking] $message"
}

function Resolve-Java {
    if ($JavaHome) {
        $candidate = Join-Path $JavaHome "bin\java.exe"
        if (Test-Path $candidate) { return $candidate }
        throw "JAVA_HOME 无效：$JavaHome"
    }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "未找到 java。请安装 JDK，或使用 -JavaHome 指定路径。"
}

function Resolve-Maven {
    $mvnw = Join-Path $Root "mvnw.cmd"
    if (Test-Path $mvnw) { return $mvnw }
    if ($MavenHome) {
        $candidate = Join-Path $MavenHome "bin\mvn.cmd"
        if (Test-Path $candidate) { return $candidate }
        throw "MAVEN_HOME 无效：$MavenHome"
    }
    if ($env:MAVEN_HOME) {
        $candidate = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $ideaMaven = "E:\IntelliJ IDEA 2023.3.5\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMaven) {
        Write-Warning "未找到独立 Maven，临时使用 IntelliJ 内置 Maven。建议安装 Maven 或添加 Maven Wrapper。"
        return $ideaMaven
    }
    throw "未找到 Maven。请安装 Maven，或添加 Maven Wrapper。"
}

function Get-JavaMajorVersion($javaExe) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $javaExe
    $psi.Arguments = "-version"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardOutput = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    [void]$process.Start()
    $stderr = $process.StandardError.ReadToEnd()
    $stdout = $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    $line = (($stderr + "`n" + $stdout) -split "`r?`n" | Select-Object -First 1).ToString()
    if ($line -match '"1\.(\d+)') { return [int]$Matches[1] }
    if ($line -match '"(\d+)') { return [int]$Matches[1] }
    return 0
}

function Rotate-Log($path) {
    if (!(Test-Path $path)) { return }
    $maxBytes = [int64]$MaxLogMB * 1024 * 1024
    $item = Get-Item $path
    if ($item.Length -lt $maxBytes) { return }
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Move-Item -LiteralPath $path -Destination "$path.$stamp" -Force
}

function Join-ProcessArguments([string[]]$items) {
    $quoted = foreach ($item in $items) {
        $text = [string]$item
        if ($text -match '[\s"]') {
            '"' + ($text -replace '"', '\"') + '"'
        } else {
            $text
        }
    }
    return ($quoted -join " ")
}

function Get-PortOwnersFromNetstat($port) {
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

function Test-PortFree($port) {
    $listener = $null
    try {
        $listener = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Any, $port)
        $listener.Start()
    } catch {
        $owners = Get-PortOwnersFromNetstat $port
        if ($owners.Count -gt 0) {
            throw "端口 $port 已被占用，进程 PID：$($owners -join ', ')"
        }
        throw "端口 $port 不可用：$($_.Exception.Message)"
    } finally {
        if ($listener) { $listener.Stop() }
    }
}

function Test-ExistingProcess {
    if (!(Test-Path $PidFile)) { return }
    $existingPid = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$existingPid) { return }
    $process = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
    if ($process) {
        throw "服务似乎已经在运行，PID=$existingPid。请先执行 scripts\stop.ps1。"
    }
    Remove-Item -LiteralPath $PidFile -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $Root "logs") | Out-Null
Test-ExistingProcess
Test-PortFree $Port

$javaExe = Resolve-Java
$javaMajor = Get-JavaMajorVersion $javaExe
if ($javaMajor -lt 8) {
    throw "JDK 版本过低：检测到 Java $javaMajor，至少需要 Java 8。建议使用 Java 17。"
}
if ($javaMajor -lt 17) {
    Write-Warning "当前 Java $javaMajor 可运行本项目，但眼动实时采集建议使用 JDK 17+。"
}

$needBuild = $Build -or (!(Test-Path $JarPath))
if ($NoBuild) { $needBuild = $false }
if ($needBuild) {
    $mvn = Resolve-Maven
    Write-Step "构建 JAR：$mvn clean package -DskipTests"
    & $mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "构建失败，已停止启动。"
    }
}
if (!(Test-Path $JarPath)) {
    throw "未找到 JAR：$JarPath。请去掉 -NoBuild 后重新启动。"
}

Rotate-Log $OutLog
Rotate-Log $ErrLog

$arguments = @(
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
    "-Xms${XmsMB}m",
    "-Xmx${XmxMB}m",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=30",
    "-Dserver.port=$Port"
)
if ($Profile) {
    $arguments += "-Dspring.profiles.active=$Profile"
}
$arguments += @("-jar", $JarPath)

$argumentLine = Join-ProcessArguments $arguments
$startOptions = @{
    FilePath = $javaExe
    ArgumentList = $argumentLine
    WorkingDirectory = $Root
    RedirectStandardOutput = $OutLog
    RedirectStandardError = $ErrLog
    PassThru = $true
}
if ($WindowStyle -ne "Normal") {
    $startOptions.WindowStyle = $WindowStyle
}
$process = Start-Process @startOptions

try {
    $process.PriorityClass = $Priority
    if ($AffinityMask -gt 0) {
        $process.ProcessorAffinity = [IntPtr]$AffinityMask
    }
} catch {
    Write-Warning "设置进程优先级或 CPU 亲和性失败：$($_.Exception.Message)"
}

$process.Id | Set-Content -Path $PidFile -Encoding ASCII
Write-Step "已启动，PID=$($process.Id)，优先级=$Priority，窗口=$WindowStyle，端口=$Port"
Write-Step "日志：$OutLog / $ErrLog"
Write-Step "访问地址：http://localhost:$Port/eye-tracking/"
