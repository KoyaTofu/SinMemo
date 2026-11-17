<#
.SYNOPSIS
    7.1 総合テスト用: 端末のデバッグ版の SinMemo（com.example.sinmemo.debug）の DB を、テスト用のメモ 1,000 件に置き換える／元に戻す。

.DESCRIPTION
    docs/総合テスト/テスト設計.md 5.2 の手順（使い方は 5.2.0）で使う（スケジュール 決定事項 8: adb で入れる、アプリのコードは変えない）。
      backup  端末の DB を PC に保存するだけ
      seed    まず backup し、そのあと DB をテスト用 1,000 件だけの DB に置き換える
      restore backup で保存した DB を端末に戻す（-From で保存先を指定。省略すると、テスト用でない最新の保存）
    - デバッグ版でしか動かない（adb shell run-as を使うため）。7.4 からデバッグ版は別のアプリ（com.example.sinmemo.debug）なので、
      リリース版（com.example.sinmemo）のメモには触れない
    - -OverLimit を付けると、10,050 文字の「上限を超えたメモ」を 1 件足す（docs/総合テスト/テスト設計.md 5.6）
    - DB は PC の sqlite3.exe（Android SDK の platform-tools にある）で作る。中身は乱数の種を固定しているので毎回同じ
    - 保存先は既定で %USERPROFILE%\SinMemo-backup\<日時>\（リポジトリの外。ユーザーのメモが入るため）

.EXAMPLE
    .\scripts\seed_memos.ps1 seed
    .\scripts\seed_memos.ps1 restore
    .\scripts\seed_memos.ps1 seed -Serial emulator-5554
    .\scripts\seed_memos.ps1 seed -OverLimit
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('backup', 'seed', 'restore')]
    [string]$Command,
    [int]$Count = 1000,
    [string]$Serial,
    [string]$From,
    [string]$BackupRoot = (Join-Path $env:USERPROFILE 'SinMemo-backup'),
    [switch]$OverLimit
)

$ErrorActionPreference = 'Stop'
# デバッグ版のパッケージ名（app/build.gradle.kts の applicationIdSuffix。docs/共通/基本設計.md 6.1）
$Package = 'com.example.sinmemo.debug'
$DbName = 'sinmemo.db'
# app/schemas/.../1.json の identityHash。Room はこれが合わない DB を開かない（スキーマを変えたらここも直す）。
# ここの値（$Package・$DbName・$IdentityHash・$DbVersion と CREATE 文）がアプリと合うことは
# 単体テスト SeedScriptConsistencyTest で確かめる（docs/共通/テスト設計.md 2.4）
$IdentityHash = '64ddaf9d41e535c98606d7848fd56381'
$DbVersion = 1
# テスト用の DB に付ける印（SQLite のヘッダの application_id。Room は見ない）。0x53454544 = 'SEED'
$SeedMark = 1397048644

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbExe = Join-Path $sdk 'platform-tools\adb.exe'
$sqliteExe = Join-Path $sdk 'platform-tools\sqlite3.exe'
foreach ($exe in $adbExe, $sqliteExe) {
    if (-not (Test-Path $exe)) { throw "見つからない: $exe（Android SDK の platform-tools を入れる。場所が違えば ANDROID_HOME を設定）" }
}

function Adb {
    $a = @(); if ($Serial) { $a += '-s', $Serial }
    # PowerShell 5.1 は外部コマンドの標準エラーをエラー扱いにするので、ここだけ止めずに終了コードで判定する
    $ErrorActionPreference = 'Continue'
    $out = & $adbExe @a @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') が失敗: $out" }
    $out
}

function RunAs([string]$shellCommand) {
    # 端末の中でアプリの権限で動かす（デバッグ版のみ）
    Adb shell "run-as $Package sh -c '$shellCommand'"
}

function Assert-Ready {
    $devices = @(& $adbExe devices | Select-String "`tdevice$")
    if ($devices.Count -eq 0) { throw '端末が見つからない。USB デバッグを有効にしてつなぎ、スマホの「USB デバッグを許可」で許可する' }
    if ($devices.Count -gt 1 -and -not $Serial) { throw "端末が複数ある。-Serial で選ぶ:`n$($devices -join "`n")" }
    $ErrorActionPreference = 'Continue'
    $check = & $adbExe @(if ($Serial) { '-s', $Serial }) shell "run-as $Package true" 2>&1 | ForEach-Object { "$_" }
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0 -or "$check" -match 'not debuggable|unknown package') {
        throw "run-as が使えない: $check（デバッグ版の SinMemo を入れる）"
    }
}

function Backup-Db {
    Adb shell am force-stop $Package | Out-Null
    $dir = Join-Path $BackupRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
    New-Item -ItemType Directory -Force $dir | Out-Null
    # 一度もアプリを開いていないと databases フォルダがなく ls が失敗するので、終了コードを 0 にする
    $files = @(RunAs "ls databases 2>/dev/null; true" | Where-Object { $_ -match "^$DbName(-wal|-shm)?$" })
    foreach ($f in $files) {
        # exec-out はバイナリをそのまま出す。PowerShell 5.1 のパイプはバイナリを壊すので cmd に任せる
        $target = Join-Path $dir $f
        $a = if ($Serial) { "-s $Serial " } else { '' }
        cmd /c "`"$adbExe`" $a exec-out run-as $Package cat databases/$f > `"$target`""
        if ($LASTEXITCODE -ne 0) { throw "$f の保存に失敗" }
    }
    if ($files.Count -eq 0) { Write-Host '端末に DB がまだない（一度もアプリを開いていない）。空の保存先だけ作った' }
    if ($files -contains $DbName -and (Test-SeedDb $dir)) {
        # すでにテスト用の DB だった（seed を 2 回続けた等）。restore で既定に選ばれないよう名前に印を付ける
        Rename-Item $dir "$(Split-Path $dir -Leaf)-seed"
        $dir = "$dir-seed"
        Write-Host '端末の DB はテスト用のものだった。ユーザーのメモは、これより前の保存にある'
    }
    Write-Host "保存した: $dir（$($files -join ', ')）"
    $dir
}

