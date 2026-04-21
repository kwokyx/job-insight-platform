param(
    [string]$MysqlHost = $(if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "localhost" }),
    [int]$MysqlPort = $(if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 }),
    [string]$MysqlUser = $(if ($env:MYSQL_USERNAME) { $env:MYSQL_USERNAME } else { "career" }),
    [string]$MysqlPassword = $(if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "career2026" }),
    [string]$Database = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "career_platform" }),
    [string]$MysqlExe = $(if ($env:MYSQL_EXE) { $env:MYSQL_EXE } else { "" }),
    [switch]$FullRefresh
)

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$sqlPath = if ($FullRefresh) {
    Join-Path $repoRoot "config\sync_crawl_to_business.sql"
} else {
    Join-Path $repoRoot "config\sync_crawl_to_business_incremental.sql"
}

if (-not (Test-Path $sqlPath)) {
    throw "SQL file not found: $sqlPath"
}

if (-not $MysqlExe) {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($mysqlCommand) {
        $MysqlExe = $mysqlCommand.Source
    }
}

if (-not $MysqlExe -or -not (Test-Path $MysqlExe)) {
    throw "mysql executable not found. Set MYSQL_EXE or add mysql to PATH."
}

$tempSqlPath = Join-Path ([System.IO.Path]::GetTempPath()) ("career_sync_" + [guid]::NewGuid().ToString("N") + ".sql")
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    $tempSqlPath,
    (Get-Content -Raw -Encoding UTF8 $sqlPath),
    $utf8NoBom
)

Write-Host "Applying SQL: $sqlPath"
Write-Host "Using mysql: $MysqlExe"

$previousMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $MysqlPassword

try {
    $output = & $MysqlExe `
        --host=$MysqlHost `
        --port=$MysqlPort `
        --user=$MysqlUser `
        --database=$Database `
        --default-character-set=utf8mb4 `
        --batch `
        --raw `
        --execute="source $tempSqlPath" 2>&1

    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $joined = ($output | Out-String).Trim()
        throw "mysql exited with code $exitCode. $joined"
    }

    if ($output) {
        $output
    }
} finally {
    if ($null -ne $previousMysqlPwd) {
        $env:MYSQL_PWD = $previousMysqlPwd
    } else {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    Remove-Item $tempSqlPath -ErrorAction SilentlyContinue
}
