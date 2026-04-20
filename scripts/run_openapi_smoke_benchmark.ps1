$ErrorActionPreference = "Stop"

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Path = "/api/v1/open/analysis/overview",
    [int]$Iterations = 20
)

$durations = New-Object System.Collections.Generic.List[Double]
$target = $BaseUrl.TrimEnd("/") + $Path

for ($i = 1; $i -le $Iterations; $i++) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri $target -UseBasicParsing -TimeoutSec 15
        $watch.Stop()
        $durations.Add($watch.Elapsed.TotalMilliseconds)
        Write-Host ("[{0}/{1}] status={2} latencyMs={3:N2}" -f $i, $Iterations, $response.StatusCode, $watch.Elapsed.TotalMilliseconds)
    } catch {
        $watch.Stop()
        Write-Host ("[{0}/{1}] failed latencyMs={2:N2} error={3}" -f $i, $Iterations, $watch.Elapsed.TotalMilliseconds, $_.Exception.Message)
    }
}

if ($durations.Count -gt 0) {
    $sorted = $durations | Sort-Object
    $avg = ($durations | Measure-Object -Average).Average
    $p95Index = [Math]::Min($sorted.Count - 1, [Math]::Floor($sorted.Count * 0.95))
    $p95 = $sorted[$p95Index]
    $max = ($durations | Measure-Object -Maximum).Maximum
    $min = ($durations | Measure-Object -Minimum).Minimum

    Write-Host ""
    Write-Host "OpenAPI Smoke Benchmark Summary"
    Write-Host ("target={0}" -f $target)
    Write-Host ("samples={0}" -f $durations.Count)
    Write-Host ("avgMs={0:N2}" -f $avg)
    Write-Host ("p95Ms={0:N2}" -f $p95)
    Write-Host ("minMs={0:N2}" -f $min)
    Write-Host ("maxMs={0:N2}" -f $max)
}