function Test-SeedDb([string]$dir) {
    # 保存したファイルを変えないよう、一時フォルダに写してから application_id（テスト用の印）を読む
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) "sinmemo-check-$PID"
    New-Item -ItemType Directory -Force $tmp | Out-Null
    try {
        Get-ChildItem $dir -Filter "$DbName*" | Copy-Item -Destination $tmp
        $id = & $sqliteExe (Join-Path $tmp $DbName) 'PRAGMA application_id;'
        return ("$id".Trim() -eq "$SeedMark")
    } finally {
        Remove-Item -Recurse -Force $tmp
    }
}

function Push-Db([string]$localDb, [string]$localWal) {
    Adb shell am force-stop $Package | Out-Null
    $tmp = "/data/local/tmp/$Package-$DbName"
    Adb push $localDb $tmp | Out-Null
    $cmd = "mkdir -p databases && cp $tmp databases/$DbName && rm -f databases/$DbName-wal databases/$DbName-shm"
    if ($localWal) {
        Adb push $localWal "$tmp-wal" | Out-Null
        $cmd += " && cp $tmp-wal databases/$DbName-wal"
    }
    # /data/local/tmp からの写しは誰でも読める権限（666）になるので、アプリが自分で作るときと同じ 660 にする
    $cmd += " && chmod 660 databases/$DbName*"
    RunAs $cmd | Out-Null
    Adb shell rm -f $tmp "$tmp-wal" | Out-Null
}

# ---- テスト用のメモを作る（決定事項: 長さを混ぜる。乱数の種は固定） ----------------------------------

function New-MemoBodies([int]$n) {
    $rnd = [System.Random]::new(20261115)
    $items = '牛乳', '卵', 'パン', 'トマト', '玉ねぎ', 'コーヒー豆', '洗剤', '電池', '封筒', 'ノート', '豆腐', 'りんご'
    $places = '会議室A', '駅前のカフェ', '本社', 'オンライン', '図書館', '実家'
    $topics = 'Kotlin のコルーチン', 'Room の移行', '旅行の行き先', '確定申告', '自転車の修理', '新しい本棚', 'Compose のアニメーション'
    $names = '佐藤', '鈴木', '高橋', '田中', '伊藤', '山本'
    $sentences = @(
        '今日は思ったより早く終わったので、残りの時間で次の準備をした。',
        '大事なのは、先に全体の流れを決めてから細かいところに入ること。',
        '次に会うときまでに、資料を一度読み直しておく。',
        '雨の日は道が混むので、いつもより十分早く出る。',
        '忘れないうちに書いておく。あとで見返して整理する。',
        'うまくいかなかった理由を三つ挙げて、ひとつずつ試す。'
    )
    function Pick($arr) { $arr[$rnd.Next($arr.Count)] }
    function Line {
        switch ($rnd.Next(5)) {
            0 { "買う物: $(Pick $items)、$(Pick $items)、$(Pick $items)" }
            1 { "$($rnd.Next(9, 19)):$('{0:00}' -f ($rnd.Next(4) * 15)) から $(Pick $places) で打ち合わせ" }
            2 { "$(Pick $topics) について調べる" }
            3 { "電話: $(Pick $names)さんに折り返す" }
            default { Pick $sentences }
        }
    }
    $longIndexes = @(7, 150, 333, 512, 777)          # 上限近く（9,000〜10,000 文字）
    $blankHead = @(42, 420)                          # 先頭が空行（一覧は空でない最初の行を出す。メモ一覧 基本設計 3.1）
    for ($i = 0; $i -lt $n; $i++) {
        if ($longIndexes -contains $i) {
            $sb = [System.Text.StringBuilder]::new("長いメモ $($i + 1)`n")
            $target = $rnd.Next(9000, 10001)
            while ($sb.Length -lt $target) { [void]$sb.Append((Pick $sentences)).Append("`n") }
            $sb.ToString().Substring(0, $target)
        } elseif ($rnd.Next(100) -lt 13) {
            $lines = 1..$rnd.Next(4, 20) | ForEach-Object { if ($rnd.Next(3) -eq 0) { Pick $sentences } else { Line } }
            ($(if ($blankHead -contains $i) { "`n`n" } else { '' }) + ($lines -join "`n"))
        } else {
            $(if ($blankHead -contains $i) { "`n`n" } else { '' }) + (Line)
        }
    }
}

