@echo off
rem Novograd: PC cleanup. Batch part is ASCII only; the script itself (Russian) is below the marker line.
chcp 65001 >nul
title Novograd - cleanup
set "NVG_SELF=%~f0"
net session >nul 2>&1
if %errorlevel%==0 goto run
echo Requesting administrator rights...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Start-Process -FilePath $env:NVG_SELF -Verb RunAs -ErrorAction Stop; exit 0 } catch { exit 1 }"
if %errorlevel%==0 exit /b
echo No admin rights - running anyway (system folders will be skipped).
:run
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=[IO.File]::ReadAllText($env:NVG_SELF,[Text.Encoding]::UTF8); $m=[string][char]10+'#'+'PS'+'START'; $i=$t.IndexOf($m); if($i -lt 0){throw 'marker not found'}; iex ($t.Substring($i+$m.Length))"
if errorlevel 1 echo PowerShell error. Send a screenshot of this window.
pause
exit /b

#PSSTART
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$ErrorActionPreference = 'SilentlyContinue'
$Host.UI.RawUI.WindowTitle = 'Новоград — очистка компьютера'

function GB($b) { if ($b -ge 1GB) { '{0:N1} ГБ' -f ($b / 1GB) } else { '{0:N0} МБ' -f ($b / 1MB) } }
function FreeC { (Get-PSDrive C).Free }
function Files($paths, [int]$olderDays = 0) {
    $lim = (Get-Date).AddDays(-$olderDays)
    foreach ($p in $paths) {
        if (-not $p) { continue }
        Get-ChildItem -Path $p -Recurse -Force -File -ErrorAction SilentlyContinue |
            Where-Object { $olderDays -eq 0 -or $_.LastWriteTime -lt $lim }
    }
}

$MC = Join-Path $env:APPDATA '.minecraft'
$TL = Join-Path $env:APPDATA '.tlauncher'
$LA = $env:LOCALAPPDATA
$DL = Join-Path $env:USERPROFILE 'Downloads'

