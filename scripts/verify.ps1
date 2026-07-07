$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

Write-Host 'Verifying backend...'
& (Join-Path $repositoryRoot 'server\mvnw.cmd') -f (Join-Path $repositoryRoot 'server\pom.xml') verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Verifying frontend...'
Push-Location (Join-Path $repositoryRoot 'client')
try {
    & npm.cmd run verify
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
