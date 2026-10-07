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
