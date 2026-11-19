@echo off
rem Novograd Kino installer (TLauncher and Prism-based launchers). Batch part is ASCII only; the script (Russian) is below the marker line.
chcp 65001 >nul
title Novograd Kino - install
set "NVG_SELF=%~f0"
set "NVG_DIR=%~dp0"
if not exist "%~dp0files\mods" (
  echo Unpack the archive into a normal folder first, then run the installer from there.
  echo (Raspakuy arhiv v obychnuyu papku i zapusti ustanovshchik ottuda.)
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
$SRC = Join-Path $env:NVG_DIR 'files'
$utf8 = New-Object Text.UTF8Encoding($false)
$man = [IO.File]::ReadAllText((Join-Path $env:NVG_DIR 'manifest.json'), [Text.Encoding]::UTF8) | ConvertFrom-Json

function Fail($msg) {
    Write-Host ''
    Write-Host '============================================' -ForegroundColor Red
    Write-Host ('   ОШИБКА: ' + $msg) -ForegroundColor Red
    Write-Host '============================================' -ForegroundColor Red
    exit 1
}
function Sha1($p) { (Get-FileHash -Algorithm SHA1 -LiteralPath $p).Hash.ToLower() }
function Stem($n) { ($n -replace '[-_+]?(mc)?\d.*$', '').ToLower() }

Write-Host '============================================' -ForegroundColor Cyan
Write-Host ("   НОВОГРАД: КИНО $($man.version) — установка") -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan

# 0. Архив цел? (всё нужное — внутри архива, интернет для главного не нужен)
foreach ($m in $man.bundled) {
    $p = Join-Path $SRC (Join-Path $m.folder $m.file)
    if (-not (Test-Path -LiteralPath $p)) { Fail "в распакованной папке нет $($m.file). Распакуй архив заново целиком (не запускай установщик прямо из архива)." }
    if ((Sha1 $p) -ne $m.sha1) { Fail "файл $($m.file) повреждён. Скачай архив заново." }
}

# 1. Куда ставить: TLauncher (.minecraft) или сборка в Prism / ElyPrism / других лаунчерах на основе Prism
$targets = New-Object System.Collections.ArrayList
$tl = Join-Path $env:APPDATA '.minecraft'
if (Test-Path $tl) { [void]$targets.Add([pscustomobject]@{ Name = 'TLauncher / обычная папка .minecraft'; Dir = $tl; Pack = $null }) }
foreach ($base in @($env:APPDATA, $env:LOCALAPPDATA)) {
    if (-not $base) { continue }
    foreach ($root in Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue) {
        $inst = Join-Path $root.FullName 'instances'
        if (-not (Test-Path $inst)) { continue }
        foreach ($i in Get-ChildItem -LiteralPath $inst -Directory -ErrorAction SilentlyContinue) {
            $pack = Join-Path $i.FullName 'mmc-pack.json'
            if (-not (Test-Path $pack)) { continue }
            $gd = Join-Path $i.FullName 'minecraft'
            if (-not (Test-Path $gd) -and (Test-Path (Join-Path $i.FullName '.minecraft'))) { $gd = Join-Path $i.FullName '.minecraft' }
            [void]$targets.Add([pscustomobject]@{ Name = "$($root.Name): сборка «$($i.Name)»"; Dir = $gd; Pack = $pack })
        }
    }
}
Write-Host 'Куда поставить?'
for ($k = 0; $k -lt $targets.Count; $k++) { Write-Host ("  {0} — {1}" -f ($k + 1), $targets[$k].Name) }
Write-Host '  0 — указать папку игры вручную (где лежит или будет лежать папка mods)'
$GD = $null; $T = $null
while (-not $GD) {
    $def = if ($targets.Count -gt 0) { '1' } else { '0' }
    $a = Read-Host "Номер [Enter = $def]"
    if (-not $a) { $a = $def }
    $n = 0
    if (-not [int]::TryParse($a.Trim(), [ref]$n)) { continue }
    if ($n -eq 0) {
        $p = (Read-Host 'Путь к папке игры').Trim('" ')
        if ($p) { if (-not (Test-Path $p)) { New-Item -ItemType Directory -Force -Path $p | Out-Null }; $GD = $p }
    } elseif ($n -ge 1 -and $n -le $targets.Count) { $T = $targets[$n - 1]; $GD = $T.Dir }
}
function OldLoader($v) { $p = $v.Split('.'); ([int]$p[0] -eq 0) -and ([int]$p[1] -lt 17) }
if ($T -and $T.Pack) {
    $pj = [IO.File]::ReadAllText($T.Pack)
    $lv = [regex]::Match($pj, '"uid"\s*:\s*"net\.fabricmc\.fabric-loader"[^}]*?"version"\s*:\s*"([0-9.]+)"')
    if (-not $lv.Success) { $lv = [regex]::Match($pj, '"version"\s*:\s*"([0-9.]+)"[^}]*?"uid"\s*:\s*"net\.fabricmc\.fabric-loader"') }
    if ($pj -notmatch 'net\.fabricmc\.fabric-loader' -or $pj -notmatch '"1\.21\.1"') {
        Write-Host 'Внимание: в этой сборке не видно Fabric для 1.21.1.' -ForegroundColor Yellow
        Write-Host "  В лаунчере: сборка — Изменить — Версия — Minecraft 1.21.1, «Установить Fabric» (загрузчик $($man.loader) или новее)." -ForegroundColor Yellow
    } elseif ($lv.Success -and (OldLoader $lv.Groups[1].Value)) {
        Write-Host "Внимание: в этой сборке Fabric Loader $($lv.Groups[1].Value), а нужен $($man.loader) или новее (иначе GeckoLib не запустится)." -ForegroundColor Yellow
        Write-Host '  В лаунчере: сборка — Изменить — Версия — Fabric Loader — «Изменить версию» — самая новая.' -ForegroundColor Yellow
    }
} else {
    # профиль Fabric Loader (не ниже 0.17 — так требует GeckoLib внутри Новограда): лаунчер сам докачает библиотеки
    $vd = Join-Path $GD "versions\$($man.profile)"
    try {
        New-Item -ItemType Directory -Force -Path $vd | Out-Null
        Copy-Item -LiteralPath (Join-Path $SRC "$($man.profile).json") -Destination (Join-Path $vd "$($man.profile).json") -Force
        Write-Host "Добавил версию $($man.profile) — выбери её в TLauncher (или «Fabric 1.21.1» с загрузчиком $($man.loader)+)."
    } catch { Write-Host "Не смог добавить версию $($man.profile): в TLauncher выбери «Fabric 1.21.1» с самым новым загрузчиком." -ForegroundColor Yellow }
}
foreach ($d in 'mods', 'shaderpacks', 'config') { New-Item -ItemType Directory -Force -Path (Join-Path $GD $d) | Out-Null }

# 2. Вопросы
$lite = (Read-Host 'Ноутбук или слабый компьютер (встроенная видеокарта, 8 ГБ памяти и меньше)? Лёгкая сборка без шейдеров и эффектов [Y/N]').Trim().ToUpper() -in @('Y', 'Д', 'Н', 'L')
$big = $false; $skins = $false
if (-not $lite) {
    Write-Host 'Что ещё скачать (необязательно, нужен интернет):'
    $big = (Read-Host '  Flashback (запись и монтаж видео, 200 МБ) и AmbientSounds (звуки природы)? [Y/N]').Trim().ToUpper() -in @('Y', 'Д', 'Н')
    $skins = (Read-Host '  Свои скины: CustomSkinLoader + SkinRestorer? [Y/N]').Trim().ToUpper() -in @('Y', 'Д', 'Н')
}

# 3. Всё новое — сначала во временную папку. Старые моды не трогаем, пока новые не лежат рядом целыми.
$stage = Join-Path $GD 'novograd_install_tmp'
if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'mods'), (Join-Path $stage 'shaderpacks') | Out-Null
$want = @()
Write-Host '[1/3] Моды из архива...'
foreach ($m in $man.bundled) {
    if ($lite -and $m.group -eq 'fx') { continue }
    Copy-Item -LiteralPath (Join-Path $SRC (Join-Path $m.folder $m.file)) -Destination (Join-Path $stage $m.folder) -Force
    $want += $m
    Write-Host ('    ' + $m.file)
}
Write-Host '[2/3] Необязательное из интернета (modrinth.com)...'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
$ProgressPreference = 'SilentlyContinue'
$missed = @()
foreach ($m in $man.optional) {
    if ($m.group -eq 'big' -and -not $big) { continue }
    if ($m.group -eq 'skins' -and -not $skins) { continue }
    if ($m.group -eq 'fx' -and $lite) { continue }
    $dst = Join-Path $stage (Join-Path $m.folder $m.file)
    $ok = $false
    for ($try = 1; $try -le 3 -and -not $ok; $try++) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $m.url -OutFile $dst -TimeoutSec 180
            if ((Sha1 $dst) -eq $m.sha1) { $ok = $true } else { Remove-Item -LiteralPath $dst -Force }
        } catch { if (Test-Path -LiteralPath $dst) { Remove-Item -LiteralPath $dst -Force } }
    }
    if ($ok) { $want += $m; Write-Host ('    ' + $m.file) }
    else { $missed += $m.file; Write-Host ('    не скачался: ' + $m.file + ' (необязательный, игра запустится и без него)') -ForegroundColor Yellow }
}

