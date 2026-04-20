param(
    [string]$RepoRoot = "C:\Users\32020\Desktop\occupational _competencies_platform",
    [string]$LogDir = "C:\Users\32020\Desktop\occupational _competencies_platform\logs\acquisition"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

$logFile = Join-Path $LogDir ("etl-" + (Get-Date -Format "yyyyMMdd") + ".log")

function Write-Log {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Add-Content -Path $logFile -Value $line
    Write-Host $line
}

try {
    Write-Log "ETL incremental sync started"
    $syncScript = Join-Path $RepoRoot "scripts\sync_crawl_to_business.ps1"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $syncScript *>> $logFile
    Write-Log "ETL incremental sync completed"
} catch {
    Write-Log ("ETL incremental sync failed: " + $_.Exception.Message)
    throw
}
