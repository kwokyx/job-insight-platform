param(
    [string]$NodeRoot = ".\\crawler-node"
)

$ErrorActionPreference = "Stop"

$venvPython = Join-Path $NodeRoot ".venv\\Scripts\\python.exe"
if (-not (Test-Path $venvPython)) {
    throw "venv python not found: $venvPython. Run install_vm_node.ps1 first."
}

if (-not (Test-Path (Join-Path $NodeRoot ".env"))) {
    throw ".env not found under $NodeRoot"
}

Push-Location $NodeRoot
try {
    & $venvPython main.py
} finally {
    Pop-Location
}
