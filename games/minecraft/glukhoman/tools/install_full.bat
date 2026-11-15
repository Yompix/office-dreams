@echo off
rem Glukhoman full installer for the official Minecraft launcher and TLauncher. Batch part is ASCII only; the script (Russian) is below the marker line.
chcp 65001 >nul
title Glukhoman - install
set "GLK_SELF=%~f0"
set "GLK_DIR=%~dp0"
if not exist "%~dp0files\mods" (
  echo Unpack the archive into a normal folder first, then run INSTALL.bat from there.
  pause
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=[IO.File]::ReadAllText($env:GLK_SELF,[Text.Encoding]::UTF8); $m=[string][char]10+'#'+'PS'+'START'; $i=$t.IndexOf($m); if($i -lt 0){throw 'marker not found'}; iex ($t.Substring($i+$m.Length))"
if errorlevel 1 echo PowerShell error. Send a screenshot of this window.
pause
exit /b

#PSSTART
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'
$VID = '@@VID@@'
$SRC = Join-Path $env:GLK_DIR 'files'
$utf8 = New-Object Text.UTF8Encoding($false)

Write-Host '============================================' -ForegroundColor DarkRed
Write-Host '   ГЛУХОМАНЬ — установка' -ForegroundColor DarkRed
Write-Host '============================================' -ForegroundColor DarkRed

$MC = Join-Path $env:APPDATA '.minecraft'
if (-not (Test-Path $MC)) {
    Write-Host 'Не нашёл папку .minecraft. Запусти лаунчер Minecraft (или TLauncher) один раз и закрой его.' -ForegroundColor Yellow
    $MC = Read-Host 'Или вставь путь к папке .minecraft'
    if (-not (Test-Path $MC)) { New-Item -ItemType Directory -Path $MC | Out-Null }
}
$GD = Join-Path $MC 'glukhoman'
$tl = Read-Host 'Играешь через TLauncher? [Y/N]'
$tl = $tl.Trim().ToUpper() -in @('Y', 'Д', 'Н', 'L')

# 1. Fabric Loader — профиль версии (лаунчер сам докачает Minecraft 1.21.1 и библиотеки)
Write-Host '[1/4] Fabric Loader...'
$vd = Join-Path $MC "versions\$VID"
New-Item -ItemType Directory -Force -Path $vd | Out-Null
Copy-Item (Join-Path $SRC "$VID.json") (Join-Path $vd "$VID.json") -Force

# 2. Моды — в отдельную папку игры Глухомани (и в .minecraft\mods для TLauncher)
Write-Host '[2/4] Моды...'
$ours = Get-ChildItem (Join-Path $SRC 'mods') -Filter *.jar
function Put-Mods($dir) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    # старые версии тех же модов убираем (по началу имени до цифр), чужие моды не трогаем
    foreach ($j in Get-ChildItem $dir -Filter *.jar -ErrorAction SilentlyContinue) {
        $stem = ($j.Name -replace '[-_+]?(mc)?\d.*$', '').ToLower()
        if ($stem -and ($ours | Where-Object { ($_.Name -replace '[-_+]?(mc)?\d.*$', '').ToLower() -eq $stem })) { Remove-Item $j.FullName -Force }
    }
    foreach ($j in $ours) { Copy-Item $j.FullName $dir -Force; Write-Host ('    ' + $j.Name) }
}
foreach ($d in 'config') { New-Item -ItemType Directory -Force -Path (Join-Path $GD $d) | Out-Null }
Put-Mods (Join-Path $GD 'mods')
if ($tl) { Write-Host '    + копия в .minecraft\mods для TLauncher'; Put-Mods (Join-Path $MC 'mods') }
# первые настройки игры: русский язык, дальность под туман, без лишней нагрузки
foreach ($dir in @($GD) + $(if ($tl) { @($MC) } else { @() })) {
    $opt = Join-Path $dir 'options.txt'
    if (-not (Test-Path $opt)) {
        $o = @('lang:ru_ru', 'maxFps:60', 'enableVsync:true', 'renderDistance:8', 'simulationDistance:6', 'biomeBlendRadius:0', 'entityShadows:false', 'onboardAccessibility:false')
        [IO.File]::WriteAllText($opt, ($o -join "`r`n") + "`r`n", $utf8)
    }
}