# 4. Замена: убираем старые версии тех же модов (и прошлый Новоград), кладём новые
Write-Host '[3/3] Ставлю...'
foreach ($m in $want) {
    $dir = Join-Path $GD $m.folder
    if ($m.folder -eq 'mods') {
        $st = Stem $m.file
        foreach ($j in Get-ChildItem -LiteralPath $dir -Filter *.jar -ErrorAction SilentlyContinue) {
            if ($j.Name -eq $m.file) { continue }
            if ((Stem $j.Name) -eq $st -or ($m.prefix -and $j.Name.StartsWith($m.prefix, [StringComparison]::OrdinalIgnoreCase))) { Remove-Item -LiteralPath $j.FullName -Force }
        }
    }
    Copy-Item -LiteralPath (Join-Path $stage (Join-Path $m.folder $m.file)) -Destination $dir -Force
}
# Entity Culling ставила старая версия сборки; теперь вместо него More Culling
foreach ($j in Get-ChildItem -LiteralPath (Join-Path $GD 'mods') -Filter 'entityculling-*.jar' -ErrorAction SilentlyContinue) { Remove-Item -LiteralPath $j.FullName -Force }
Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue

# настройки: шейдеры (в лёгкой — выключены), лёгкий режим Новограда
$enable = if ($lite) { 'false' } else { 'true' }
[IO.File]::WriteAllText((Join-Path $GD 'config\iris.properties'), "enableShaders=$enable`r`nshaderPack=$($man.shader)`r`n", $utf8)
if ($lite -and -not (Test-Path (Join-Path $GD 'config\novograd.json'))) {
    [IO.File]::WriteAllText((Join-Path $GD 'config\novograd.json'), '{"graphics":1,"perf":0,"hudScale":0.85}', $utf8)
}

