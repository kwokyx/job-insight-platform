param(
    [string]$RulePrefix = "CareerPlatform Acquisition"
)

$rules = @(
    @{ Name = "$RulePrefix MySQL"; Port = 3307 },
    @{ Name = "$RulePrefix Redis"; Port = 6379 },
    @{ Name = "$RulePrefix RabbitMQ"; Port = 5672 },
    @{ Name = "$RulePrefix RabbitMQ UI"; Port = 15672 },
    @{ Name = "$RulePrefix Scheduler"; Port = 8001 }
)

foreach ($rule in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
    if (-not $existing) {
        New-NetFirewallRule `
            -DisplayName $rule.Name `
            -Direction Inbound `
            -Action Allow `
            -Protocol TCP `
            -LocalPort $rule.Port | Out-Null
    }
}

Write-Host "Acquisition firewall rules ensured."