function New-SeedDb([string]$path, [int]$n) {
    $now = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    $yearMs = 365L * 24 * 60 * 60 * 1000
    $rnd = [System.Random]::new(115)
    $sql = [System.Text.StringBuilder]::new()
    [void]$sql.AppendLine("PRAGMA user_version = $DbVersion;")
    [void]$sql.AppendLine("PRAGMA application_id = $SeedMark;")
    [void]$sql.AppendLine('BEGIN;')
    [void]$sql.AppendLine('CREATE TABLE IF NOT EXISTS `memos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL);')
    [void]$sql.AppendLine('CREATE INDEX IF NOT EXISTS `index_memos_updated_at` ON `memos` (`updated_at`);')
    [void]$sql.AppendLine('CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);')
    [void]$sql.AppendLine("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$IdentityHash');")
    $longRank = 0
    foreach ($body in (New-MemoBodies $n)) {
        $updated = $now - [long]($rnd.NextDouble() * $yearMs)
        # 上限近くの長いメモは、開く確認をしやすいよう一覧の先頭（更新日時が新しい順）に並べる
        if ($body.StartsWith('長いメモ')) { $longRank++; $updated = $now - $longRank * 60000L }
        $created = $updated - [long]($rnd.NextDouble() * 30 * 24 * 60 * 60 * 1000)
        $escaped = $body.Replace("'", "''")
        [void]$sql.AppendLine("INSERT INTO memos (body, created_at, updated_at) VALUES('$escaped', $created, $updated);")
    }
    if ($OverLimit) {
        # 上限（10,000 文字）を超えたメモ。上限を超えた本文を減らせることの確認に使う（docs/メモ編集/テスト設計.md E-D-09）。
        # 一覧では「長いメモ」の次に並べる
        $sb = [System.Text.StringBuilder]::new("上限を超えたメモ`n")
        while ($sb.Length -lt 10050) { [void]$sb.Append('上限を超えた本文を減らせることを確かめる。').Append("`n") }
        $over = $sb.ToString().Substring(0, 10050)
        $updated = $now - ($longRank + 1) * 60000L
        [void]$sql.AppendLine("INSERT INTO memos (body, created_at, updated_at) VALUES('$over', $updated, $updated);")
    }
    [void]$sql.AppendLine('COMMIT;')
    $sqlFile = "$path.sql"
    # sqlite3 は BOM 付きの UTF-8 を読めないので BOM なしで書く
    [System.IO.File]::WriteAllText($sqlFile, $sql.ToString(), [System.Text.UTF8Encoding]::new($false))
    if (Test-Path $path) { Remove-Item $path }
    & $sqliteExe $path ".read '$($sqlFile.Replace('\', '/'))'"
    if ($LASTEXITCODE -ne 0) { throw 'sqlite3 で DB を作れなかった' }
    Remove-Item $sqlFile
    $check = & $sqliteExe $path 'SELECT COUNT(*), MAX(LENGTH(body)), MIN(updated_at), MAX(updated_at) FROM memos;'
    Write-Host "作った DB: 件数|最長の文字数|最古|最新 = $check"
}

# ---- 本体 ---------------------------------------------------------------------------------------

Assert-Ready
switch ($Command) {
    'backup' { Backup-Db | Out-Null }
    'seed' {
        $dir = Backup-Db
        $seed = Join-Path $dir "seed-$Count.db"
        New-SeedDb $seed $Count
        Push-Db $seed $null
        $total = if ($OverLimit) { $Count + 1 } else { $Count }
        Write-Host "端末の DB をテスト用 $total 件に置き換えた。元に戻すとき: .\scripts\seed_memos.ps1 restore（テスト用でない最新の保存を戻す）"
    }
    'restore' {
        if (-not $From) {
            # 最新の保存のうち、テスト用の DB でないもの（名前が -seed で終わらないもの）
            $From = (Get-ChildItem $BackupRoot -Directory | Where-Object { $_.Name -notlike '*-seed' } |
                Sort-Object Name | Select-Object -Last 1).FullName
            if (-not $From) { throw "保存したものがない: $BackupRoot" }
        }
        $db = Join-Path $From $DbName
        if (-not (Test-Path $db)) { throw "$db がない（保存したときに DB がなかった場合は、アプリの「データを消去」で空に戻す）" }
        $wal = Join-Path $From "$DbName-wal"
        Push-Db $db $(if (Test-Path $wal) { $wal } else { $null })
        Write-Host "戻した: $From"
    }
}
