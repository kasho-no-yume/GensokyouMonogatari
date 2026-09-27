<#
.SYNOPSIS
    Run Gradle tasks safely from an AI agent / opencode session without wedging the session pipe.

.DESCRIPTION
    Why this exists
    ---------------
    opencode's Windows shell tool treats "stdout pipe reached EOF" as the condition for
    a call to return, NOT "the command process exited". Any descendant process that
    inherits the stdout/stderr handles keeps that pipe from ever reaching EOF. The
    symptom is deceptive: the TUI keeps printing output normally, but the tool call
    never returns, the spinner spins forever, and the only way out is to kill the
    session by hand.

    Gradle's run* tasks (runClient / runServer / runGameTestServer) launch a Minecraft
    JVM that inherits exactly those handles, which makes them the prime offender.

    How this script avoids it
    -------------------------
      1. Start-Process -WindowStyle Hidden fully detaches from the agent's console, and
         stdout/stderr are redirected into a log file, so no descendant holds the
         agent's pipe. The pipe can therefore reach EOF normally.
      2. cmd writes the build's exit code into a sentinel file when the build ends.
      3. This script polls that sentinel while tailing the log, so the caller still
         sees live progress, and the moment the sentinel appears the script exits,
         restoring a well-defined return condition.

    NEVER use `cmd /c start /b ...` here: `start /b` inherits handles, which is
    precisely the bug being worked around.

    NOTE: this file is intentionally ASCII-only. PowerShell 5.1 reads BOM-less UTF-8
    using the system ANSI code page, which corrupts multi-byte characters and can
    derail the parser.

.EXAMPLE
    .\tools\gradle_task.ps1 build
    Streams `build`, prints the log live, returns the real exit code.

.EXAMPLE
    .\tools\gradle_task.ps1 runServer -TimeoutSec 300
    Runs runServer and force-returns after 5 minutes even though the server keeps
    running (the process is left alone, not killed).

.EXAMPLE
    .\tools\gradle_task.ps1 runClient -NoWait
    Returns immediately and leaves runClient in the background.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]]$Task,

    [int]$TimeoutSec = 1800,

    # Return right away without waiting for the build; prints the log path.
    [switch]$NoWait,

    # Regex applied to DISPLAYED log lines only. The build is never touched, so this
    # is safe, unlike piping a live command into Select-Object -First / -Last.
    [string]$Filter,

    # Catch-all for gradle's own flags (--rerun-tasks, -x, --info, ...).
    # Without this, PowerShell tries to bind '--rerun-tasks' to a named parameter
    # and the call fails with ParameterBindingException.
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArg
)

$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
$gradlew     = Join-Path $projectDir 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "gradlew.bat not found at $gradlew"
}

