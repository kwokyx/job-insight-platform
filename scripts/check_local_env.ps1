$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = "D:\Python\python.exe"

function Test-Command {
    param(
        [string]$Name,
        [string]$Command
    )

    try {
        $output = Invoke-Expression $Command 2>&1 | Select-Object -First 1
        [PSCustomObject]@{
            Name = $Name
            Status = "OK"
            Detail = [string]$output
        }
    } catch {
        [PSCustomObject]@{
            Name = $Name
            Status = "MISSING"
            Detail = $_.Exception.Message
        }
    }
}

$checks = @()
$checks += Test-Command -Name "Java" -Command "cmd /c ""java -version 2>&1"""
$checks += Test-Command -Name "Maven" -Command "mvn -version"
$checks += Test-Command -Name "Node" -Command "node -v"
$checks += Test-Command -Name "NPM" -Command "npm -v"

if (Test-Path $python) {
    $checks += Test-Command -Name "Python" -Command "& '$python' --version"
} else {
    $checks += [PSCustomObject]@{
        Name = "Python"
        Status = "MISSING"
        Detail = "Expected at $python"
    }
}

$serviceDir = Join-Path $root "algorithm"
$appFile = Join-Path $serviceDir "app\main.py"
$checks += [PSCustomObject]@{
    Name = "AlgorithmService"
    Status = $(if (Test-Path $appFile) { "OK" } else { "MISSING" })
    Detail = $(if (Test-Path $appFile) { $appFile } else { "Missing algorithm\\app\\main.py" })
}

$checks | Format-Table -AutoSize
