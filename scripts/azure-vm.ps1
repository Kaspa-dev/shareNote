param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Start', 'Stop', 'Status')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
$subscription = '4d31a374-2b09-4820-9c8f-f51a38ce87de'
$resourceGroup = 'rg-sharenote-demo'
$vmName = 'vm-sharenote-demo'

switch ($Action) {
    'Start' { az vm start --subscription $subscription --resource-group $resourceGroup --name $vmName }
    'Stop' { az vm deallocate --subscription $subscription --resource-group $resourceGroup --name $vmName }
    'Status' {
        az vm show --subscription $subscription --resource-group $resourceGroup --name $vmName --show-details --query '{status:powerState,url:fqdns,ip:publicIps}' --output json
    }
}
if ($LASTEXITCODE -ne 0) { throw "Azure VM $Action failed." }
if ($Action -eq 'Stop') {
    Write-Host 'VM deallocated. Compute billing stopped; disk and public IP charges continue.'
}
