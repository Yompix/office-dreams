# Unity Boost RU — установщик / удаление.
# Запускается из «Установить.bat» (или «Удалить.bat» с ключом -Uninstall).
# Нужен только встроенный в Windows PowerShell 5.1 (или новее). Ничего не скачивает:
# всё нужное лежит в папке payload рядом со скриптом.
param(
    [switch]$Uninstall,
    [string]$GameExe = "",
    [switch]$NoGui        # для проверок: без окон, ответы «да», сообщения в консоль
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$ScriptPath = $MyInvocation.MyCommand.Path
$Here    = Split-Path -Parent $ScriptPath
$Payload = Join-Path $Here "payload"
$Title   = "Unity Boost RU"
$ManifestName = "unityboost-manifest.txt"
$Sep = [System.IO.Path]::DirectorySeparatorChar
# Пути пишем по-виндовому; на других ОС (только для проверок) меняем разделитель.
function P([string]$s) { return $s.Replace([char]'\', $Sep) }

if (-not $NoGui) {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# ---------------------------------------------------------------- сообщения
function Show-Info([string]$text) {
    Write-Host $text
    if (-not $NoGui) { [void][System.Windows.Forms.MessageBox]::Show($text, $Title, "OK", "Information") }
}
function Show-Error([string]$text) {
    Write-Host "ОШИБКА: $text" -ForegroundColor Red
    if (-not $NoGui) { [void][System.Windows.Forms.MessageBox]::Show($text, $Title, "OK", "Error") }
}
function Ask-YesNo([string]$text) {
    Write-Host $text
    if ($NoGui) { return $true }
    $r = [System.Windows.Forms.MessageBox]::Show($text, $Title, "YesNo", "Warning", "Button2")
    return ($r -eq [System.Windows.Forms.DialogResult]::Yes)
}
function Step([string]$text) { Write-Host ("  - " + $text) }

# ---------------------------------------------------------------- выбор exe
function Select-GameExe([string]$prompt) {
    if ($GameExe) { return $GameExe }
    $dlg = New-Object System.Windows.Forms.OpenFileDialog
    $dlg.Title = $prompt
    $dlg.Filter = "Игра (*.exe)|*.exe"
    $dlg.Multiselect = $false
    $steam = "C:\Program Files (x86)\Steam\steamapps\common"
    if (Test-Path $steam) { $dlg.InitialDirectory = $steam }
    if ($dlg.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { return $null }
    return $dlg.FileName
}

# ---------------------------------------------------------------- разрядность PE-файла
function Get-PeArch([string]$path) {
    $fs = [System.IO.File]::OpenRead($path)
    try {
        $br = New-Object System.IO.BinaryReader($fs)
        if ($br.ReadUInt16() -ne 0x5A4D) { return $null }       # 'MZ'
        $fs.Position = 0x3C
        $peOff = $br.ReadInt32()
        $fs.Position = $peOff
        if ($br.ReadUInt32() -ne 0x00004550) { return $null }   # 'PE\0\0'
        $machine = $br.ReadUInt16()
        switch ($machine) {
            0x8664 { return "x64" }
            0x014C { return "x86" }
            0xAA64 { return "arm64" }
            default { return ("0x{0:X4}" -f $machine) }
        }
    } finally { $fs.Close() }
}

# ---------------------------------------------------------------- анализ игры
function Get-GameInfo([string]$exe) {
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw "Файл не найден: $exe" }
    $dir  = Split-Path -Parent $exe
    $name = [System.IO.Path]::GetFileNameWithoutExtension($exe)
    $data = Join-Path $dir ($name + "_Data")
    if (-not (Test-Path -LiteralPath $data -PathType Container)) {
        $cands = @(Get-ChildItem -LiteralPath $dir -Directory -Filter "*_Data" -ErrorAction SilentlyContinue |
                   Where-Object { Test-Path -LiteralPath (Join-Path $dir (($_.Name -replace '_Data$','') + ".exe")) })
        if ($cands.Count -ge 1) {
            $exeOk = ($cands[0].Name -replace '_Data$','') + ".exe"
            throw "Похоже, выбран не тот файл ($name.exe).`nВыберите главный файл игры: $exeOk"
        }
        throw "Это не похоже на Unity-игру: рядом с $name.exe нет папки ${name}_Data."
    }

    $info = [ordered]@{
        Exe = $exe; Dir = $dir; Name = $name; Data = $data
        Backend = $null; Arch = $null; Unity = ""; AntiCheat = @(); OtherLoader = ""
    }

    if (Test-Path -LiteralPath (Join-Path $dir "GameAssembly.dll")) { $info.Backend = "IL2CPP" }
    elseif (Test-Path -LiteralPath (Join-Path $data (P "Managed\Assembly-CSharp.dll"))) { $info.Backend = "Mono" }
    elseif (Test-Path -LiteralPath (Join-Path $data (P "Managed\UnityEngine.dll"))) { $info.Backend = "Mono" }
    else { throw "Не удалось понять тип игры: нет ни GameAssembly.dll, ни ${name}_Data\Managed." }

    $player = Join-Path $dir "UnityPlayer.dll"
    $archFile = $exe
    if ($info.Backend -eq "IL2CPP") { $archFile = Join-Path $dir "GameAssembly.dll" }
    elseif (Test-Path -LiteralPath $player) { $archFile = $player }
    $info.Arch = Get-PeArch $archFile
    if ($info.Arch -ne "x64" -and $info.Arch -ne "x86") { throw "Неподдерживаемая разрядность игры: $($info.Arch)." }

    $verFile = $exe
    if (Test-Path -LiteralPath $player) { $verFile = $player }
    try { $info.Unity = ([System.Diagnostics.FileVersionInfo]::GetVersionInfo($verFile)).ProductVersion } catch { }
    if (-not $info.Unity) { $info.Unity = "" }

    # Античиты: с ними моды = риск бана.
    $ac = @()
    foreach ($d in @("EasyAntiCheat", "EasyAntiCheat_EOS", "BattlEye", "BattlEye_Launcher", "XignCode", "GameGuard", "nProtect")) {
        if (Test-Path -LiteralPath (Join-Path $dir $d)) { $ac += $d }
    }
    foreach ($pat in @("start_protected_game.exe", "EasyAntiCheat*.exe", "BEService*.exe", "*_BE.exe", "mhyprot*.sys", "xhunter*.sys")) {
        if (@(Get-ChildItem -LiteralPath $dir -Filter $pat -File -ErrorAction SilentlyContinue).Count -gt 0) { $ac += $pat }
    }
    $info.AntiCheat = $ac

    if (Test-Path -LiteralPath (Join-Path $dir "MelonLoader")) { $info.OtherLoader = "MelonLoader" }
    return $info
}

function Test-Writable([string]$dir) {
    $probe = Join-Path $dir (".unityboost_" + [guid]::NewGuid().ToString("N") + ".tmp")
    try { [System.IO.File]::WriteAllText($probe, "x"); Remove-Item -LiteralPath $probe -Force; return $true }
    catch { return $false }
}

function Restart-AsAdmin([string]$exe) {
    $argList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-File", "`"$ScriptPath`"", "-GameExe", "`"$exe`"")
    if ($Uninstall) { $argList += "-Uninstall" }
    Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Verb RunAs | Out-Null
}

# ---------------------------------------------------------------- распаковка с учётом созданных файлов
function Expand-ZipTracked([string]$zip, [string]$dest, [System.Collections.Generic.List[string]]$created) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
    try {
        foreach ($e in $archive.Entries) {
            $rel = $e.FullName.Replace([char]'/', $Sep)
            # changelog.txt из архива BepInEx не нужен и может затереть одноимённый файл игры.
            if ($rel -ieq "changelog.txt") { continue }
            $target = [System.IO.Path]::GetFullPath((Join-Path $dest $rel))
            if (-not $target.StartsWith([System.IO.Path]::GetFullPath($dest), [System.StringComparison]::OrdinalIgnoreCase)) { continue }
            if ($rel.EndsWith([string]$Sep)) {
                if (-not (Test-Path -LiteralPath $target)) { [void][System.IO.Directory]::CreateDirectory($target) }
                continue
            }
            $parent = Split-Path -Parent $target
            if (-not (Test-Path -LiteralPath $parent)) { [void][System.IO.Directory]::CreateDirectory($parent) }
            if (-not (Test-Path -LiteralPath $target)) { $created.Add($rel) }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        }
    } finally { $archive.Dispose() }
}

function Copy-Tracked([string]$src, [string]$dest, [string]$rel, [System.Collections.Generic.List[string]]$created, [switch]$KeepExisting) {
    $target = Join-Path $dest $rel
    $parent = Split-Path -Parent $target
    if (-not (Test-Path -LiteralPath $parent)) { [void][System.IO.Directory]::CreateDirectory($parent) }
    if (Test-Path -LiteralPath $target) {
        if ($KeepExisting) { return $false }
    } else { $created.Add($rel) }
    Copy-Item -LiteralPath $src -Destination $target -Force
    return $true
}

# Поменять key=value в ini-файле, не трогая остальное.
function Set-IniValue([string]$path, [string]$section, [string]$key, [string]$value) {
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path -LiteralPath $path) { foreach ($l in [System.IO.File]::ReadAllLines($path)) { $lines.Add($l) } }
    $inSec = $false; $secIdx = -1; $done = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $t = $lines[$i].Trim()
        if ($t -match '^\[(.+)\]$') {
            if ($inSec -and -not $done) { $lines.Insert($i, "$key=$value"); $done = $true; break }
            $inSec = ($Matches[1] -eq $section)
            if ($inSec) { $secIdx = $i }
            continue
        }
        if ($inSec -and ($t -match ('^' + [regex]::Escape($key) + '\s*='))) { $lines[$i] = "$key=$value"; $done = $true; break }
    }
    if (-not $done) {
        if ($secIdx -lt 0) { $lines.Add(""); $lines.Add("[$section]") }
        $lines.Add("$key=$value")
    }
    [System.IO.File]::WriteAllLines($path, $lines.ToArray())
}

# ---------------------------------------------------------------- установка
function Install-Boost($g) {
    $dir = $g.Dir
    $bepDir = Join-Path $dir "BepInEx"
    $bepExisted = Test-Path -LiteralPath $bepDir
    $created = New-Object System.Collections.Generic.List[string]

    # Уже стоит BepInEx другого поколения — не смешиваем.
    $hasV5 = Test-Path -LiteralPath (Join-Path $bepDir (P "core\BepInEx.dll"))
    $hasV6 = Test-Path -LiteralPath (Join-Path $bepDir (P "core\BepInEx.Core.dll"))
    $manifestPath = Join-Path $bepDir $ManifestName
    $ours = Test-Path -LiteralPath $manifestPath
    if ($g.Backend -eq "Mono" -and $hasV6) { throw "В игре уже стоит BepInEx 6. Для этой игры нужен BepInEx 5.`nСначала удалите старые моды (папку BepInEx)." }
    if ($g.Backend -eq "IL2CPP" -and $hasV5) { throw "В игре уже стоит BepInEx 5, а игре нужен BepInEx 6 (IL2CPP).`nСначала удалите старые моды (папку BepInEx)." }
    if ($bepExisted -and -not $ours) {
        if (-not (Ask-YesNo "В игре уже есть BepInEx (другие моды).`nUnity Boost добавит свои файлы к ним. Продолжить?")) { return $false }
    }

    $old = @()
    $bepCreatedFlag = -not $bepExisted
    if ($ours) {
        $prev = [System.IO.File]::ReadAllLines($manifestPath)
        $old = @($prev | Where-Object { $_ -and -not $_.StartsWith("#") })
        if ($prev -contains "#bepinex-created") { $bepCreatedFlag = $true }
    }

    if ($g.Backend -eq "Mono") {
        $bepZip = Join-Path $Payload ("bep5_" + $g.Arch + ".zip")
        $xuZip  = Join-Path $Payload "xu_bep5.zip"
        $dll    = Join-Path $Payload (P "unityboost\bep5\UnityBoost.dll")
    } else {
        $bepZip = Join-Path $Payload ("bep6_" + $g.Arch + ".zip")
        $xuZip  = Join-Path $Payload "xu_il2cpp.zip"
        $dll    = Join-Path $Payload (P "unityboost\il2cpp\UnityBoost.dll")
    }
    foreach ($f in @($bepZip, $xuZip, $dll)) { if (-not (Test-Path -LiteralPath $f)) { throw "Не хватает файла установщика: $f`nРаспакуйте архив целиком и запустите снова." } }

    Step "BepInEx ($($g.Backend), $($g.Arch))"
    Expand-ZipTracked $bepZip $dir $created
    Step "переводчик XUnity.AutoTranslator"
    Expand-ZipTracked $xuZip $dir $created
    Step "Unity Boost"
    [void](Copy-Tracked $dll $dir (P "BepInEx\plugins\UnityBoost\UnityBoost.dll") $created)

    Step "настройки"
    $cfgDir = Join-Path $bepDir "config"
    if (-not (Test-Path -LiteralPath $cfgDir)) { [void][System.IO.Directory]::CreateDirectory($cfgDir) }
    $xuCfg = Join-Path $cfgDir "AutoTranslatorConfig.ini"
    if (-not (Copy-Tracked (Join-Path $Payload "AutoTranslatorConfig.ini") $dir (P "BepInEx\config\AutoTranslatorConfig.ini") $created -KeepExisting)) {
        # Уже был конфиг — меняем только язык и переводчик.
        Set-IniValue $xuCfg "Service" "Endpoint" "GoogleTranslateV2"
        Set-IniValue $xuCfg "Service" "FallbackEndpoint" "BingTranslate"
        Set-IniValue $xuCfg "General" "Language" "ru"
        Set-IniValue $xuCfg "General" "FromLanguage" "en"
    }
    [void](Copy-Tracked (Join-Path $Payload "UnityBoost.cfg") $dir (P "BepInEx\config\UnityBoost.cfg") $created -KeepExisting)
    if ($g.Backend -eq "Mono") {
        [void](Copy-Tracked (Join-Path $Payload "BepInEx5.cfg") $dir (P "BepInEx\config\BepInEx.cfg") $created -KeepExisting)
    }

    # Файлы, скачанные из интернета, Windows может пометить как «заблокированные».
    try { Get-ChildItem -LiteralPath $bepDir -Recurse -File | Unblock-File -ErrorAction SilentlyContinue } catch { }

    # Список наших файлов — для «Удалить.bat».
    $all = New-Object System.Collections.Generic.List[string]
    foreach ($x in $old) { if (-not $all.Contains($x)) { $all.Add($x) } }
    foreach ($x in $created) { if (-not $all.Contains($x)) { $all.Add($x) } }
    $hdr = @("# Unity Boost RU: файлы, которые поставил установщик (для удаления).",
             "# backend=$($g.Backend) arch=$($g.Arch) unity=$($g.Unity) date=$((Get-Date).ToString('yyyy-MM-dd HH:mm'))")
    if ($bepCreatedFlag) { $hdr += "#bepinex-created" }
    [System.IO.File]::WriteAllLines($manifestPath, [string[]]($hdr + $all.ToArray()))
    return $true
}

# ---------------------------------------------------------------- удаление
function Uninstall-Boost([string]$dir) {
    $bepDir = Join-Path $dir "BepInEx"
    $manifestPath = Join-Path $bepDir $ManifestName
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        if (Test-Path -LiteralPath (Join-Path $bepDir (P "plugins\UnityBoost\UnityBoost.dll"))) {
            throw "Не найден список файлов Unity Boost ($ManifestName).`nУдалите вручную: папку BepInEx, файлы winhttp.dll, doorstop_config.ini, .doorstop_version (и папку dotnet для IL2CPP-игр)."
        }
        throw "В этой игре Unity Boost не установлен."
    }
    $lines = [System.IO.File]::ReadAllLines($manifestPath)
    $bepCreated = $lines -contains "#bepinex-created"
    $files = @($lines | Where-Object { $_ -and -not $_.StartsWith("#") })

    $msg = "Удалить Unity Boost RU из папки игры?`n$dir"
    if ($bepCreated) { $msg += "`n`nПапка BepInEx будет удалена целиком (вместе с кэшем переводов)." }
    if (-not (Ask-YesNo $msg)) { return $false }

    $removed = 0
    foreach ($rel in $files) {
        if ($bepCreated -and $rel.StartsWith("BepInEx" + $Sep, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
        $p = Join-Path $dir $rel
        if (Test-Path -LiteralPath $p -PathType Leaf) { Remove-Item -LiteralPath $p -Force; $removed++ }
    }
    if ($bepCreated) {
        Remove-Item -LiteralPath $bepDir -Recurse -Force
        $removed++
    } else {
        Remove-Item -LiteralPath $manifestPath -Force
    }
    # Пустые папки, которые остались от нас (например, dotnet).
    $dirs = @($files | ForEach-Object { Split-Path -Parent $_ } | Where-Object { $_ } | Sort-Object -Unique | Sort-Object -Descending -Property Length)
    foreach ($d in $dirs) {
        $p = Join-Path $dir $d
        while ($p.Length -gt $dir.Length -and (Test-Path -LiteralPath $p) -and
               @(Get-ChildItem -LiteralPath $p -Force -ErrorAction SilentlyContinue).Count -eq 0) {
            Remove-Item -LiteralPath $p -Force
            $p = Split-Path -Parent $p
        }
    }
    return $true
}

# ================================================================ main
try {
    Write-Host ""
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ""

    if ($Uninstall) {
        $exe = Select-GameExe "Удаление Unity Boost: выберите .exe игры"
        if (-not $exe) { exit 0 }
        $dir = Split-Path -Parent $exe
        if (-not (Test-Writable $dir)) {
            if (Ask-YesNo "Нет прав на запись в папку игры.`nЗапустить удаление от имени администратора?") { Restart-AsAdmin $exe }
            exit 0
        }
        if (Uninstall-Boost $dir) { Show-Info "Готово: Unity Boost удалён.`nИгра снова как была." }
        exit 0
    }

    $exe = Select-GameExe "Unity Boost: выберите .exe игры (например, из папки Steam\steamapps\common)"
    if (-not $exe) { exit 0 }
    $g = Get-GameInfo $exe
    Write-Host ("Игра: {0}`nUnity: {1}   Тип: {2}   Разрядность: {3}" -f $g.Name, $g.Unity, $g.Backend, $g.Arch)

    if ($g.OtherLoader) { throw "В игре уже стоит $($g.OtherLoader). Вместе с ним Unity Boost работать не будет." }
    if ($g.AntiCheat.Count -gt 0) {
        $t = "В игре найден античит: " + ($g.AntiCheat -join ", ") + ".`n`n" +
             "Моды в играх с античитом могут привести к БАНУ аккаунта.`n" +
             "Ставьте только в одиночные игры без онлайна.`n`nВсё равно установить?"
        if (-not (Ask-YesNo $t)) { exit 0 }
    }
    if (-not (Test-Writable $g.Dir)) {
        if (Ask-YesNo "Нет прав на запись в папку игры.`nЗапустить установку от имени администратора?") { Restart-AsAdmin $exe }
        exit 0
    }

    if (-not (Install-Boost $g)) { exit 0 }

    $first = ""
    if ($g.Backend -eq "IL2CPP") {
        $first = "`n`nПЕРВЫЙ запуск будет долгим (от 1 до 10 минут) и с чёрным окном текста: " +
                 "BepInEx готовит игру. Нужен интернет. Не закрывайте — дальше игра запускается как обычно."
    }
    Show-Info ("Готово! Unity Boost RU установлен в:`n$($g.Dir)`n`n" +
               "Тип игры: $($g.Backend) $($g.Arch), Unity $($g.Unity)`n`n" +
               "В игре:`n  F9 — пресет (Слабый ноутбук / Средний / Выкл)`n  F10 — счётчик FPS`n" +
               "  Alt+T — оригинал / перевод`n`nПеревод машинный (Google), нужен интернет; " +
               "переведённое запоминается и потом работает без сети." + $first)
    exit 0
}
catch {
    Show-Error ($_.Exception.Message)
    exit 1
}
