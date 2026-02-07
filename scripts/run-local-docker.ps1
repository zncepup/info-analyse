param(
    [int]$AppPort = 8080,
    [string]$Image = "info-analyse:lowmem",
    [string]$Container = "info-analyse-local",
    [string]$ApiKeyFile = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

Set-Location (Split-Path -Parent (Split-Path -Parent $PSCommandPath))

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker command not found. Install and start Docker Desktop first."
}

Write-Host "[1/5] Build jar..."
if (-not $SkipBuild) {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "mvn package failed."
    }
}

$jarCandidates = Get-ChildItem -Path ".\target\*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "(?i)(sources|javadoc|original)" } |
    Sort-Object LastWriteTime -Descending
if (-not $jarCandidates -or $jarCandidates.Count -eq 0) {
    throw "No runnable jar found under target/. Please run mvn package first."
}
$jarPath = $jarCandidates[0].FullName

$tmpDir = Join-Path (Get-Location) ".deploy-tmp"
$runtimeDir = Join-Path (Get-Location) ".local-docker"
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runtimeDir "config") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runtimeDir "data") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runtimeDir "log") -Force | Out-Null

$dockerfileContent = @'
FROM mcr.microsoft.com/playwright/java:v1.40.0-jammy
WORKDIR /app
COPY app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xms128m","-Xmx512m","-XX:+UseSerialGC","-Dfile.encoding=UTF-8","-jar","/app/app.jar","--server.address=0.0.0.0","--server.port=8080"]
'@

$dockerfilePath = Join-Path $tmpDir "Dockerfile.local"
$dockerfileContent = $dockerfileContent -replace "`r?`n", "`n"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($dockerfilePath, $dockerfileContent, $utf8NoBom)
Copy-Item $jarPath (Join-Path $tmpDir "app.jar") -Force

$cookiesTarget = Join-Path $runtimeDir "config\zhihu_cookies.json"
if (Test-Path ".\zhihu_cookies.json") {
    Copy-Item ".\zhihu_cookies.json" $cookiesTarget -Force
} elseif (-not (Test-Path $cookiesTarget)) {
    [System.IO.File]::WriteAllText($cookiesTarget, "[]`n", $utf8NoBom)
}

$apiTarget = Join-Path $runtimeDir "config\api.txt"
if (-not [string]::IsNullOrWhiteSpace($ApiKeyFile)) {
    if (-not (Test-Path $ApiKeyFile)) {
        throw "ApiKeyFile not found: $ApiKeyFile"
    }
    Copy-Item $ApiKeyFile $apiTarget -Force
} elseif (-not (Test-Path $apiTarget)) {
    [System.IO.File]::WriteAllText($apiTarget, "", $utf8NoBom)
}

Write-Host "[2/5] Build Docker image..."
& docker build -t $Image -f $dockerfilePath $tmpDir
if ($LASTEXITCODE -ne 0) {
    throw "docker build failed."
}

Write-Host "[3/5] Restart container..."
& docker rm -f $Container 2>$null | Out-Null

$dataPath = (Resolve-Path (Join-Path $runtimeDir "data")).Path
$cookiesPath = (Resolve-Path $cookiesTarget).Path
$apiPath = (Resolve-Path $apiTarget).Path

& docker run -d --name $Container --restart unless-stopped --memory=1200m --cpus=1.0 --shm-size=512m -p "${AppPort}:8080" -e DEEPSEEK_API_KEY_FILE=/app/config/api.txt --mount "type=bind,source=$dataPath,target=/app/output" --mount "type=bind,source=$cookiesPath,target=/app/zhihu_cookies.json" --mount "type=bind,source=$apiPath,target=/app/config/api.txt,readonly" $Image
if ($LASTEXITCODE -ne 0) {
    throw "docker run failed."
}

Write-Host "[4/5] Health check..."
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$AppPort/" -TimeoutSec 3
        if ($resp.StatusCode -eq 200) {
            $ok = $true
            break
        }
    } catch {
    }
    Start-Sleep -Seconds 2
}

if (-not $ok) {
    & docker logs --tail 120 $Container 2>&1
    throw "Health check failed on port $AppPort."
}

Write-Host "[5/5] Done."
& docker ps --filter "name=$Container" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Write-Host "URL: http://127.0.0.1:$AppPort"
