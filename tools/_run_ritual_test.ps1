param(
    # Smoke mode: stop as soon as the FIRST [GS-TEST]/[GS-AUTO] line appears
    # (proves the explicit trigger works) instead of waiting for ALL_DONE.
    [switch]$Smoke
)
$ErrorActionPreference = 'Continue'
Set-Location 'D:\code\Gensokyou'

# Manual dev harness: launches the ISOLATED test server (run-test/), sends the
# test-pack entry function over RCON, polls for [GS-TEST]/[GS-AUTO], then stops.
#
# Test packs MUST NOT self-trigger on load, so the RCON command is the ONLY trigger.
#
# Spawn model: WMI (Win32_Process.Create), NOT Start-Process -WindowStyle Hidden.
# Start-Process still lets the game JVM inherit this shell's stdout/stderr handles,
# which wedges an agent tool call forever (see AGENTS.md). WMI spawns from the WMI
# provider, so no descendant holds our pipe, and output goes to a file we poll.
#
# Trigger channel: RCON (see tools/rcon_cmd.py). `gradlew runServerTest < stdin.txt`
# does not work: the Gradle daemon does not forward stdin to the JavaExec run task.
$projectDir = 'D:\code\Gensokyou'
$gradlew = Join-Path $projectDir 'gradlew.bat'
$mods = Join-Path $projectDir 'run\mods'
$stash = Join-Path $projectDir 'tools\_mod_stash'
$testDir = Join-Path $projectDir 'run-test'
$propsFile = Join-Path $testDir 'server.properties'
$outFile = Join-Path $projectDir 'build_out.txt'
$rconCmd = Join-Path $projectDir 'tools\rcon_cmd.py'
$rconPort = 25575
$rconPass = 'gsdev'

New-Item -ItemType Directory -Force -Path $testDir | Out-Null

# Enable RCON on the isolated server (idempotent upsert).
$props = @{}
if (Test-Path $propsFile) {
    Get-Content $propsFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2] }
    }
}
$props['enable-rcon'] = 'true'
$props['rcon.port'] = "$rconPort"
$props['rcon.password'] = $rconPass
$props['broadcast-rcon-to-ops'] = 'true'
($props.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) |
    Set-Content -LiteralPath $propsFile -Encoding Ascii

Remove-Item $outFile -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stash | Out-Null
if (Test-Path $mods) {
    Get-ChildItem $mods -Filter '*.jar' | Move-Item -Destination $stash -Force
}
Write-Output 'mods stashed'
try {
    # Paths contain no spaces, so unquoted redirection is safe for cmd.
    $inner = "$gradlew runServerTest --console=plain > $outFile 2>&1"
    $cmdLine = 'cmd /c "cd /d ' + $projectDir + ' & ' + $inner + '"'
    $startup = ([wmiclass]'Win32_ProcessStartup').CreateInstance()
    $startup.ShowWindow = 0
    $created = ([wmiclass]'Win32_Process').Create($cmdLine, $null, $startup)
    if ($created.ReturnValue -ne 0) { throw "WMI spawn failed: $($created.ReturnValue)" }
    Write-Output "launcher pid: $($created.ProcessId)"

    # Wait until the server is up (log says "Done (") and RCON is accepting.
    $bootDeadline = (Get-Date).AddMinutes(6)
    $serverUp = $false
    while ((Get-Date) -lt $bootDeadline) {
        Start-Sleep -Seconds 5
        if (Test-Path $outFile) {
            $raw = Get-Content $outFile -Raw -ErrorAction SilentlyContinue
            if ($raw -match 'Done \(') {
                $tcp = New-Object System.Net.Sockets.TcpClient
                try {
                    $iar = $tcp.BeginConnect('127.0.0.1', $rconPort, $null, $null)
                    if ($iar.AsyncWaitHandle.WaitOne(2000) -and $tcp.Connected) { $serverUp = $true }
                } catch { } finally { $tcp.Close() }
                if ($serverUp) { break }
            }
        }
    }
    if (-not $serverUp) {
        Write-Output 'server did not become ready (no Done/RCON)'
        Get-Content $outFile -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
    } else {
        Write-Output 'server ready, sending trigger over RCON'
        & python $rconCmd --port $rconPort --password $rconPass 'function gs_test:run_all'
        Write-Output "rcon exit: $LASTEXITCODE"
    }

    $deadline = (Get-Date).AddMinutes($(if ($Smoke) { 6 } else { 25 }))
    $done = $false
    $sawGs = $false
    while ($serverUp -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if (Test-Path $outFile) {
            $content = Get-Content $outFile -Raw -ErrorAction SilentlyContinue
            if ($content -match 'GS-TEST|GS-AUTO') {
                $sawGs = $true
                ($content -split "`n" | Select-String 'GS-TEST|GS-AUTO') |
                    ForEach-Object { Write-Output $_.Line.Trim() }
                if ($Smoke) { $done = $true; break }
            }
            if ($content -match 'ALL_DONE') { $done = $true; break }
            if ($content -match 'BUILD FAILED|FATAL') {
                Write-Output '--- failure marker detected ---'
                ($content -split "`n" | Select-String 'FATAL|GS-TEST|Rejected ritual|Exception') |
                    Select-Object -First 20 | ForEach-Object { Write-Output $_.Line.Trim() }
                break
            }
        }
    }
    Write-Output "poll end, done=$done sawGs=$sawGs"
    if (-not $done) {
        Get-Content $outFile -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
    }
    # Stop the server: find the listener on 25565 (skip the Gradle daemon), then
    # wait for handles to release so the next run does not read stale output.
    $deadlineKill = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadlineKill) {
        $conn = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if (-not $conn) { break }
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Output "killed server pid $($conn.OwningProcess)"
        Start-Sleep -Seconds 2
    }
    Start-Sleep -Seconds 3
    # Fallback: kill leftover runServer java (exclude the Gradle daemon).
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'neoforged|fml' -and $_.CommandLine -notmatch 'GradleDaemon' } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Output "killed java $($_.ProcessId)"
        }
    Start-Sleep -Seconds 2
} finally {
    if (Test-Path $stash) {
        Get-ChildItem $stash -Filter '*.jar' | Move-Item -Destination $mods -Force
        Remove-Item $stash -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Output 'mods restored'
}
