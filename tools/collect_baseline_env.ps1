<#
.SYNOPSIS
    Snapshots the environment record the danmaku performance baseline must carry.

.DESCRIPTION
    The sampling guide requires every sample to be joined with: git commit + uncommitted
    diff, JDK, OS, CPU/GPU/RAM, GPU driver, resolution, view/simulation distance, FPS cap,
    VSync, server compression threshold, the actual config, player count, world seed, and
    the recorded stance. Collecting that by hand is how the first run ended up with none
    of it. This script gathers everything a machine can know and writes it as JSON next to
    the evidence dir, so only stance/view/player-count have to be typed by hand.

    Anything it cannot determine is written as null rather than guessed.

    This file is intentionally ASCII-only: PowerShell 5.1 reads BOM-less UTF-8 using the
    system ANSI code page, which corrupts multi-byte characters and derails the parser.
    The JSON it writes IS UTF-8 (written via UTF8Encoding($false)) so that non-ASCII OS
    captions or paths survive.

.EXAMPLE
    .\tools\collect_baseline_env.ps1
    Writes build/danmaku-baseline/env-record.json

.EXAMPLE
    .\tools\collect_baseline_env.ps1 -Out build/danmaku-baseline/flower_r1/env-record.json -Notes "stance: 10 64 20 facing +X"
#>
[CmdletBinding()]
param(
    [string]$Out,
    [string]$ServerGameDir = 'run-test',
    [string]$ClientGameDir = 'run',
    # Free text for the parts no machine can know (stance, view, movement rule).
    [string]$Notes = ''
)

$ErrorActionPreference = 'Continue'

$projectDir = Split-Path -Parent $PSScriptRoot
if (-not $Out) { $Out = Join-Path $projectDir 'build\danmaku-baseline\env-record.json' }
if (-not [IO.Path]::IsPathRooted($Out)) { $Out = Join-Path $projectDir $Out }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null

function First-Line { param($Value) return (("$Value" -split "`r?`n")[0]).Trim() }
function NullIfBlank { param($Value) if ([string]::IsNullOrWhiteSpace("$Value")) { return $null } else { return "$Value" } }

function Read-Properties {
    param([string]$Path, [string[]]$Keys)
    $result = [ordered]@{}
    foreach ($k in $Keys) { $result[$k] = $null }
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    # server.properties uses '=', client options.txt uses ':'. Accept both.
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#=:]+)\s*[=:](.*)$') {
            $name = $Matches[1].Trim()
            if ($Keys -contains $name) { $result[$name] = $Matches[2] }
        }
    }
    return $result
}

# ---- git ----
$gitCommit = $null
$gitPorcelainCount = $null
$gitPorcelainSha256 = $null
$gitDiffStat = $null
Push-Location $projectDir
try {
    $gitCommit = NullIfBlank (First-Line (git rev-parse HEAD 2>$null))
    $porcelain = (git status --porcelain 2>$null) -join "`n"
    if ($porcelain) {
        $gitPorcelainCount = ($porcelain -split "`n").Count
        # The repo tracks logs/ and its rotation rewrites files on every run, so a raw
        # porcelain fingerprint changes when NOTHING about the code did. Fingerprint only
        # the lines that describe source/build state, and keep the raw count separately.
        $codeLines = @($porcelain -split "`n" | Where-Object { $_ -notmatch '^\s*\S+\s+logs/' })
        $gitCodeChangeCount = $codeLines.Count
        $sha = [Security.Cryptography.SHA256]::Create()
        $bytes = [Text.Encoding]::UTF8.GetBytes(($codeLines -join "`n"))
        $gitPorcelainSha256 = ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
    } else {
        $gitPorcelainCount = 0
        $gitCodeChangeCount = 0
    }
    $gitDiffStat = NullIfBlank (First-Line ((git diff --stat 2>$null) | Select-Object -Last 1))
} finally {
    Pop-Location
}

