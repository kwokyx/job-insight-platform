param(
    [string]$OutputRoot = ".\\dist\\vm-node-bundle"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$crawlerSource = Join-Path $repoRoot "integrations\\distributed-data-acquisition\\crawler-node"
$deploySource = Join-Path $repoRoot "deploy\\vm-node"
$outputCrawler = Join-Path $OutputRoot "crawler-node"
$outputDeploy = Join-Path $OutputRoot "vm-node"
$zipPath = Join-Path $repoRoot "dist\\vm-node-bundle.zip"

if (Test-Path $OutputRoot) {
    Remove-Item -LiteralPath $OutputRoot -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
Copy-Item -Path $crawlerSource -Destination $outputCrawler -Recurse -Force
Copy-Item -Path $deploySource -Destination $outputDeploy -Recurse -Force

if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Compress-Archive -Path (Join-Path $OutputRoot "*") -DestinationPath $zipPath -Force

Write-Host "VM node bundle created:"
Write-Host $OutputRoot
Write-Host $zipPath