# Каждая находка: что это, безопасно ли, какие файлы. Ничего не удаляется без твоего «1».
$cats = @(
    @{ N = 'Временные файлы Windows и программ'; S = 'безопасно';
       D = 'Мусор, который программы оставляют после работы. Берутся только файлы старше суток.';
       F = { Files @("$env:TEMP\*", "$env:WINDIR\Temp\*") 1 } },
    @{ N = 'Корзина'; S = 'безопасно, если в корзине нет нужного'; Bin = $true;
       D = 'Файлы, которые ты уже удалил, но они ещё занимают место.' },
    @{ N = 'Кэш браузеров (Chrome, Edge, Яндекс, Opera, Firefox)'; S = 'безопасно';
       D = 'Сохранённые картинки сайтов. Пароли, вкладки и вход в аккаунты НЕ трогаются. Закрой браузер перед очисткой.';
       F = { Files @("$LA\Google\Chrome\User Data\*\Cache\*", "$LA\Google\Chrome\User Data\*\Code Cache\*",
                     "$LA\Microsoft\Edge\User Data\*\Cache\*", "$LA\Microsoft\Edge\User Data\*\Code Cache\*",
                     "$LA\Yandex\YandexBrowser\User Data\*\Cache\*", "$LA\Yandex\YandexBrowser\User Data\*\Code Cache\*",
                     "$LA\Opera Software\Opera Stable\Cache\*", "$LA\Opera Software\Opera GX Stable\Cache\*",
                     "$LA\Mozilla\Firefox\Profiles\*\cache2\*") } },
    @{ N = 'Скачанные обновления Windows'; S = 'безопасно (уже установлены)'; WU = $true;
       D = 'Установочные файлы обновлений, которые Windows уже поставила.';
       F = { Files @("$env:WINDIR\SoftwareDistribution\Download\*") } },
    @{ N = 'Отчёты об ошибках и дампы вылетов'; S = 'безопасно';
       D = 'Файлы, которые Windows пишет при вылетах программ. Тебе они не нужны.';
       F = { Files @("$env:ProgramData\Microsoft\Windows\WER\*", "$LA\CrashDumps\*", "$env:WINDIR\Minidump\*", "$env:WINDIR\MEMORY.DMP", "$LA\Microsoft\Windows\WER\*") } },
    @{ N = 'Кэш миниатюр картинок'; S = 'безопасно (Windows создаст заново)';
       D = 'Маленькие копии картинок для папок.';
       F = { Files @("$LA\Microsoft\Windows\Explorer\thumbcache_*.db", "$LA\Microsoft\Windows\Explorer\iconcache_*.db") } },
    @{ N = 'Minecraft: старые логи и отчёты о вылетах'; S = 'безопасно';
       D = 'Журналы прошлых запусков игры и TLauncher. Последний журнал (latest.log) оставляю.';
       F = { Files @("$MC\logs\*.log.gz", "$MC\crash-reports\*", "$MC\debug\*", "$MC\.fabric\processedMods\*", "$TL\logs\*") } },
    @{ N = 'Minecraft: старые версии модов (mods_old)'; S = 'безопасно';
       D = 'Копии старых модов, оставшиеся после обновлений.';
       F = { Files @("$MC\mods_old\*") } },
    @{ N = 'Minecraft: тяжёлые моды, убранные починкой (mods_backup)'; S = 'удаляй, только если не собираешься их возвращать';
       D = 'Iris, Flashback, звуковые моды и т.п., которые ПОЧИНИТЬ_ЗАПУСК.bat убрал из игры, чтобы не вылетало.';
       F = { Files @("$MC\mods_backup\*") } },
    @{ N = 'Старые установщики Новограда в «Загрузках»'; S = 'безопасно (самая новая версия остаётся)';
       D = 'Скачанные раньше архивы и моды Новограда прошлых версий.';
       F = {
           $all = @(Get-ChildItem -Path "$DL\*" -Force -File -Include 'Novograd-*', 'novograd-*' -ErrorAction SilentlyContinue)
           $ver = $all | ForEach-Object { if ($_.Name -match '(\d+\.\d+\.\d+)') { [version]$Matches[1] } } | Sort-Object -Descending | Select-Object -First 1
           if ($ver) { $all | Where-Object { $_.Name -match '(\d+\.\d+\.\d+)' -and [version]$Matches[1] -lt $ver } }
       } },
    @{ N = 'Записи Flashback / Replay Mod'; S = 'ВНИМАНИЕ: это твои записанные видео из игры';
       D = 'Повторы, которые ты записывал. Удаляй, только если они уже не нужны.';
       F = { Files @("$MC\flashback\*", "$MC\replay_recordings\*") } }
)

Write-Host ''
Write-Host '============================================' -ForegroundColor Cyan
Write-Host '   НОВОГРАД — проверка и очистка компьютера' -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan
$free0 = FreeC
Write-Host ('Свободно на диске C: ' + (GB $free0))
Write-Host ''
Write-Host 'Шаг 1. Ищу ненужное (1–3 минуты, ничего не удаляю)...' -ForegroundColor Yellow

$found = @()
foreach ($c in $cats) {
    Write-Host ('  проверяю: ' + $c.N)
    if ($c.Bin) {
        $sh = New-Object -ComObject Shell.Application
        $size = 0; $cnt = 0
        foreach ($i in $sh.Namespace(10).Items()) { $size += $i.Size; $cnt++ }
        $c.Size = $size; $c.Num = $cnt; $c.List = $null
    } else {
        $list = @(& $c.F)
        $c.List = $list
        $c.Size = ($list | Measure-Object -Property Length -Sum).Sum
        if (-not $c.Size) { $c.Size = 0 }
        $c.Num = $list.Count
    }
    if ($c.Num -gt 0 -and $c.Size -gt 1MB) { $found += $c }
}

