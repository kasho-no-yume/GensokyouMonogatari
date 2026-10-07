<#
.SYNOPSIS
    Build the mod jar and launch a dedicated NeoForge server from it (GUI mode).

.DESCRIPTION
    1. Run tools/gradle_task.ps1 build (incremental, always).
    2. Copy build/libs/gensokyou-<version>.jar into server/mods/.
    3. Ensure Patchouli + GeckoLib jars exist in server/mods/
       (Gradle cache first, maven download as fallback, proxy aware).
    4. On first run (server/libraries missing), download the neoforge installer
       and run --installServer into server/.
    5. Write server/eula.txt (eula=true) if missing.
    6. Spawn the server detached via WMI (SW_HIDE), GUI mode (no --nogui),
       and return immediately.

    This file is intentionally ASCII-only: PowerShell 5.1 reads BOM-less UTF-8
    as the system ANSI codepage, corrupting multi-byte characters.

.EXAMPLE
    .\tools\run_server_jar.ps1

.EXAMPLE
    .\tools\run_server_jar.ps1 nogui
#>
[CmdletBinding()]
param(
    [string]$ProxyHost = '127.0.0.1',
    [int]$ProxyPort = 7897,
    [switch]$NoProxy,

    # ---- Optional client<->server latency injection ----
    # Base delay in ms; 0 = disabled (no proxy started).
    [int]$LatencyMs = 200,
    # Per-chunk jitter in +- ms.
    [int]$JitterMs = 50,
    # Port the jitter proxy listens on (clients connect here).
    [int]$LagPort = 25566,
    # Delay only the server->client direction.
    [switch]$LagDownstreamOnly,
    # Stop any existing jitter proxy and exit (no build, no launch).
    [switch]$LagOff,
    # Hot-update a running proxy's parameters (no build, no launch).
    [switch]$SetLag,

    # Extra args passed through to the server (e.g. -ServerArg nogui).
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ServerArg
)

$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
$serverDir  = Join-Path $projectDir 'server'
$modsDir    = Join-Path $serverDir 'mods'
$propsPath  = Join-Path $projectDir 'gradle.properties'

function Read-GradleProperty {
    param([string]$Key)
    $line = Get-Content $propsPath | Where-Object { $_ -match "^\s*$Key\s*=" } | Select-Object -First 1
    if (-not $line) { throw "Property $Key not found in gradle.properties" }
    return ($line -split '=', 2)[1].Trim()
}

$minecraftVersion = Read-GradleProperty 'minecraft_version'
$neoVersion       = Read-GradleProperty 'neo_version'
$modVersion       = Read-GradleProperty 'mod_version'
$patchouliVersion = Read-GradleProperty 'patchouli_version'
$geckoVersion     = Read-GradleProperty 'geckolib_version'

$javaHome = (Read-GradleProperty 'org.gradle.java.home') -replace '/', '\'
$javaExe  = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $javaExe)) { throw "java.exe not found at $javaExe" }

# ---- Dependency descriptors ----
# Each entry: gradle-cache artifact path parts, expected jar file name, maven URL.
$deps = @(
    @{
        Group    = 'vazkii.patchouli'
        Artifact = 'Patchouli'
        Version  = $patchouliVersion
        JarName  = "Patchouli-$patchouliVersion.jar"
        Url      = "https://maven.blamejared.com/vazkii/patchouli/Patchouli/$patchouliVersion/Patchouli-$patchouliVersion.jar"
    },
    @{
        Group    = 'software.bernie.geckolib'
        Artifact = "geckolib-neoforge-$minecraftVersion"
        Version  = $geckoVersion
        JarName  = "geckolib-neoforge-$minecraftVersion-$geckoVersion.jar"
        Url      = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-neoforge-$minecraftVersion/$geckoVersion/geckolib-neoforge-$minecraftVersion-$geckoVersion.jar"
    }
)

function Get-ProxyArgs {
    if ($NoProxy) { return @() }
    return @(
        "-Dhttp.proxyHost=$ProxyHost",
        "-Dhttp.proxyPort=$ProxyPort",
        "-Dhttps.proxyHost=$ProxyHost",
        "-Dhttps.proxyPort=$ProxyPort"
    )
}

function Download-File {
    param([string]$Url, [string]$Dest)
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
        return
    } catch {
        if ($NoProxy) { throw "Download failed: $Url ($_)" }
        Write-Host "[deps] direct failed, retrying via proxy ${ProxyHost}:${ProxyPort}"
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing -Proxy "http://${ProxyHost}:${ProxyPort}"
    }
}

# Kill whatever listens on the given port (the jitter proxy, if any).
function Stop-LagProxy {
    param([int]$Port)
    $pids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pids) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "[lag] stopped proxy pid $procId on port $Port"
    }
}

