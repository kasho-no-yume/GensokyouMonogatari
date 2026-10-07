## 1. Prepare server directory

- [x] 1.1 Ensure `server/` exists; add `server/` (and `server/mods/*.jar`, `server/logs`, `server/world`) to `.gitignore` with a `server/.gitkeep` placeholder
- [x] 1.2 Verify existing `server/libraries` install remains usable (leave as-is)

## 2. Build step

- [x] 2.1 Add a thin wrapper call in `tools/run_server_jar.ps1` to invoke `tools/gradle_task.ps1 build` and propagate failure
- [x] 2.2 Copy `build/libs/gensokyou-1.0.0.jar` into `server/mods/` after successful build

## 3. Dependency jar staging

- [x] 3.1 Read `patchouli_version` and `geckolib_version` from `gradle.properties`
- [x] 3.2 Implement cache lookup under `%USERPROFILE%\.gradle\caches\modules-2\files-2.1`; fall back to maven download from blamejared/cloudsmith
- [x] 3.3 Download via proxy (127.0.0.1:7897) when direct fails; place jars in `server/mods/`

## 4. First-run install path

- [x] 4.1 Detect missing `server/libraries/net/neoforged/neoforge/21.1.248/`
- [x] 4.2 Download `neoforge-21.1.248-installer.jar` (BMCLAPI maven first, official as fallback) and run `--installServer` with Java proxy args
- [x] 4.3 Write `server/eula.txt` (`eula=true`) after install

## 5. Launch step

- [x] 5.1 Build the launch command: `F:\application\jdk-21\bin\java.exe @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.248/win_args.txt %*`
- [x] 5.2 Spawn via WMI (`Win32_Process.Create`, `Win32_ProcessStartup.ShowWindow = 0`) from `server/` as cwd, logging to `server/logs/latest.log`
- [x] 5.3 Return immediately after spawn; print PID and log path

## 6. Verification

- [x] 6.1 Smoke-run the script end to end and confirm the GUI server boots
- [x] 6.2 Confirm `server/mods/` contains gensokyou, Patchouli, GeckoLib jars
- [x] 6.3 Confirm no agent pipe wedge (script call returns on its own)
