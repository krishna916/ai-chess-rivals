Write-Host "Starting PostgreSQL database container..." -ForegroundColor Green
docker compose -f server/docker-compose.yml up -d postgres