$logDir = Join-Path $projectDir 'build\agent-logs'
if (-not (Test-Path -LiteralPath $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$stamp    = Get-Date -Format 'yyyyMMdd-HHmmss'
$safeTask = ($Task -join '_') -replace '[^\w.-]', '_'
if ($safeTask.Length -gt 60) { $safeTask = $safeTask.Substring(0, 60) }

$logFile  = Join-Path $logDir "$stamp-$safeTask.log"
$exitFile = "$logFile.exit"

# Quote paths to survive cmd's argument parsing on Windows.
$quotedGradlew = '"{0}"' -f $gradlew
$quotedLog     = '"{0}"' -f $logFile
$quotedExit    = '"{0}"' -f $exitFile
# Drop null/empty entries: with only -Filter and no gradle switches, $GradleArg is an
# empty array and the pipeline still iterates once, formatting $null into "" and making
# gradle receive an empty task path ("Cannot locate matching tasks for an empty path").
$taskArgs      = (@($Task) + @($GradleArg) |
        Where-Object { $null -ne $_ -and "$_".Trim() -ne '' } |
        ForEach-Object { '"{0}"' -f $_ }) -join ' '

# /v:on enables delayed expansion so !ERRORLEVEL! is evaluated AFTER gradle finishes.
# The WMI-spawned process has no working directory of ours, so cd into the project.
$inner     = "$quotedGradlew $taskArgs --console=plain > $quotedLog 2>&1 & echo !ERRORLEVEL! > $quotedExit"
$cmdLine   = 'cmd /v:on /c "cd /d ""' + $projectDir + '"" & ' + $inner + '"'

Write-Host "[gradle_task] launching: gradlew $($Task -join ' ')"
Write-Host "[gradle_task] log: $logFile"

# Spawn via the WMI service rather than Start-Process. This is the crucial part:
# the WMI service (WmiPrvSE.exe) creates the process, so its parent is the WMI
# provider -- NOT this PowerShell. It therefore inherits none of the agent's
# stdout/stderr handles, and the agent's pipe reaches EOF immediately.
#
# Start-Process -WindowStyle Hidden does NOT work here: empirically it still lets
# the descendant hold the inherited handles, which wedges the tool call forever.
# Spawn via the WMI service rather than Start-Process. This is the crucial part:
# the WMI service (WmiPrvSE.exe) creates the process, so its parent is the WMI
# provider -- NOT this PowerShell. It therefore inherits none of the agent's
# stdout/stderr handles, and the agent's pipe reaches EOF immediately.
#
# Start-Process -WindowStyle Hidden does NOT work here: empirically it still lets
# the descendant hold the inherited handles, which wedges the tool call forever.
#
# The legacy [wmiclass] API is used because it binds the Win32_ProcessStartup
# parameter correctly. Newer paths do not:
#   - Invoke-CimMethod -Arguments @{ ProcessStartupInformation = ... } -> 0x80041005 type mismatch
#   - New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -> creates fine but will not bind
# ShowWindow = 0 (SW_HIDE) keeps the console window from flashing on the desktop.
$startup = ([wmiclass]'Win32_ProcessStartup').CreateInstance()
$startup.ShowWindow = 0

$created = ([wmiclass]'Win32_Process').Create($cmdLine, $null, $startup)

if ($created.ReturnValue -ne 0) {
    throw "Failed to spawn detached build (Win32_Process.Create returned $($created.ReturnValue))"
}
Write-Host "[gradle_task] detached pid: $($created.ProcessId)"

if ($NoWait) {
    Write-Host "[gradle_task] detached. follow with: Get-Content -Wait '$logFile'"
    return
}

# ---- Poll the sentinel while tailing the log ----
# IMPORTANT: every display decision here is made against lines already written to
# $logFile. The running build is never piped through PowerShell, so it can neither be
# truncated (Select-Object -First) nor left without a reader (Out-String buffering).
$sw        = [Diagnostics.Stopwatch]::StartNew()
$lastLine  = 0
$timedOut  = $false
$code      = $null
$lastBeat  = 0.0

function Show-NewLines {
    param([string[]]$Lines)
    if (-not $Lines -or $Lines.Count -eq 0) { return }
    if ($Filter) { $Lines = @($Lines | Where-Object { $_ -match $Filter }) }
    if ($Lines.Count -gt 0) { $Lines | ForEach-Object { Write-Host $_ } }
}

while ($true) {
    if (Test-Path -LiteralPath $exitFile) {
        Start-Sleep -Milliseconds 200   # let cmd flush
        $code = (Get-Content -LiteralPath $exitFile -Raw).Trim()
        break
    }

    if (Test-Path -LiteralPath $logFile) {
        $all = @(Get-Content -LiteralPath $logFile -ErrorAction SilentlyContinue)
        if ($all.Count -gt $lastLine) {
            Show-NewLines -Lines $all[$lastLine..($all.Count - 1)]
            $lastLine = $all.Count
        }
    } elseif ($Filter -and ($sw.Elapsed.TotalSeconds - $lastBeat) -ge 10) {
        # With a filter, silence is expected; emit a heartbeat so it is not mistaken
        # for a hang.
        $lastBeat = $sw.Elapsed.TotalSeconds
        Write-Host "[gradle_task] ...still running ($([math]::Round($sw.Elapsed.TotalSeconds,0))s)"
    }

    if ($sw.Elapsed.TotalSeconds -gt $TimeoutSec) { $timedOut = $true; break }
    Start-Sleep -Milliseconds 400
}

# Flush any trailing lines written just before the sentinel appeared.
if (Test-Path -LiteralPath $logFile) {
    $all = @(Get-Content -LiteralPath $logFile -ErrorAction SilentlyContinue)
    if ($all.Count -gt $lastLine) {
        Show-NewLines -Lines $all[$lastLine..($all.Count - 1)]
    }
}

Write-Host ''

if ($timedOut) {
    Write-Warning "[gradle_task] TIMEOUT after ${TimeoutSec}s. Build left running (detached, safe to ignore)."
    Write-Warning "[gradle_task] log: $logFile"
    exit 124
}

$exitCode = 0
if ($code -match '^\d+$') { $exitCode = [int]$code }

if ($exitCode -eq 0) {
    Write-Host "[gradle_task] OK in $([math]::Round($sw.Elapsed.TotalSeconds,1))s"
} else {
    Write-Host "[gradle_task] FAILED (exit $exitCode) after $([math]::Round($sw.Elapsed.TotalSeconds,1))s"
    Write-Host "[gradle_task] log: $logFile"
}
exit $exitCode