# ---- -LagOff: stop the proxy and exit (no build, no launch) ----
if ($LagOff) {
    Stop-LagProxy -Port $LagPort
    Write-Host "[lag] port $LagPort is now free"
    return
}

# ---- -SetLag: hot-update the running proxy via the control file ----
if ($SetLag) {
    $proxyUp = Get-NetTCPConnection -LocalPort $LagPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $proxyUp) {
        throw "no lag proxy listening on port $LagPort; start one with -LatencyMs/-JitterMs first"
    }
    $controlPath = Join-Path $serverDir 'lag_control.json'
    $cfg = @{ latency = $LatencyMs; jitter = $JitterMs; downstream_only = [bool]$LagDownstreamOnly }
    if (Test-Path $controlPath) {
        try {
            $existing = Get-Content $controlPath -Raw | ConvertFrom-Json
            foreach ($p in $existing.PSObject.Properties) { $cfg[$p.Name] = $p.Value }
        } catch {
            Write-Warning "[lag] existing control file unreadable; rewriting"
        }
    }
    if ($PSBoundParameters.ContainsKey('LatencyMs')) { $cfg['latency'] = $LatencyMs }
    if ($PSBoundParameters.ContainsKey('JitterMs')) { $cfg['jitter'] = $JitterMs }
    if ($PSBoundParameters.ContainsKey('LagDownstreamOnly')) { $cfg['downstream_only'] = [bool]$LagDownstreamOnly }
    ($cfg | ConvertTo-Json -Compress) | Set-Content -LiteralPath $controlPath -Encoding Ascii
    Write-Host "[lag] updated ${controlPath}: latency=$($cfg['latency'])ms jitter=+-$($cfg['jitter'])ms downstream_only=$($cfg['downstream_only'])"
    return
}

# ---- Step 1: incremental build ----
Write-Host '[build] running gradle build (incremental)...'
& (Join-Path $projectDir 'tools\gradle_task.ps1') build
if ($LASTEXITCODE -ne 0) { throw "gradle build failed with exit $LASTEXITCODE" }

# ---- Step 2: stage mod jar ----
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
$jarPath = Join-Path $projectDir "build\libs\gensokyou-$modVersion.jar"
if (-not (Test-Path $jarPath)) { throw "Built jar not found at $jarPath" }
Copy-Item $jarPath (Join-Path $modsDir (Split-Path $jarPath -Leaf)) -Force
Write-Host "[stage] copied $(Split-Path $jarPath -Leaf) into server/mods"

# ---- Step 3: dependency jars ----
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
foreach ($dep in $deps) {
    $dest = Join-Path $modsDir $dep.JarName
    if (Test-Path $dest) { Write-Host "[deps] present: $($dep.JarName)"; continue }

    $cacheDir = Join-Path $cacheRoot "$($dep.Group)\$($dep.Artifact)\$($dep.Version)"
    $cached = $null
    if (Test-Path $cacheDir) {
        $cached = Get-ChildItem $cacheDir -Recurse -File -Filter $dep.JarName | Select-Object -First 1
    }
    if ($cached) {
        Copy-Item $cached.FullName $dest -Force
        Write-Host "[deps] copied from cache: $($dep.JarName)"
    } else {
        Write-Host "[deps] downloading: $($dep.Url)"
        Download-File -Url $dep.Url -Dest $dest
        Write-Host "[deps] downloaded: $($dep.JarName)"
    }
}

