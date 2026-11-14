@echo off
chcp 65001 >nul
title Глухомань — починить запуск
:: --- права администратора (нужны для файла подкачки) ---
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Запрашиваю права администратора...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
echo ============================================
echo   ГЛУХОМАНЬ — настраиваю компьютер и игру,
echo   чтобы не вылетало и не тормозило
echo ============================================
set "MC=%APPDATA%\.minecraft"
if not exist "%MC%" (
  set /p MC="Не нашёл .minecraft. Вставь путь к папке игры (где лежит mods): "
)
rem Глухомань ставится в свою папку .minecraft\glukhoman — чиним её
if exist "%MC%\glukhoman\mods" set "MC=%MC%\glukhoman"

echo.
echo [1/6] Файл подкачки: включаю автоматический размер (Windows сама даст сколько нужно)...
powershell -NoProfile -Command "$cs=Get-CimInstance Win32_ComputerSystem; if(-not $cs.AutomaticManagedPagefile){ $cs | Set-CimInstance -Property @{AutomaticManagedPagefile=$true}; Write-Host '   включено — нужна перезагрузка компьютера'; exit 3 } else { Write-Host '   уже включено' }"
if %errorlevel%==3 set REBOOT=1

echo.
echo [2/6] Память компьютера:
set RAMGB=8
for /f %%m in ('powershell -NoProfile -Command "[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB)"') do set RAMGB=%%m
set XMX=3072
if %RAMGB% GEQ 12 set XMX=4096
if %RAMGB% GEQ 16 set XMX=6144
echo    В компьютере %RAMGB% ГБ. Для игры нужно выделить %XMX% МБ.
:: TLauncher хранит память в своих настройках — если нашли, ставим сами
for %%f in ("%APPDATA%\.tlauncher\tlauncher-2.0.properties" "%MC%\TLauncher\tlauncher-2.0.properties" "%APPDATA%\.minecraft\tlauncher-2.0.properties") do (
  if exist %%f (
    powershell -NoProfile -Command "$p='%%~f'; $t=Get-Content $p -Raw; if($t -match 'minecraft\.xmx='){ $t=$t -replace 'minecraft\.xmx=\d+','minecraft.xmx=%XMX%' } else { $t+=\"`nminecraft.xmx=%XMX%\" }; Set-Content -Path $p -Value $t -Encoding UTF8"
    echo    Записал %XMX% МБ в настройки TLauncher.
  )
)

echo.
echo [3/6] Настройки Minecraft: дальность 8, быстрая графика, 60 кадров, без облаков...
if not exist "%MC%\options.txt" type nul > "%MC%\options.txt"
powershell -NoProfile -Command ^
 "$p='%MC%\options.txt'; $o=@{}; foreach($l in Get-Content $p){ $i=$l.IndexOf(':'); if($i -gt 0){ $o[$l.Substring(0,$i)]=$l.Substring($i+1) } };" ^
 "$set=@{renderDistance='8';simulationDistance='6';graphicsMode='0';maxFps='60';enableVsync='true';particles='1';biomeBlendRadius='0';mipmapLevels='2';entityDistanceScaling='0.75';entityShadows='false';ao='false'};" ^
 "foreach($k in $set.Keys){ $o[$k]=$set[$k] }; ($o.GetEnumerator() | ForEach-Object { $_.Key + ':' + $_.Value }) | Set-Content -Path $p -Encoding UTF8"
echo    готово

echo.
echo [4/6] Шейдеры выключаю (на встроенной видеокарте они и роняют игру)...
if not exist "%MC%\config" mkdir "%MC%\config"
echo enableShaders=false> "%MC%\config\iris.properties"
echo    готово (включить потом: Настройки - Графика - Шейдеры)

echo.
echo [5/6] Лишние тяжёлые моды убираю в папку mods_backup (ничего не удаляю)...
set "BK=%MC%\mods_backup"
if not exist "%BK%" mkdir "%BK%"
for %%x in (iris-* Flashback-* AmbientSounds_* CreativeCore_* bbs-* replaymod-* sound-physics-* particlerain-* visuality-* wakes-* continuity-* CustomSkinLoader_* skinrestorer-* camerautils-* freecam-* entityculling-*) do (
  if exist "%MC%\mods\%%x.jar" move /y "%MC%\mods\%%x.jar" "%BK%\" >nul
)
echo    В mods остались: Глухомань, Fabric API, Sodium и моды ускорения.
dir /b "%MC%\mods\*.jar"

echo.
echo [6/6] Графика Глухоманьа — лёгкий режим...
powershell -NoProfile -Command "$p='%MC%\config\glukhoman.json'; if(Test-Path $p){ $j=Get-Content $p -Raw | ConvertFrom-Json } else { $j=[pscustomobject]@{} }; $j | Add-Member -Force graphics 1; $j | Add-Member -Force windyFoliage $false; $j | ConvertTo-Json | Set-Content -Path $p -Encoding UTF8"
echo    готово

echo.
echo ============================================
echo   ГОТОВО.
if defined REBOOT echo   1) ПЕРЕЗАГРУЗИ компьютер (включился файл подкачки).
echo   2) В TLauncher: шестерёнка - память = %XMX% МБ (если не поставилось само).
echo   3) Закрой браузер и другие программы перед игрой.
echo   4) Запускай Fabric 1.21.1.
echo   Вернуть моды: перенеси файлы из .minecraft\mods_backup обратно в mods.
echo ============================================
pause
