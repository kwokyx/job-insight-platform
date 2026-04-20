$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = "D:\Python\python.exe"
$serviceDir = Join-Path $root "algorithm"
$appFile = Join-Path $serviceDir "app\main.py"

if (-not (Test-Path $python)) {
    throw "Python not found at $python"
}

if (-not (Test-Path $appFile)) {
    throw "Algorithm service entry file not found: $appFile"
}

Write-Host "Starting algorithm service with $python"
Set-Location $serviceDir
& $python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