# Старые версии игры в TLauncher (кроме 1.21.1 — на ней Новоград)
$vers = @()
if (Test-Path "$MC\versions") {
    foreach ($d in Get-ChildItem "$MC\versions" -Directory) {
        if ($d.Name -match '1\.21\.1') { continue }
        $s = (Get-ChildItem $d.FullName -Recurse -Force -File | Measure-Object Length -Sum).Sum
        if ($s -gt 20MB) { $vers += @{ Name = $d.Name; Path = $d.FullName; Size = $s } }
    }
}

# Большие файлы — только показываю, решаешь сам
$big = @(Get-ChildItem -Path "$DL\*", "$env:USERPROFILE\Desktop\*", "$env:USERPROFILE\Videos\*" -Recurse -Force -File -ErrorAction SilentlyContinue |
         Where-Object { $_.Length -gt 500MB } | Sort-Object Length -Descending | Select-Object -First 15)

Write-Host ''
Write-Host '============================================' -ForegroundColor Cyan
Write-Host '   ЧТО НАШЁЛ' -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan
$total = 0
$n = 0
foreach ($c in $found) {
    $n++
    $total += $c.Size
    Write-Host ''
    Write-Host ("[$n] " + $c.N + ' — ' + (GB $c.Size) + ' (' + $c.Num + ' файлов)') -ForegroundColor White
    Write-Host ('    ' + $c.D)
    $col = 'Green'; if ($c.S -match 'ВНИМАНИЕ|только') { $col = 'Yellow' }
    Write-Host ('    ' + $c.S) -ForegroundColor $col
}
foreach ($v in $vers) {
    $n++
    Write-Host ''
    Write-Host ("[$n] Версия игры «" + $v.Name + '» — ' + (GB $v.Size)) -ForegroundColor White
    Write-Host '    Старая версия Minecraft в TLauncher. Новоград работает на Fabric 1.21.1 — она не трогается.'
    Write-Host '    Удаляй, если в эту версию не играешь (TLauncher скачает заново, если понадобится).' -ForegroundColor Yellow
}
if ($n -eq 0) { Write-Host ''; Write-Host 'Мусора почти нет — компьютер чистый.' -ForegroundColor Green }
else { Write-Host ''; Write-Host ('Всего можно освободить примерно: ' + (GB ($total + ($vers | ForEach-Object { $_.Size } | Measure-Object -Sum).Sum))) -ForegroundColor Cyan }

if ($big.Count -gt 0) {
    Write-Host ''
    Write-Host 'Большие файлы в Загрузках, на Рабочем столе и в Видео (их НЕ трогаю, посмотри сам):' -ForegroundColor Yellow
    foreach ($b in $big) { Write-Host ('    ' + (GB $b.Length) + '  ' + $b.FullName) }
}

if ($n -gt 0) {
    Write-Host ''
    Write-Host '============================================' -ForegroundColor Cyan
    Write-Host '   Шаг 2. Что удалить? (1 — удалить, 0 — оставить)' -ForegroundColor Cyan
    Write-Host '============================================' -ForegroundColor Cyan
    $k = 0
    foreach ($c in $found) {
        $k++
        $a = Read-Host ("[$k] " + $c.N + ' (' + (GB $c.Size) + ') — удалить? 1/0')
        if ($a.Trim() -ne '1') { Write-Host '    оставил'; continue }
        if ($c.Bin) { Clear-RecycleBin -Force -ErrorAction SilentlyContinue; Write-Host '    корзина очищена' -ForegroundColor Green; continue }
        if ($c.WU) { Stop-Service wuauserv -Force; Stop-Service bits -Force }
        $ok = 0; $skip = 0
        foreach ($f in $c.List) {
            Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $f.FullName) { $skip++ } else { $ok++ }
        }
        if ($c.WU) { Start-Service bits; Start-Service wuauserv }
        Write-Host ("    удалено файлов: $ok" + $(if ($skip) { " (заняты программами, пропущено: $skip)" } else { '' })) -ForegroundColor Green
    }
    foreach ($v in $vers) {
        $k++
        $a = Read-Host ("[$k] Версия «" + $v.Name + '» (' + (GB $v.Size) + ') — удалить? 1/0')
        if ($a.Trim() -ne '1') { Write-Host '    оставил'; continue }
        Remove-Item -LiteralPath $v.Path -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $v.Path) { Write-Host '    не удалилось целиком (игра запущена?)' -ForegroundColor Yellow } else { Write-Host '    удалено' -ForegroundColor Green }
    }
}

