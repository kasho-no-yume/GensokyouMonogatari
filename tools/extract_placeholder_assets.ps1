param(
    [string]$ClientJar = ""
)

$ErrorActionPreference = "Stop"

if (-not $ClientJar) {
    $candidate = Get-ChildItem "$env:USERPROFILE\.gradle\caches\neoformruntime\artifacts" -Filter "minecraft_*_client.jar" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($candidate) { $ClientJar = $candidate.FullName }
}
if (-not $ClientJar -or -not (Test-Path $ClientJar)) {
    Write-Host "ERROR: vanilla client jar not found. Pass -ClientJar <path> manually."
    exit 1
}

$assets = "D:\code\Gensokyou\src\main\resources\assets\gensokyou"
$heartTargets = @("guide_book","ppoint","bpoint",
    "spellcard_star","broken_spell_card_star","yen")
$weaponTargets = @("laevatein")
$armorTargets = @()
$spellcardTargets = @("musou_fuuin","light_reflect")
$blockTargets = @("ritual_core","ritual_stone")
$entityMap = @{
    "flandre"     = "assets/minecraft/textures/entity/player/slim/alex.png"
    "fairy"       = "assets/minecraft/textures/entity/player/slim/alex.png"
    "big_fairy"   = "assets/minecraft/textures/entity/player/slim/alex.png"
    "danmaku"     = "LEGACY_LIGHTORB"
    "orbit_orb"   = "LEGACY_LIGHTORB"
}
$legacyLightorbUrl = "https://raw.githubusercontent.com/kasho-no-yume/Gensokyou/master/src/main/resources/assets/gensokyou/textures/items/lightorb.png"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($ClientJar)
try {
    function Copy-Entry([string]$entryName, [string]$destPath) {
        $entry = $zip.GetEntry($entryName)
        if ($null -eq $entry) {
            Write-Host "MISSING entry: $entryName"
            return $false
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $destPath) | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
        return $true
    }

    foreach ($name in $heartTargets) {
        Copy-Entry "assets/minecraft/textures/item/heart_of_the_sea.png" "$assets\textures\item\$name.png" | Out-Null
    }
    foreach ($name in $weaponTargets) {
        Copy-Entry "assets/minecraft/textures/item/trident.png" "$assets\textures\item\$name.png" | Out-Null
    }
    foreach ($name in $armorTargets) {
        Copy-Entry "assets/minecraft/textures/item/netherite_chestplate.png" "$assets\textures\item\$name.png" | Out-Null
    }
    foreach ($name in $spellcardTargets) {
        Copy-Entry "assets/minecraft/textures/item/flower_banner_pattern.png" "$assets\textures\item\$name.png" | Out-Null
    }
    foreach ($name in $blockTargets) {
        Copy-Entry "assets/minecraft/textures/block/diamond_block.png" "$assets\textures\block\$name.png" | Out-Null
    }
    foreach ($name in $entityMap.Keys) {
        if ($entityMap[$name] -eq "LEGACY_LIGHTORB") {
            New-Item -ItemType Directory -Force -Path "$assets\textures\entity" | Out-Null
            Invoke-WebRequest -Uri $legacyLightorbUrl -OutFile "$assets\textures\entity\$name.png" -ErrorAction Stop
        } else {
            Copy-Entry $entityMap[$name] "$assets\textures\entity\$name.png" | Out-Null
        }
    }
} finally {
    $zip.Dispose()
}
Write-Host "Placeholder assets extracted from: $ClientJar"
