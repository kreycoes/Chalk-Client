@echo off
setlocal
where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 wurde nicht gefunden.
  pause
  exit /b 1
)
where gradle >nul 2>nul
if not errorlevel 1 (
  call gradle build --no-daemon --console=plain %*
) else (
  if exist "C:\Gradle\gradle-9.2.1\bin\gradle.bat" (
    call "C:\Gradle\gradle-9.2.1\bin\gradle.bat" build --no-daemon --console=plain %*
  ) else (
    echo Gradle 9.2.1 wurde nicht gefunden. Bitte installieren oder zum PATH hinzufuegen.
    pause
    exit /b 1
  )
)
set "chalk_build_result=%errorlevel%"
if not "%chalk_build_result%"=="0" pause
exit /b %chalk_build_result%