# 5. Проверка: все обязательные моды на месте и целые
$bad = @()
foreach ($m in $man.bundled) {
    if (-not $m.required) { continue }
    if ($lite -and $m.group -eq 'fx') { continue }
    $p = Join-Path $GD (Join-Path $m.folder $m.file)
    if (-not (Test-Path -LiteralPath $p) -or (Sha1 $p) -ne $m.sha1) { $bad += $m.file }
}
if ($bad.Count -gt 0) { Fail ("не на месте обязательные моды: " + ($bad -join ', ') + ". Закрой игру и лаунчер и запусти УСТАНОВИТЬ.bat ещё раз.") }

Write-Host ''
Write-Host '============================================' -ForegroundColor Green
Write-Host '   ГОТОВО! Все обязательные моды на месте (Fabric API, Новоград и др.).' -ForegroundColor Green
Write-Host ('   Папка игры: ' + $GD)
if ($T -and $T.Pack) { Write-Host '   Запускай эту сборку в своём лаунчере.' } else { Write-Host "   Запускай в TLauncher версию $($man.profile) (Fabric 1.21.1, загрузчик $($man.loader))." }
Write-Host '   В игре: Новый мир — Ещё — Тип мира «Новоград: Город».'
if ($lite) { Write-Host '   Лёгкая сборка: шейдеры выключены, «Производительность» — низкая (Настройки — Графика).' }
else { Write-Host '   Шейдеры: быстрый MakeUp. Красивее — Настройки — Графика — Шейдеры — Complementary (если скачался).' }
if ($missed.Count -gt 0) { Write-Host ('   Не скачались необязательные: ' + ($missed -join ', ') + '. Можно запустить установку ещё раз позже.') -ForegroundColor Yellow }
Write-Host '============================================' -ForegroundColor Green
