param([switch]$DatabaseOnly, [switch]$BackendOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$frontend = $null
Push-Location $root
try {
    # Read the same settings as Compose without executing .env as code.
    if (Test-Path '.env') {
        foreach ($line in Get-Content '.env') {
            if ($line -match '^\s*(JAVA_HOME|ADMIN_USERNAME|ADMIN_PASSWORD|DB_PASSWORD)\s*=(.*)$') {
                $name = $matches[1]
                $value = $matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                if (-not (Test-Path "Env:$name")) { Set-Item "Env:$name" $value }
            }
        }
    }
    if (-not $DatabaseOnly) {
        if (-not $BackendOnly) { $null = Get-Command npm.cmd -ErrorAction Stop }
        if (-not $env:JAVA_HOME) {
            $null = Get-Command java -ErrorAction Stop
            $settings = & $env:ComSpec /c 'java -XshowSettings:properties -version 2>&1' | Out-String
            if ($settings -match 'java.home\s*=\s*([^\r\n]+)') {
                $env:JAVA_HOME = $matches[1].Trim()
            }
        }
        if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
            throw 'Set JAVA_HOME to a Java JDK 21 or newer installation.'
        }
    }
    docker compose up -d --wait --remove-orphans db
    if ($LASTEXITCODE -ne 0) { throw 'Database startup failed.' }
    if ($DatabaseOnly) { return }

    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/sharenote'
    $env:DB_USER = 'sharenote'
    $env:SERVER_ADDRESS = '127.0.0.1'
    if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }

    if (-not $BackendOnly) {
        Push-Location frontend
        try {
            if (-not (Test-Path 'node_modules')) {
                npm.cmd ci
                if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            }
            $frontend = Start-Process -FilePath (Get-Command node).Source -ArgumentList 'node_modules/vite/bin/vite.js --host 127.0.0.1' -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -RedirectStandardOutput "$root/frontend-dev.log" -RedirectStandardError "$root/frontend-dev-error.log" -PassThru
            Start-Sleep -Seconds 2
            if ($frontend.HasExited) { throw 'Frontend startup failed. See frontend-dev-error.log.' }
        } finally { Pop-Location }

        Write-Host 'Frontend: http://localhost:3000 (logs: frontend-dev.log)'
    }
    Write-Host "Starting local API: http://localhost:$env:SERVER_PORT/swagger-ui.html"
    Write-Host 'Press Ctrl+C to stop the local app. Stop the database with: docker compose down'
    Push-Location backend
    try {
        .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'Local API exited with an error.' }
    } finally { Pop-Location }
} finally {
    if ($frontend -and -not $frontend.HasExited) { Stop-Process -Id $frontend.Id -ErrorAction SilentlyContinue }
    Pop-Location
}
