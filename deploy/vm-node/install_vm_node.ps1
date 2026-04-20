param(
    [string]$NodeRoot = ".\\crawler-node"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $NodeRoot)) {
    throw "crawler-node directory not found: $NodeRoot"
}

Set-Location $NodeRoot

if (-not (Test-Path ".env")) {
    throw ".env not found under $NodeRoot. Copy one of deploy/vm-node/env.*.example to .env first."
}

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "python is not installed or not in PATH."
}

python -m venv .venv

$venvPython = Join-Path ".venv" "Scripts\\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython"
}

& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install -r requirements.txt

Write-Host "VM node installation completed."
