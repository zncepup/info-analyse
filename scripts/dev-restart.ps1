$ErrorActionPreference = "Stop"
$JAR = "target\info-analyse-1.0.0.jar"
$LOG = "app-run.log"
$ERR = "app-run.err"
$PORT = 8080

Write-Host "=== [1/3] Maven package ===" -ForegroundColor Cyan
mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed" -ForegroundColor Red
    exit 1
}
Write-Host "Build OK" -ForegroundColor Green

Write-Host "=== [2/3] Kill old process ===" -ForegroundColor Cyan
$conns = Get-NetTCPConnection -LocalPort $PORT -ErrorAction SilentlyContinue
if ($conns) {
    $procIds = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($p in $procIds) {
        Write-Host "  kill PID $p"
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
} else {
    Write-Host "  No old process"
}

Write-Host "=== [3/3] Start new process ===" -ForegroundColor Cyan
Start-Process -FilePath "java" -ArgumentList "-jar",$JAR -RedirectStandardOutput $LOG -RedirectStandardError $ERR -WindowStyle Hidden

Write-Host "Waiting..." -NoNewline
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 2
    $check = Get-NetTCPConnection -LocalPort $PORT -ErrorAction SilentlyContinue
    if ($check) {
        Write-Host ""
        Write-Host "OK http://localhost:$PORT" -ForegroundColor Green
        exit 0
    }
    Write-Host "." -NoNewline
}
Write-Host ""
Write-Host "Timeout - check app-run.err" -ForegroundColor Yellow
