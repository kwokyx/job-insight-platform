param(
    [string]$MysqlHost = $(if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "localhost" }),
    [int]$MysqlPort = $(if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 }),
    [string]$MysqlUser = $(if ($env:MYSQL_USERNAME) { $env:MYSQL_USERNAME } else { "career" }),
    [string]$MysqlPassword = $(if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "career2026" }),
    [string]$Database = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "career_platform" }),
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

Write-Host "Applying SQL: $sqlPath"

Get-Content -Raw $sqlPath |
    & mysql `
        --host=$MysqlHost `
        --port=$MysqlPort `
        --user=$MysqlUser `
        --password=$MysqlPassword `
        --database=$Database `
        --default-character-set=utf8mb4
