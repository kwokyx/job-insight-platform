param(
    [string]$SchedulerUrl = "http://localhost:8001/api/monitor/dashboard",
    [string]$RabbitManagementUrl = "http://localhost:15672",
    [string]$MysqlHost = "localhost",
    [int]$MysqlPort = 3307,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$RabbitHost = "localhost",
    [int]$RabbitPort = 5672
)

function Test-TcpPort {
    param(
        [string]$TargetHost,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(3000, $false)
        $client.Close()
        return $ok
    } catch {
        return $false
    }
}

$checks = @(
    @{ Name = "Scheduler API"; Passed = $false },
    @{ Name = "RabbitMQ UI"; Passed = $false },
    @{ Name = "MySQL TCP"; Passed = (Test-TcpPort -TargetHost $MysqlHost -Port $MysqlPort) },
    @{ Name = "Redis TCP"; Passed = (Test-TcpPort -TargetHost $RedisHost -Port $RedisPort) },
    @{ Name = "RabbitMQ TCP"; Passed = (Test-TcpPort -TargetHost $RabbitHost -Port $RabbitPort) }
)

try {
    Invoke-WebRequest -Uri $SchedulerUrl -UseBasicParsing -TimeoutSec 5 | Out-Null
    $checks[0].Passed = $true
} catch {}

try {
    Invoke-WebRequest -Uri $RabbitManagementUrl -UseBasicParsing -TimeoutSec 5 | Out-Null
    $checks[1].Passed = $true
} catch {}

$checks | Format-Table -AutoSize

if ($checks.Passed -contains $false) {
    exit 1
}
