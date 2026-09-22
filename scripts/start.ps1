param([switch]$DatabaseOnly)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if ($DatabaseOnly) { docker compose up -d --wait db }
    else {
        docker compose up -d --build
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose start failed.' }
        $ready = $false
        for ($attempt = 0; $attempt -lt 90; $attempt++) {
            try {
                $null = Invoke-RestMethod http://localhost:8080/api/organizations -TimeoutSec 2
                $ready = $true
                break
            } catch { Start-Sleep -Seconds 1 }
        }
        if (-not $ready) { throw 'API did not become ready. Run: docker compose logs api' }
        $null = Invoke-RestMethod http://localhost:8080/v3/api-docs -TimeoutSec 30
        Write-Host 'API ready: http://localhost:8080/swagger-ui.html'
        Write-Host 'Demo: node scripts/demo.mjs'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose command failed.' }
} finally { Pop-Location }
