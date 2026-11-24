<#
.SYNOPSIS
    Delete only the generated Gensokyo dimension chunks so they regenerate on next launch.

.DESCRIPTION
    This is an OFFLINE tool. Stop the server before running it. It never touches the
    overworld or other dimensions, and it does not modify the data-pack dimension JSON.
    On the next start the level stem from data/gensokyou/dimension/gensokyo.json is
    recreated and chunks are generated again from the world seed.

    By default this script only prints what it would do (dry run). Pass -Confirm to
    actually delete. A session.lock in the world root blocks the operation unless
    -Force is given (only use -Force when you are certain no server owns the world).

.EXAMPLE
    .\tools\regenerate_gensokyo.ps1
    .\tools\regenerate_gensokyo.ps1 -GameDir run -WorldName world -Backup -Confirm
    .\tools\regenerate_gensokyo.ps1 -GameDir build\gensokyo-worldgen-probe -Seed 424242 -Backup -Confirm
#>
[CmdletBinding()]
param(
    [string]$GameDir = 'run',
    [string]$WorldName = 'world',
    [long]$Seed = 0,
    [switch]$Backup,
    [switch]$ResetData,
    [switch]$Force,
    [switch]$Confirm
)

$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
$gamePath = if ([System.IO.Path]::IsPathRooted($GameDir)) { $GameDir } else { Join-Path $projectDir $GameDir }
$worldPath = Join-Path $gamePath $WorldName
$dimPath = Join-Path $worldPath 'dimensions\gensokyou\gensokyo'
$propsPath = Join-Path $gamePath 'server.properties'
$lockPath = Join-Path $worldPath 'session.lock'

if (-not (Test-Path -LiteralPath $worldPath -PathType Container)) {
    throw "World directory not found: $worldPath"
}

$targets = @('region', 'entities', 'poi')
if ($ResetData) { $targets += 'data' }

$existing = @()
foreach ($name in $targets) {
    $p = Join-Path $dimPath $name
    if (Test-Path -LiteralPath $p) { $existing += $name }
}

if ($existing.Count -eq 0) {
    Write-Host "GENSOKYO_REGEN SKIP reason=nothing_to_delete path=$dimPath"
    return
}

Write-Host "GENSOKYO_REGEN PLAN world=$worldPath dimension=gensokyou:gensokyo"
Write-Host "  delete: $($existing -join ', ')"
if ($Backup) { Write-Host "  backup: build/gensokyo-backups/<timestamp>/gensokyo" }
if ($Seed -ne 0) { Write-Host "  level-seed: $Seed" }
if (-not $Confirm) {
    Write-Host "GENSOKYO_REGEN DRY_RUN pass -Confirm to apply"
    return
}

if (Test-Path -LiteralPath $lockPath) {
    if (-not $Force) {
        throw "session.lock exists at $lockPath - stop the server first, or pass -Force if no server owns this world."
    }
    Write-Warning "session.lock present but -Force was given; continuing."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $projectDir 'build\agent-logs'
if (-not (Test-Path -LiteralPath $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

if ($Backup) {
    $backupRoot = Join-Path $projectDir 'build\gensokyo-backups'
    $backupPath = Join-Path $backupRoot "$stamp\gensokyo"
    New-Item -ItemType Directory -Path $backupPath -Force | Out-Null
    foreach ($name in $existing) {
        Copy-Item -LiteralPath (Join-Path $dimPath $name) -Destination $backupPath -Recurse -Force
    }
    Write-Host "  backup written: $backupPath"
}

foreach ($name in $existing) {
    Remove-Item -LiteralPath (Join-Path $dimPath $name) -Recurse -Force
}

$seedApplied = 'no'
if ($Seed -ne 0) {
    if (-not (Test-Path -LiteralPath $propsPath)) {
        throw "server.properties not found at $propsPath (needed to set level-seed)."
    }
    $lines = @(Get-Content -LiteralPath $propsPath -Encoding Ascii)
    $found = $false
    $out = @()
    foreach ($line in $lines) {
        if ($line -match '^\s*level-seed\s*=') {
            $out += "level-seed=$Seed"
            $found = $true
        } else {
            $out += $line
        }
    }
    if (-not $found) { $out += "level-seed=$Seed" }
    Set-Content -LiteralPath $propsPath -Value $out -Encoding Ascii
    $seedApplied = 'yes'
}

$logFile = Join-Path $logDir "$stamp-regenerate_gensokyo.log"
Set-Content -LiteralPath $logFile -Value "world=$worldPath`ndeleted=$($existing -join ',')`nseed=$seedApplied" -Encoding Ascii

Write-Host "GENSOKYO_REGEN PASS gameDir=$GameDir world=$WorldName deleted=$($existing -join '|') seed=$seedApplied backup=$($Backup.IsPresent) log=$logFile"
Write-Host "NOTE: maps already handed to players may keep the old base image; use a fresh debug map (Java-side LAYOUT_VERSION bump is a separate change)."
