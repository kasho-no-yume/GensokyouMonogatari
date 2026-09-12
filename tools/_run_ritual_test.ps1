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
            if ($content -match 'GS-TEST|GS-AUTO') {
                ($content -split "`n" | Select-String 'GS-TEST|GS-AUTO') | ForEach-Object { Write-Output $_.Line.Trim() }
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
    # 停服：找 25565 端口进程终止（跳过 gradle 守护进程），并等待句柄释放，
    # 否则残留 java 进程持有 build_out.txt 句柄 → 下一轮 Remove-Item 静默失败读到旧输出
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
    # 端口法兜底后仍有残留：按命令行特征杀 runServer 派生 java（排除 Gradle 守护进程），
    # 否则残留进程持有 build_out.txt 句柄 → 下一轮读到 stale 输出（实测踩过两次）
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'neoforged|fml' -and $_.CommandLine -notmatch 'GradleDaemon' } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Output "killed java $($_.ProcessId)"
        }
    Start-Sleep -Seconds 2
} finally {
    Get-ChildItem $stash -Filter '*.jar' | Move-Item -Destination $mods -Force
    Remove-Item $stash -Force
    Write-Output 'mods restored'
}
