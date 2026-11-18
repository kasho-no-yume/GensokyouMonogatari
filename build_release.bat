@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ============================================================
rem  build_release.bat - package a release build of the mod
rem ============================================================
rem  What it does:
rem    1. Runs the full Gradle 'build' (includes verifyNoTestContent) plus
rem       'copyReleaseDeps' via tools\gradle_task.ps1.
rem    2. Copies the mod jar into .\release\ and writes a SHA-256 checksum.
rem    3. Copies third-party mod jars into release\required\ (GeckoLib,
rem       Patchouli) and release\optional\ (JEI).
rem
rem  Docs and non-mod libraries are intentionally NOT bundled.
rem
rem  Safe to run from any working directory; every path is resolved
rem  relative to this script's own location.
rem ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "WRAPPER=%ROOT%\tools\gradle_task.ps1"
set "LIBSDIR=%ROOT%\build\libs"
set "DEPSDIR=%ROOT%\build\release-deps"
set "RELEASEDIR=%ROOT%\release"

echo ============================================================
echo  Gensokyou - release build
echo ============================================================
echo  Project : %ROOT%
echo.

if not exist "%WRAPPER%" (
    echo [release] ERROR: Gradle wrapper missing: %WRAPPER%
    exit /b 1
)

rem ---- 1/3 : build (full chain, including verifyNoTestContent) + collect deps ----
echo [release] 1/3  Running full build and collecting dependencies ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%WRAPPER%" build copyReleaseDeps
if errorlevel 1 (
    echo [release] ERROR: build failed. Nothing was packaged.
    exit /b 1
)
if not exist "%DEPSDIR%\required\*.jar" (
    echo [release] ERROR: required dependency jars missing in %DEPSDIR%\required
    exit /b 1
)
if not exist "%DEPSDIR%\optional\*.jar" (
    echo [release] ERROR: optional dependency jars missing in %DEPSDIR%\optional
    exit /b 1
)

rem ---- 2/3 : locate the produced mod jar ----
echo [release] 2/3  Locating mod jar ...
set "JAR="
set "JARNAME="
for %%F in ("%LIBSDIR%\gensokyou-*.jar") do (
    set "N=%%~nxF"
    echo(!N!| findstr /i /c:"-sources" /c:"-javadoc" >nul
    if errorlevel 1 (
        set "JAR=%%~fF"
        set "JARNAME=!N!"
    )
)
if not defined JAR goto :NOJAR
if not exist "%JAR%" goto :NOJAR
echo [release]       Found: !JARNAME!
goto :GOTJAR
:NOJAR
echo [release] ERROR: no mod jar found under %LIBSDIR%
exit /b 1
:GOTJAR

rem ---- 3/3 : copy + checksum ----
echo [release] 3/3  Copying to %RELEASEDIR% ...
rem mkdir on an existing dir returns 1, so ignore its code and check existence instead:
rem relying on `if errorlevel` here is unsafe (findstr in the scan loop leaves it at 1).
mkdir "%RELEASEDIR%" >nul 2>&1
if not exist "%RELEASEDIR%" (
    echo [release] ERROR: cannot create %RELEASEDIR%
    exit /b 1
)
rem Clean only the files this script owns, leave everything else alone.
del /q "%RELEASEDIR%\gensokyou-*.jar" >nul 2>&1
del /q "%RELEASEDIR%\gensokyou-*.jar.sha256" >nul 2>&1

set "OUT=%RELEASEDIR%\!JARNAME!"
copy /y "!JAR!" "%OUT%" >nul
if errorlevel 1 (
    echo [release] ERROR: copy failed.
    exit /b 1
)

rem certutil is used instead of PowerShell's Get-FileHash: a PowerShell 7
rem host can leak its PSModulePath into Windows PowerShell 5.1 and break
rem cmdlet discovery. certutil is a plain system tool with no such issue.
set "HASH="
for /f "skip=1 tokens=1" %%H in ('certutil -hashfile "%OUT%" SHA256') do (
    if not defined HASH set "HASH=%%H"
)
if defined HASH (
    > "%OUT%.sha256" echo %JARNAME%  %HASH%
) else (
    echo [release] WARNING: could not compute SHA-256 checksum.
)

rem ---- dependency jars: required (hard) / optional (soft) ----
echo [release]       Copying dependencies ...
mkdir "%RELEASEDIR%\required" >nul 2>&1
mkdir "%RELEASEDIR%\optional" >nul 2>&1
rem Clean only the jars this script owns; leave everything else alone.
del /q "%RELEASEDIR%\required\*.jar" >nul 2>&1
del /q "%RELEASEDIR%\optional\*.jar" >nul 2>&1
copy /y "%DEPSDIR%\required\*.jar" "%RELEASEDIR%\required\" >nul
if errorlevel 1 (
    echo [release] ERROR: copying required dependencies failed.
    exit /b 1
)
copy /y "%DEPSDIR%\optional\*.jar" "%RELEASEDIR%\optional\" >nul
if errorlevel 1 (
    echo [release] ERROR: copying optional dependencies failed.
    exit /b 1
)

echo.
echo ------------------------------------------------------------
echo  Release ready:
echo    %OUT%
if exist "%OUT%.sha256" type "%OUT%.sha256"
echo.
echo  required\  (must install):
dir /b "%RELEASEDIR%\required\*.jar"
echo  optional\ (JEI, optional):
dir /b "%RELEASEDIR%\optional\*.jar"
echo ------------------------------------------------------------
exit /b 0