# ============================================================
#   Шаг 3. Освободить больше места и ускорить ноутбук
#   Каждый пункт: что делает, сколько даст, чем платишь. 1 — сделать, 0 — пропустить.
# ============================================================
function Ask($title, $what, $cost) {
    Write-Host ''
    Write-Host $title -ForegroundColor White
    Write-Host ('    Что делает: ' + $what)
    Write-Host ('    Минус: ' + $cost) -ForegroundColor Yellow
    $a = Read-Host '    Сделать? 1/0'
    return $a.Trim() -eq '1'
}

Write-Host ''
Write-Host '============================================' -ForegroundColor Cyan
Write-Host '   Шаг 3. Больше места на C и быстрее ноутбук' -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan

# 3.1 Гибернация: файл C:\hiberfil.sys размером с половину оперативной памяти
$hib = Get-Item 'C:\hiberfil.sys' -Force -ErrorAction SilentlyContinue
if ($hib -and $hib.Length -gt 100MB) {
    if (Ask ('[3.1] Отключить гибернацию — освободит ' + (GB $hib.Length)) 'удаляет файл гибернации C:\hiberfil.sys' 'пропадёт режим «Гибернация» и «Быстрый запуск»; обычный «Сон» работает как раньше') {
        powercfg /h off
        Write-Host '    готово' -ForegroundColor Green
    }
}

# 3.2 Старые копии обновлений Windows (папка WinSxS)
if (Ask '[3.2] Очистить старые копии обновлений Windows — обычно 1–5 ГБ' 'встроенная очистка Windows (DISM): удаляет заменённые версии системных файлов' 'идёт 5–20 минут; установленные обновления нельзя будет удалить') {
    Write-Host '    Работаю, не закрывай окно...'
    Dism.exe /Online /Cleanup-Image /StartComponentCleanup /NoRestart | Out-Null
    Write-Host '    готово' -ForegroundColor Green
}

# 3.3 Кэш «Оптимизации доставки» (раздача обновлений другим компьютерам)
if (Ask '[3.3] Очистить кэш раздачи обновлений' 'удаляет скачанные куски обновлений из кэша оптимизации доставки' 'нет') {
    try { Delete-DeliveryOptimizationCache -Force -ErrorAction Stop } catch { Remove-Item "$env:WINDIR\SoftwareDistribution\DeliveryOptimization\*" -Recurse -Force }
    Write-Host '    готово' -ForegroundColor Green
}

# 3.4 Папка старой Windows после обновления
$old = 'C:\Windows.old'
if (Test-Path $old) {
    $s = (Get-ChildItem $old -Recurse -Force -File | Measure-Object Length -Sum).Sum
    if (Ask ('[3.4] Удалить C:\Windows.old — освободит ' + (GB $s)) 'удаляет копию прошлой версии Windows, оставшуюся после большого обновления' 'вернуться к прошлой версии Windows будет нельзя') {
        takeown /F $old /R /A /D Y | Out-Null
        icacls $old /grant '*S-1-5-32-544:F' /T /C /Q | Out-Null
        Remove-Item $old -Recurse -Force
        Write-Host '    готово' -ForegroundColor Green
    }
}

# 3.5 Точки восстановления: ограничить место
if (Ask '[3.5] Ограничить точки восстановления до 5% диска' 'Windows хранит копии системы для отката; часто они занимают 10% диска и больше' 'старые точки восстановления удалятся, останутся самые свежие') {
    vssadmin resize shadowstorage /for=C: /on=C: /maxsize=5% | Out-Null
    Write-Host '    готово' -ForegroundColor Green
}

