param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$destination = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/openapi.json'
$specification = Invoke-RestMethod "$($BaseUrl.TrimEnd('/'))/v3/api-docs"
$json = $specification | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($destination, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Write-Host "OpenAPI exported: $destination"
