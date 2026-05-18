param(
    [Parameter(Mandatory = $true)] [string]$JavaExe,
    [Parameter(Mandatory = $true)] [string]$JarPath,
    [Parameter(Mandatory = $true)] [string]$PidFile,
    [Parameter(Mandatory = $true)] [string]$OutLog,
    [Parameter(Mandatory = $true)] [string]$ErrLog,
    [int]$Port = 8080,
    [string]$Profile = "",
    [int]$XmsMB = 512,
    [int]$XmxMB = 1024,
    [string]$Priority = "AboveNormal",
    [int64]$AffinityMask = 0
)

$ErrorActionPreference = "Stop"

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

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JavaExe
$psi.WorkingDirectory = Resolve-Path (Join-Path $PSScriptRoot "..")
$psi.Arguments = Join-ProcessArguments $arguments
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $false

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $psi

$outStream = [System.IO.File]::Open($OutLog, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
$errStream = [System.IO.File]::Open($ErrLog, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
$outWriter = New-Object System.IO.StreamWriter($outStream, [System.Text.Encoding]::UTF8)
$errWriter = New-Object System.IO.StreamWriter($errStream, [System.Text.Encoding]::UTF8)
$outWriter.AutoFlush = $true
$errWriter.AutoFlush = $true

$process.add_OutputDataReceived({ if ($EventArgs.Data) { $outWriter.WriteLine($EventArgs.Data) } })
$process.add_ErrorDataReceived({ if ($EventArgs.Data) { $errWriter.WriteLine($EventArgs.Data) } })

try {
    [void]$process.Start()
    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()
    $process.Id | Set-Content -Path $PidFile -Encoding ASCII

    try {
        $process.PriorityClass = $Priority
        if ($AffinityMask -gt 0) {
            $process.ProcessorAffinity = [IntPtr]$AffinityMask
        }
    } catch {
        $errWriter.WriteLine("设置优先级或 CPU 亲和性失败：$($_.Exception.Message)")
    }

    $process.WaitForExit()
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    exit $process.ExitCode
} finally {
    $outWriter.Dispose()
    $errWriter.Dispose()
    $outStream.Dispose()
    $errStream.Dispose()
}
