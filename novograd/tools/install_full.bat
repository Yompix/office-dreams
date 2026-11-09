@echo off
rem Novograd full installer for the official Minecraft launcher. Batch part is ASCII only; the script (Russian) is below the marker line.
chcp 65001 >nul
title Novograd - install
set "NVG_SELF=%~f0"
set "NVG_DIR=%~dp0"
if not exist "%~dp0files\mods" (
  echo Unpack the archive into a normal folder first, then run INSTALL.bat from there.
  pause
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=[IO.File]::ReadAllText($env:NVG_SELF,[Text.Encoding]::UTF8); $m=[string][char]10+'#'+'PS'+'START'; $i=$t.IndexOf($m); if($i -lt 0){throw 'marker not found'}; iex ($t.Substring($i+$m.Length))"
if errorlevel 1 echo PowerShell error. Send a screenshot of this window.
pause
exit /b

#PSSTART
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'
$VID = '@@VID@@'
$SRC = Join-Path $env:NVG_DIR 'files'
$utf8 = New-Object Text.UTF8Encoding($false)

Write-Host '============================================' -ForegroundColor Cyan
Write-Host '   НОВОГРАД — установка в официальный лаунчер' -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan

$MC = Join-Path $env:APPDATA '.minecraft'
if (-not (Test-Path $MC)) {
    Write-Host 'Не нашёл папку .minecraft. Запусти лаунчер Minecraft один раз и закрой его.' -ForegroundColor Yellow
    $MC = Read-Host 'Или вставь путь к папке .minecraft'
    if (-not (Test-Path $MC)) { New-Item -ItemType Directory -Path $MC | Out-Null }
}
$GD = Join-Path $MC 'novograd'

$lite = Read-Host 'Слабый компьютер или ноутбук (встроенная видеокарта, 8 ГБ памяти и меньше)? Лёгкий режим без шейдеров [Y/N]'
$lite = $lite.Trim().ToUpper() -in @('Y', 'Д', 'Н', 'L')

# 1. Fabric Loader — профиль версии (лаунчер сам докачает Minecraft 1.21.1 и библиотеки)
Write-Host '[1/4] Fabric Loader...'
$vd = Join-Path $MC "versions\$VID"
New-Item -ItemType Directory -Force -Path $vd | Out-Null
Copy-Item (Join-Path $SRC "$VID.json") (Join-Path $vd "$VID.json") -Force

# 2. Моды и шейдеры — в отдельную папку игры Новограда
Write-Host '[2/4] Моды...'
foreach ($d in 'mods', 'shaderpacks', 'config') { New-Item -ItemType Directory -Force -Path (Join-Path $GD $d) | Out-Null }
$ours = Get-ChildItem (Join-Path $SRC 'mods') -Filter *.jar
# старые версии тех же модов и Новограда убираем (по началу имени до цифр), чужие моды не трогаем
foreach ($j in Get-ChildItem (Join-Path $GD 'mods') -Filter *.jar -ErrorAction SilentlyContinue) {
    $stem = ($j.Name -replace '[-_+]?(mc)?\d.*$', '').ToLower()
    if ($stem -and ($ours | Where-Object { ($_.Name -replace '[-_+]?(mc)?\d.*$', '').ToLower() -eq $stem })) { Remove-Item $j.FullName -Force }
}
foreach ($j in $ours) { Copy-Item $j.FullName (Join-Path $GD 'mods') -Force; Write-Host ('    ' + $j.Name) }
$shader = Get-ChildItem (Join-Path $SRC 'shaderpacks') | Select-Object -First 1
Copy-Item $shader.FullName (Join-Path $GD 'shaderpacks') -Force
$enable = if ($lite) { 'false' } else { 'true' }
[IO.File]::WriteAllText((Join-Path $GD 'config\iris.properties'), "enableShaders=$enable`r`nshaderPack=$($shader.Name)`r`n", $utf8)
if ($lite -and -not (Test-Path (Join-Path $GD 'config\novograd.json'))) {
    [IO.File]::WriteAllText((Join-Path $GD 'config\novograd.json'), '{"graphics":1,"hudScale":0.85,"windyFoliage":false}', $utf8)
}
# первые настройки игры: русский язык и лёгкая дальность (если своих ещё нет)
$opt = Join-Path $GD 'options.txt'
if (-not (Test-Path $opt)) {
    $o = @('lang:ru_ru', 'maxFps:60', 'enableVsync:true', 'biomeBlendRadius:0', 'entityShadows:false')
    if ($lite) { $o += @('renderDistance:8', 'simulationDistance:6', 'graphicsMode:0', 'particles:1', 'ao:false') }
    else { $o += @('renderDistance:10', 'simulationDistance:8') }
    [IO.File]::WriteAllText($opt, ($o -join "`r`n") + "`r`n", $utf8)
}

# 3. Память для игры — по объёму оперативной памяти
Write-Host '[3/4] Память...'
$ram = 8
try { $ram = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB) } catch {}
$xmx = 3072
if ($ram -ge 12) { $xmx = 4096 }
if ($ram -ge 16) { $xmx = 6144 }
Write-Host "    В компьютере $ram ГБ — игре выделяю $xmx МБ"

# 4. Профиль «Новоград» в лаунчере
Write-Host '[4/4] Профиль в лаунчере...'
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$prof = [pscustomobject]@{
    name = 'Новоград'; type = 'custom'; created = $now; lastUsed = $now; lastVersionId = $VID; gameDir = $GD; icon = 'Furnace'
    javaArgs = "-Xmx${xmx}M -Xms1024M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200"
}
$files = @('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') | ForEach-Object { Join-Path $MC $_ } | Where-Object { Test-Path $_ }
if (-not $files) { $files = @(Join-Path $MC 'launcher_profiles.json') }
foreach ($f in $files) {
    if (Test-Path $f) { $j = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json } else { $j = [pscustomobject]@{} }
    if (-not $j.profiles) { $j | Add-Member -Force profiles ([pscustomobject]@{}) }
    $j.profiles | Add-Member -Force novograd $prof
    [IO.File]::WriteAllText($f, ($j | ConvertTo-Json -Depth 20), $utf8)
    Write-Host ('    ' + $f)
}

Write-Host ''
Write-Host '============================================' -ForegroundColor Green
Write-Host '   ГОТОВО!' -ForegroundColor Green
Write-Host '   Открой лаунчер Minecraft, внизу слева выбери профиль «Новоград»'
Write-Host '   и нажми «Играть». Первый запуск дольше: лаунчер докачает Minecraft 1.21.1.'
Write-Host '   В игре: Новый мир — Ещё — Тип мира «Новоград: Город».'
if ($lite) { Write-Host '   Лёгкий режим: шейдеры выключены (включить: Настройки — Графика — Шейдеры).' }
Write-Host '============================================' -ForegroundColor Green
