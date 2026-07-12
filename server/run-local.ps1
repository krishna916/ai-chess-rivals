$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/aichessrivals"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_FLYWAY_URL="jdbc:postgresql://localhost:5433/aichessrivals"
$env:SPRING_FLYWAY_USER="postgres"
$databasePassword = $env:SPRING_DATASOURCE_PASSWORD
if ([string]::IsNullOrWhiteSpace($databasePassword)) {
    $databasePassword = $env:SPRING_FLYWAY_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($databasePassword)) {
    throw "Set SPRING_DATASOURCE_PASSWORD (or SPRING_FLYWAY_PASSWORD) before running this script."
}
$env:SPRING_DATASOURCE_PASSWORD = $databasePassword
$env:SPRING_FLYWAY_PASSWORD = $databasePassword
$env:STOCKFISH_PATH="stockfish/stockfish.exe"
$env:SERVER_PORT="8082"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
