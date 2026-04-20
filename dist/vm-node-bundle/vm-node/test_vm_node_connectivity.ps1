param(
    [string]$EnvFile = ".\\crawler-node\\.env"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile"
}

$config = @{}
Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
        return
    }
    $parts = $line.Split("=", 2)
    $config[$parts[0].Trim()] = $parts[1].Trim()
}

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

$schedulerHost = $config["SCHEDULER_HOST"]
$schedulerPort = [int]$config["SCHEDULER_PORT"]
$rabbitHost = $config["RABBITMQ_HOST"]
$rabbitPort = [int]$config["RABBITMQ_PORT"]
$mysqlHost = $config["MYSQL_HOST"]
$mysqlPort = [int]$config["MYSQL_PORT"]
$redisHost = $config["REDIS_HOST"]
$redisPort = [int]$config["REDIS_PORT"]

$checks = @(
    @{ Name = "Scheduler TCP"; Passed = (Test-TcpPort -TargetHost $schedulerHost -Port $schedulerPort) },
    @{ Name = "RabbitMQ TCP"; Passed = (Test-TcpPort -TargetHost $rabbitHost -Port $rabbitPort) },
    @{ Name = "MySQL TCP"; Passed = (Test-TcpPort -TargetHost $mysqlHost -Port $mysqlPort) },
    @{ Name = "Redis TCP"; Passed = (Test-TcpPort -TargetHost $redisHost -Port $redisPort) }
)

$checks | Format-Table -AutoSize

if ($checks.Passed -contains $false) {
    exit 1
}