# 3.6 Сжатие системных файлов
if (Ask '[3.6] Сжать файлы Windows (CompactOS) — обычно 2–4 ГБ' 'Windows хранит свои файлы сжатыми; включается встроенной командой compact' 'идёт 10–20 минут; процессор чуть больше работает при запуске программ (почти незаметно)') {
    Write-Host '    Работаю, не закрывай окно...'
    compact.exe /CompactOS:always | Out-Null
    Write-Host '    готово' -ForegroundColor Green
}

# 3.7 Контроль памяти: Windows сама чистит мусор
if (Ask '[3.7] Включить «Контроль памяти»' 'Windows сама раз в месяц очищает временные файлы и корзину старше 30 дней' 'файлы в корзине старше 30 дней удаляются сами') {
    $k = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\StorageSense\Parameters\StoragePolicy'
    New-Item -Path $k -Force | Out-Null
    Set-ItemProperty -Path $k -Name '01' -Value 1 -Type DWord
    Set-ItemProperty -Path $k -Name '2048' -Value 30 -Type DWord
    Write-Host '    готово (Параметры — Система — Память)' -ForegroundColor Green
}

# 3.8 Электропитание
if (Ask '[3.8] Режим «Высокая производительность»' 'процессор не сбавляет частоту — игры и программы работают быстрее' 'от батареи ноутбук разряжается быстрее (на зарядке — разницы нет)') {
    powercfg -setactive SCHEME_MIN
    Write-Host '    готово (вернуть: Панель управления — Электропитание — Сбалансированная)' -ForegroundColor Green
}

# 3.9 Визуальные эффекты
if (Ask '[3.9] Отключить анимации и прозрачность Windows' 'окна открываются без анимации, нет прозрачности — меньше нагрузка на встроенную видеокарту' 'Windows выглядит проще; изменения видны после перезахода в систему') {
    Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\VisualEffects' -Name VisualFXSetting -Value 2 -Type DWord
    Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize' -Name EnableTransparency -Value 0 -Type DWord
    Set-ItemProperty -Path 'HKCU:\Control Panel\Desktop\WindowMetrics' -Name MinAnimate -Value '0'
    Write-Host '    готово' -ForegroundColor Green
}

# 3.10 Игровой режим
if (Ask '[3.10] Включить «Игровой режим» Windows' 'во время игры Windows не ставит обновления и отдаёт игре больше ресурсов' 'нет') {
    New-Item -Path 'HKCU:\Software\Microsoft\GameBar' -Force | Out-Null
    Set-ItemProperty -Path 'HKCU:\Software\Microsoft\GameBar' -Name AutoGameModeEnabled -Value 1 -Type DWord
    Set-ItemProperty -Path 'HKCU:\Software\Microsoft\GameBar' -Name AllowAutoGameMode -Value 1 -Type DWord
    Write-Host '    готово' -ForegroundColor Green
}

# 3.11 Автозагрузка — только показываю
$start = @(Get-CimInstance Win32_StartupCommand | Select-Object Name, Command)
if ($start.Count -gt 0) {
    Write-Host ''
    Write-Host '[3.11] Программы, которые запускаются вместе с Windows (сам их не выключаю):' -ForegroundColor White
    foreach ($s in $start) { Write-Host ('    • ' + $s.Name) }
    Write-Host '    Выключить лишние: Ctrl+Shift+Esc — вкладка «Автозагрузка» — ПКМ — «Отключить».' -ForegroundColor Yellow
    Write-Host '    Оставь антивирус и драйверы звука/видео (Realtek, AMD). Discord, Steam, лаунчеры — можно отключить.'
}

$free1 = FreeC
Write-Host ''
Write-Host '============================================' -ForegroundColor Cyan
Write-Host ('   ГОТОВО. Было свободно: ' + (GB $free0) + ', стало: ' + (GB $free1)) -ForegroundColor Cyan
Write-Host ('   Освобождено: ' + (GB ([math]::Max(0, $free1 - $free0)))) -ForegroundColor Green
Write-Host '   Твои документы, фото, игры, сохранения миров и пароли не трогались.'
Write-Host '   Совет: перезагрузи компьютер, чтобы все изменения заработали.'
Write-Host '============================================' -ForegroundColor Cyan
