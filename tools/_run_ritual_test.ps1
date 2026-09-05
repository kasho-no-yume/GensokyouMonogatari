$ErrorActionPreference = 'Continue'
Set-Location 'D:\code\Gensokyou'
$mods = 'D:\code\Gensokyou\run\mods'
$stash = 'D:\code\Gensokyou\tools\_mod_stash'
Remove-Item build_out.txt -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stash | Out-Null
Get-ChildItem $mods -Filter '*.jar' | Move-Item -Destination $stash -Force
Write-Output 'mods stashed'
try {
    $proc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList '/c gradlew.bat runServer --console=plain < run\stdin.txt > build_out.txt 2>&1' `
        -WorkingDirectory 'D:\code\Gensokyou' -PassThru -WindowStyle Hidden
    Write-Output "launcher pid: $($proc.Id)"
    $deadline = (Get-Date).AddMinutes(9)
    $done = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if (Test-Path build_out.txt) {
            $content = Get-Content build_out.txt -Raw -ErrorAction SilentlyContinue
            if ($content -match 'GS-TEST') {
                ($content -split "`n" | Select-String 'GS-TEST') | ForEach-Object { Write-Output $_.Line.Trim() }
            }
            if ($content -match 'ALL_DONE') { $done = $true; break }
            if ($content -match 'BUILD FAILED|FATAL') {
                Write-Output '--- failure marker detected ---'
                ($content -split "`n" | Select-String 'FATAL|GS-TEST|Rejected ritual|Exception') |
                    Select-Object -First 20 | ForEach-Object { Write-Output $_.Line.Trim() }
                break
            }
        }
        if ($proc.HasExited) { Write-Output 'launcher exited'; break }
    }
    Write-Output "poll end, done=$done"
    if (-not $done) {
        Get-Content build_out.txt -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { Write-Output $_ }
    }
    # 停服：找 25565 端口进程终止（跳过 gradle 守护进程）
    $conn = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) {
        Stop-Process -Id $conn.OwningProcess -Force
        Write-Output "killed server pid $($conn.OwningProcess)"
        Start-Sleep -Seconds 3
    }
} finally {
    Get-ChildItem $stash -Filter '*.jar' | Move-Item -Destination $mods -Force
    Remove-Item $stash -Force
    Write-Output 'mods restored'
}
