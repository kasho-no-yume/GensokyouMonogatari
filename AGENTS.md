# Gensokyou — Agent 工作约定

## 构建 / 运行：必须走 wrapper，禁止裸跑 gradlew

**硬性规则：任何 Gradle 调用都要用 `tools/gradle_task.ps1`，不要直接敲 `gradlew`。**

```powershell
.\tools\gradle_task.ps1 build                  # 编译 + 测试，实时输出，退出码透传
.\tools\gradle_task.ps1 compileJava            # 只编译
.\tools\gradle_task.ps1 runData                # 数据生成
.\tools\gradle_task.ps1 runServer -TimeoutSec 180   # 服务器：最多等 3 分钟就返回
.\tools\gradle_task.ps1 runClient -NoWait      # 客户端：丢后台，立刻返回

# 只看关心的行（-Filter 只过滤"显示"，绝不影响构建本身）
.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'
.\tools\gradle_task.ps1 compileJava --rerun-tasks      # gradle 自己的开关直接跟在后面
```

### 三种失败模式（都不叫"构建慢"，别往那边想）

| # | 写法 | 后果 |
|---|---|---|
| 1 | 裸跑 `gradlew`，且任务/子进程不退出 | **永不返回**。TUI 输出正常但 spinner 死转，只能手动杀会话 |
| 2 | `\| Out-String` | **全程零输出**。缓冲到命令结束才一次性吐出，界面看着像死了 |
| 3 | `\| Select-Object -First N` | **腰斩上游**。凑满 N 行就杀掉 gradle，构建没跑完却以为跑完了 |

第 3 种最阴险——它不报错、不卡死，只是**静默地给你一个截断的构建结果**。
实测 `ping -n 8 | Select-Object -First 2` 在 0.36 秒返回，且 `ping.exe` 被杀掉。

`2>&1` 本身无害，可以照用。

### 为什么不能裸跑

opencode 的 shell 工具以「stdout 管道到达 EOF」作为调用返回条件，**不是**「命令进程退出」。
任何继承 stdout/stderr 句柄的后代进程都会让管道永远等不到 EOF。表现极具迷惑性：

> TUI 输出一切正常、看起来在跑，但工具调用永不返回，spinner 一直转，只能手动终止会话。

`runClient` / `runServer` / `runGameTestServer` 拉起的 Minecraft JVM 正好继承这些句柄，是主要元凶。
本项目 `build` 本身很快（暖构建 2 秒、冷全量 20 秒内），**不是**超时问题——不要用「构建慢」解释这个现象。

### wrapper 做了什么

1. 通过 **WMI 服务**（`Win32_Process.Create`）创建进程，父进程是 `WmiPrvSE.exe` 而非当前 shell，
   因此**完全不继承 agent 的管道句柄**，管道可以正常 EOF。
2. 用 `Win32_ProcessStartup.ShowWindow = 0`（SW_HIDE）启动，**不会弹出黑色控制台窗口**。
3. cmd 在构建结束时把退出码写入 `.exit` 哨兵文件。
4. 脚本轮询哨兵并 tail 日志，所以调用方仍能实时看到输出，且哨兵一出现就退出。

超时（exit 124）时构建**继续在后台跑**，不会被杀；日志路径会打印出来。

注意：`runClient` 打开的游戏窗口（GLFW）是正常的，不是 bug。

### WMI 绑定的坑

`ShowWindow` 必须走**传统 `[wmiclass]` API** 才绑得上，其他写法都失败：

| 写法 | 结果 |
|---|---|
| `([wmiclass]'Win32_Process').Create($cl, $null, $startup)` | 正常 |
| `Invoke-CimMethod ... @{ ProcessStartupInformation = $startup }` | `0x80041005` 类型不匹配 |
| `New-CimInstance Win32_ProcessStartup -ClientOnly` | 实例能建，但传不进去 |
| `New-CimInstance ... -ClientOnly` 后直接 `$s.ShowWindow = 0` | 属性不存在，赋值抛异常 |

### 绝对不要用的写法

| 写法 | 为什么坏 |
|---|---|
| `cmd /c start /b ...` | `start /b` 继承句柄——问题本身 |
| `Start-Process -WindowStyle Hidden` | **实测同样继承句柄，照样卡死**（别想当然） |
| `wscript.exe` + `.vbs` 中转 | WMI 环境下 WSH 被拒（"拒绝访问"），不可用 |
| `... \| Select-Object -Last N` | 吞掉全部流式输出，命令跑完前界面完全静止，看着像死了 |
| `... \| Select-Object -First N` | **杀掉上游**，构建被静默截断 |
| `... \| Out-String` | 缓冲到结束才输出，全程无反馈 |
| `... > log.txt` 再 `Get-Content` | 同上，丢失实时性 |

### 要过滤输出怎么办

用 wrapper 的 `-Filter`，它只过滤**显示**，构建照常跑完：

```powershell
.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD SUCCESS|BUILD FAILED'
```

不要用管道过滤正在运行的 gradle。确实需要事后查完整日志时，读文件而不是重跑：
`Select-String -Path build\agent-logs\*.log -Pattern '错误'`。

### 卡死了怎么办（已经卡住时）

wrapper 正常情况不会卡。若仍遇到 spinner 不停：

```powershell
# 1. 干掉还占着句柄的 gradle / 游戏进程
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -match 'devlaunch|gradle-wrapper' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

# 2. 清掉可能被占住的 gradle 锁
.\gradlew.bat --stop

# 3. 之后一律改用 wrapper
```

## 构建脚本的编码约定

`tools/*.ps1` 一律**纯 ASCII**。PowerShell 5.1 读取无 BOM 的 UTF-8 时使用系统 ANSI 代码页（中文环境为 GBK），
多字节字符被破坏后会直接把解析器带偏，出现莫名其妙的 `MissingExpressionAfterToken` 解析错误。
需要中文注释时，请存为 **UTF-8 with BOM**。

## 其他

- 本地 JDK 21 在 `F:/application/jdk-21`（见 `gradle.properties` 的 `org.gradle.java.home`）
- 依赖源已针对国内网络调整：Mojang libraries 走 BMCLAPI 镜像，见 `build.gradle` 的 `afterEvaluate`
- 构建日志落在 `build/agent-logs/`（已被 gitignore）