# ---- Step 4: first-run install ----
$neoforgeLib = Join-Path $serverDir "libraries\net\neoforged\neoforge\$neoVersion"
if (-not (Test-Path $neoforgeLib)) {
    Write-Host '[install] server not found, installing...'
    New-Item -ItemType Directory -Force -Path $serverDir | Out-Null
    $installerPath = Join-Path $serverDir "neoforge-$neoVersion-installer.jar"
    $installerUrl  = "https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/$neoVersion/neoforge-$neoVersion-installer.jar"
    try {
        Download-File -Url $installerUrl -Dest $installerPath
    } catch {
        $installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoVersion/neoforge-$neoVersion-installer.jar"
        Download-File -Url $installerUrl -Dest $installerPath
    }
    $proxyArgs = Get-ProxyArgs
    Push-Location $serverDir
    try {
        & $javaExe @proxyArgs -jar $installerPath --installServer
        if ($LASTEXITCODE -ne 0) { throw "installer failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    Remove-Item $installerPath -ErrorAction SilentlyContinue
}

# ---- Step 4b: eula ----
$eulaPath = Join-Path $serverDir 'eula.txt'
if (-not (Test-Path $eulaPath)) {
    Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding Ascii
    Write-Host '[eula] wrote server/eula.txt'
}

# Port conflict warning (informational only).
$portBusy = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($portBusy) {
    Write-Warning '[port] 25565 is already listening; the new server will fail to bind unless it is free.'
}

# ---- Step 5: WMI spawn, detached, GUI server (no --nogui) ----
$argsFile = "libraries/net/neoforged/neoforge/$neoVersion/win_args.txt"
$extraArgs = ''
if ($ServerArg -and $ServerArg.Count -gt 0) {
    $extraArgs = ' ' + ($ServerArg -join ' ')
}
# NOTE: Minecraft's log4j writes its own logs/latest.log inside the server dir.
# Do NOT redirect our stdout to that same path (file lock conflict); use a
# separate stdout capture file instead.
$logFile = Join-Path $serverDir 'server_stdout.log'

$quotedJava = '"{0}"' -f $javaExe
$quotedLog  = '"{0}"' -f $logFile
$inner = 'cmd /v:on /c "cd /d ""' + $serverDir + '"" & ' + $quotedJava + ' @user_jvm_args.txt @' + $argsFile + $extraArgs + ' > ' + $quotedLog + ' 2>&1"'
$startup = ([wmiclass]'Win32_ProcessStartup').CreateInstance()
$startup.ShowWindow = 0
$created = ([wmiclass]'Win32_Process').Create($inner, $serverDir, $startup)
if ($created.ReturnValue -ne 0) { throw "WMI spawn failed: $($created.ReturnValue)" }

Write-Host "[launch] server pid: $($created.ProcessId)"
Write-Host "[launch] log: $logFile"
Write-Host "[launch] detached; follow with: Get-Content -Wait '$logFile'"

# ---- Step 6: optional client<->server latency injection ----
if ($LatencyMs -gt 0 -or $JitterMs -gt 0) {
    # Idempotent: replace any existing proxy on the lag port.
    Stop-LagProxy -Port $LagPort

    $pythonExe = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $pythonExe) { throw "python not found on PATH; required for latency injection" }
    $proxyScript = Join-Path $projectDir 'tools\net_jitter_proxy.py'
    if (-not (Test-Path $proxyScript)) { throw "Proxy script not found at $proxyScript" }

    # Control file is the runtime source of truth; write the initial values.
    $controlPath = Join-Path $serverDir 'lag_control.json'
    @{
        latency         = $LatencyMs
        jitter          = $JitterMs
        downstream_only = [bool]$LagDownstreamOnly
    } | ConvertTo-Json -Compress | Set-Content -LiteralPath $controlPath -Encoding Ascii

    $lagArgs = "--listen $LagPort --target 25565 --latency $LatencyMs --jitter $JitterMs --control $controlPath"
    if ($LagDownstreamOnly) { $lagArgs += ' --downstream-only' }

    $lagLog = Join-Path $serverDir 'lag_proxy.log'
    $quotedPy     = '"{0}"' -f $pythonExe
    $quotedScript = '"{0}"' -f $proxyScript
    $quotedLagLog = '"{0}"' -f $lagLog
    $lagInner = 'cmd /v:on /c "cd /d ""' + $projectDir + '"" & ' + $quotedPy + ' ' + $quotedScript + ' ' + $lagArgs + ' > ' + $quotedLagLog + ' 2>&1"'
    $lagStartup = ([wmiclass]'Win32_ProcessStartup').CreateInstance()
    $lagStartup.ShowWindow = 0
    $lagCreated = ([wmiclass]'Win32_Process').Create($lagInner, $projectDir, $lagStartup)
    if ($lagCreated.ReturnValue -ne 0) { throw "WMI spawn of lag proxy failed: $($lagCreated.ReturnValue)" }

    Start-Sleep -Seconds 1
    $lagUp = Get-NetTCPConnection -LocalPort $LagPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($lagUp) {
        Write-Host "[lag] proxy pid: $($lagCreated.ProcessId) (port $LagPort -> 25565)"
        Write-Host "[lag] connect your client to 127.0.0.1:$LagPort to get ${LatencyMs}ms +/-${JitterMs}ms"
        Write-Host "[lag] hot-update later: .\tools\run_server_jar.ps1 -SetLag -LatencyMs <n> -JitterMs <n>"
    } else {
        Write-Warning "[lag] proxy did not come up on port $LagPort; see $lagLog"
        if (Test-Path $lagLog) { Get-Content $lagLog -Tail 10 | ForEach-Object { Write-Host "  $_" } }
    }
} elseif ($LagPort -and (Get-NetTCPConnection -LocalPort $LagPort -State Listen -ErrorAction SilentlyContinue)) {
    Write-Host "[lag] note: a proxy is listening on port $LagPort (use -LagOff to stop it)"
}