# 2б. Шейдеры (по желанию): Iris + пакет скачиваются с modrinth.com прямо сейчас, проверка sha512. Сами мы их не распространяем.
$SH = @(
@@SHADERS@@
)
Write-Host ''
Write-Host 'Шейдеры: настоящая тьма, тени и туман. 1 — лёгкие (MakeUp Ultra Fast, для слабых ноутбуков), 2 — красивые (Complementary), N — без шейдеров'
$pick = (Read-Host 'Выбор [1/2/N]').Trim().ToUpper()
if ($pick -in @('1', '2')) {
    $want = @('iris') + $(if ($pick -eq '1') { @('makeup') } else { @('complementary') })
    foreach ($s in $SH | Where-Object { $want -contains $_.name }) {
        $dir = Join-Path $GD $s.dir
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $dst = Join-Path $dir $s.file
        try {
            [Net.ServicePointManager]::SecurityProtocol = 'Tls12'
            Invoke-WebRequest -UseBasicParsing -Uri $s.url -OutFile $dst
            $h = (Get-FileHash -Algorithm SHA512 $dst).Hash.ToLower()
            if ($h -ne $s.sha) { Remove-Item $dst -Force; Write-Host ('    контрольная сумма не совпала: ' + $s.file) -ForegroundColor Yellow }
            else { Write-Host ('    ' + $s.file) }
        } catch { Write-Host ('    не скачалось: ' + $s.file + ' (нет интернета?) — можно без шейдеров') -ForegroundColor Yellow }
    }
    $pack = ($SH | Where-Object { $want[1] -eq $_.name }).file
    [IO.File]::WriteAllText((Join-Path $GD 'config\iris.properties'), "enableShaders=true`r`nshaderPack=$pack`r`n", $utf8)
}

# 3. Память для игры — по объёму оперативной памяти
Write-Host '[3/4] Память...'
$ram = 8
try { $ram = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB) } catch {}
$xmx = 2560
if ($ram -ge 12) { $xmx = 3072 }
if ($ram -ge 16) { $xmx = 4096 }
Write-Host "    В компьютере $ram ГБ — игре выделяю $xmx МБ"

# 4. Профиль «Глухомань» в официальном лаунчере
Write-Host '[4/4] Профиль в лаунчере...'
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$prof = [pscustomobject]@{
    name = 'Глухомань'; type = 'custom'; created = $now; lastUsed = $now; lastVersionId = $VID; gameDir = $GD; icon = 'Furnace'
    javaArgs = "-Xmx${xmx}M -Xms1024M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200"
}
$files = @('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') | ForEach-Object { Join-Path $MC $_ } | Where-Object { Test-Path $_ }
if (-not $files) { $files = @(Join-Path $MC 'launcher_profiles.json') }
foreach ($f in $files) {
    try {
        if (Test-Path $f) { $j = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json } else { $j = [pscustomobject]@{} }
        if (-not $j.profiles) { $j | Add-Member -Force profiles ([pscustomobject]@{}) }
        $j.profiles | Add-Member -Force glukhoman $prof
        [IO.File]::WriteAllText($f, ($j | ConvertTo-Json -Depth 20), $utf8)
        Write-Host ('    ' + $f)
    } catch { Write-Host ('    пропускаю ' + $f) -ForegroundColor Yellow }
}

Write-Host ''
Write-Host '============================================' -ForegroundColor Green
Write-Host '   ГОТОВО!' -ForegroundColor Green
Write-Host '   Официальный лаунчер: внизу слева выбери профиль «Глухомань» и нажми «Играть».'
if ($tl) { Write-Host "   TLauncher: в списке версий выбери $VID (память $xmx МБ — в настройках TLauncher)." }
Write-Host '   В игре: Новый мир — Ещё — Тип мира «Глухомань» — Создать.'
Write-Host '   С друзьями: через LANTERN или «Открыть для друзей» — у всех должен стоять этот же архив.'
Write-Host '   Играй в наушниках и с выключенным светом. R — фонарик, H — подсказка, Shift — красться.'
Write-Host '============================================' -ForegroundColor Green