# ---- toolchain / versions ----
$gradleProps = Join-Path $projectDir 'gradle.properties'
$props = [ordered]@{}
if (Test-Path -LiteralPath $gradleProps) {
    foreach ($line in Get-Content -LiteralPath $gradleProps) {
        if ($line -match '^\s*([^#=]+)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2] }
    }
}
$javaHome = $props['org.gradle.java.home']
$javaVersion = $null
if ($javaHome) {
    $exe = Join-Path $javaHome 'bin\java.exe'
    if (Test-Path -LiteralPath $exe) { $javaVersion = NullIfBlank (First-Line (& $exe -version 2>&1)) }
}
if (-not $javaVersion) {
    $javaVersion = NullIfBlank (First-Line ((& java -version 2>&1) | Select-Object -First 1))
}

# ---- machine ----
$cpu = Get-CimInstance Win32_Processor -ErrorAction SilentlyContinue | Select-Object -First 1
$os = Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue | Select-Object -First 1
$gpus = @()
Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue | ForEach-Object {
    $res = $null
    if ($_.CurrentHorizontalResolution -and $_.CurrentVerticalResolution) {
        $res = "{0}x{1}" -f $_.CurrentHorizontalResolution, $_.CurrentVerticalResolution
    }
    $gpus += [ordered]@{
        name          = $_.Name
        driverVersion = $_.DriverVersion
        resolution    = $res
        refreshHz     = $_.CurrentRefreshRate
    }
}

# ---- server / client settings ----
$serverProps = Read-Properties -Path (Join-Path $projectDir "$ServerGameDir\server.properties") -Keys @(
    'view-distance', 'simulation-distance', 'network-compression-threshold', 'difficulty',
    'gamemode', 'online-mode', 'op-permission-level', 'max-players', 'server-port',
    'enable-rcon', 'rcon.port', 'level-name', 'level-seed', 'pvp', 'spawn-protection'
)
$clientOptions = Read-Properties -Path (Join-Path $projectDir "$ClientGameDir\options.txt") -Keys @(
    'renderDistance', 'simulationDistance', 'maxFps', 'enableVsync', 'graphicsMode',
    'particles', 'guiScale', 'fullscreen', 'ao', 'entityShadows', 'entityDistanceScaling',
    'biomeBlendRadius', 'mipmapLevels'
)

# ---- mod config values that shape the load ----
$cap = $null
$configPath = Join-Path $projectDir "$ServerGameDir\config\gensokyou-common.toml"
if (Test-Path -LiteralPath $configPath) {
    $hit = Select-String -LiteralPath $configPath -Pattern '^\s*danmakuEntityCap\s*=\s*(\d+)' |
        Select-Object -First 1
    if ($hit) { $cap = [int]$hit.Matches[0].Groups[1].Value }
}

# ---- world seed (level.dat is gzip+NBT; parse it with python) ----
$seed = $null
$levelDat = Join-Path $projectDir "$ServerGameDir\world\level.dat"
if (Test-Path -LiteralPath $levelDat) {
    $py = @'
import gzip, struct, sys
try:
    d = gzip.open(sys.argv[1], 'rb').read()
except Exception:
    sys.exit(0)
i = d.find(b'\x04\x00\x04seed')
if i < 0:
    sys.exit(0)
# [tag type 1][name len 2]["seed" 4] then the long payload
sys.stdout.write(str(struct.unpack('>q', d[i + 7:i + 15])[0]))
'@
    $pyFile = Join-Path $env:TEMP 'gs_read_seed.py'
    [IO.File]::WriteAllText($pyFile, $py, (New-Object Text.UTF8Encoding($false)))
    $seedOut = & python $pyFile $levelDat 2>$null
    if ($seedOut) { $seed = NullIfBlank ("$seedOut".Trim()) }
}

# ---- mod artifacts actually on the run classpath (dev: no jar) ----
$jarSha = $null
$libs = Join-Path $projectDir 'build\libs'
if (Test-Path -LiteralPath $libs) {
    $jars = @(Get-ChildItem -LiteralPath $libs -Filter '*.jar' -ErrorAction SilentlyContinue)
    if ($jars.Count -gt 0) {
        $jarSha = @{}
        foreach ($j in $jars) {
            $jarSha[$j.Name] = (Get-FileHash -LiteralPath $j.FullName -Algorithm SHA256).Hash
        }
    }
}

$record = New-Object psobject -Property ([ordered]@{
    recordedAt          = (Get-Date).ToString('o')
    host                = $env:COMPUTERNAME
    notes               = NullIfBlank $Notes
    gitCommit           = $gitCommit
    gitPorcelainCount   = $gitPorcelainCount
    gitCodeChangeCount  = $gitCodeChangeCount
    gitPorcelainSha256  = $gitPorcelainSha256
    gitPorcelainSha256Note = 'hash covers porcelain lines EXCLUDING logs/ (rotation churn)'
    gitDiffStat         = $gitDiffStat
    devEnvironment      = $true
    # Dev runs load build/classes + build/resources through the FML modFolders argument,
    # NOT build/libs/*.jar. Anything in build/libs may be stale, so it is named for what
    # it is instead of being presented as "the artifact under test".
    devRunClasspath     = 'build/classes/java/main + build/resources/main (fml.modFolders)'
    buildLibsJarSha256  = $jarSha
    javaHome            = NullIfBlank $javaHome
    javaVersion         = $javaVersion
    minecraftVersion    = NullIfBlank $props['minecraft_version']
    neoForgeVersion     = NullIfBlank $props['neo_version']
    parchmentMappings   = NullIfBlank $props['parchment_mappings_version']
    os                  = [ordered]@{ caption = $os.Caption; version = $os.Version; build = $os.BuildNumber }
    cpu                 = [ordered]@{
        name      = $cpu.Name
        cores     = $cpu.NumberOfCores
        threads   = $cpu.NumberOfLogicalProcessors
        maxMhz    = $cpu.MaxClockSpeed
    }
    memoryKb            = $os.TotalVisibleMemorySize
    gpus                = $gpus
    danmakuEntityCap    = $cap
    serverProperties    = $serverProps
    clientOptions       = $clientOptions
    worldSeed           = $seed
})

$json = $record | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText($Out, $json, (New-Object Text.UTF8Encoding($false)))
Write-Host "[env] wrote $Out"
Write-Host "[env] commit=$gitCommit dirty=$gitPorcelainCount cap=$cap seed=$seed"
Write-Host "[env] NOTE: stance / view / movement rule / who is the renderer are NOT machine-readable."
Write-Host "[env]       Put them in -Notes or in the sample's STATUS file."
