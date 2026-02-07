param(
    [string]$ServerIp = "123.56.153.134",
    [string]$User = "root",
    [int]$Port = 22,
    [string]$Password,
    [string]$HostKey = "ssh-ed25519 255 SHA256:AOUvHzRnxx56JqjREnVGT6AtrIkZ/5eFvNL8OSGG+nk",
    [int]$AppPort = 8080,
    [string]$RemoteBase = "/root/download/info-analyse",
    [string]$ApiKeyFile = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = $env:DEPLOY_SSH_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "Missing password. Use -Password or set DEPLOY_SSH_PASSWORD."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $repoRoot

function Resolve-ToolPath {
    param(
        [string]$Preferred,
        [string]$CommandName
    )
    if ($Preferred -and (Test-Path $Preferred)) {
        return $Preferred
    }
    $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    throw "Tool not found: $CommandName"
}

$plinkPath = Resolve-ToolPath -Preferred "C:\Program Files\PuTTY\plink.exe" -CommandName "plink"
$pscpPath = Resolve-ToolPath -Preferred "C:\Program Files\PuTTY\pscp.exe" -CommandName "pscp"

function Invoke-Remote {
    param([string]$RemoteCommand)
    & $plinkPath -batch -hostkey $HostKey -pw $Password -P $Port "$User@$ServerIp" $RemoteCommand
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed: $RemoteCommand"
    }
}

function Copy-RemoteFile {
    param(
        [string]$LocalFile,
        [string]$RemoteFile
    )
    & $pscpPath -batch -hostkey $HostKey -pw $Password -P $Port $LocalFile "$User@$ServerIp`:$RemoteFile"
    if ($LASTEXITCODE -ne 0) {
        throw "Upload failed: $LocalFile -> $RemoteFile"
    }
}

Write-Host "[1/6] Build package..."
if (-not $SkipBuild) {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "mvn package failed."
    }
}

$jarCandidates = Get-ChildItem -Path (Join-Path $repoRoot "target\*.jar") -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(?i)(sources|javadoc|original)' } |
    Sort-Object LastWriteTime -Descending
if (-not $jarCandidates -or $jarCandidates.Count -eq 0) {
    throw "No runnable jar found under target/. Please run mvn package first."
}
$jarPath = $jarCandidates[0].FullName

Write-Host "[2/6] Prepare remote directories..."
Invoke-Remote "mkdir -p $RemoteBase/app $RemoteBase/config $RemoteBase/data $RemoteBase/log"

$tmpDir = Join-Path $repoRoot ".deploy-tmp"
if (-not (Test-Path $tmpDir)) {
    New-Item -ItemType Directory -Path $tmpDir | Out-Null
}

$dockerfilePath = Join-Path $tmpDir "Dockerfile"
$startScriptPath = Join-Path $tmpDir "info-analyse-start.sh"

$dockerfileContent = @'
FROM mcr.microsoft.com/playwright/java:v1.40.0-jammy
WORKDIR /app
COPY app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xms128m","-Xmx512m","-XX:+UseSerialGC","-Dfile.encoding=UTF-8","-jar","/app/app.jar","--server.address=0.0.0.0","--server.port=8080"]
'@

$startScriptContent = @"
#!/bin/bash
set -e
BASE=$RemoteBase
cd `$BASE/app

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found. install Docker first."
  exit 1
fi

mkdir -p "`$BASE/config" "`$BASE/data" "`$BASE/log"
touch "`$BASE/config/api.txt"
if [ ! -f "`$BASE/config/zhihu_cookies.json" ]; then
  echo "[]" > "`$BASE/config/zhihu_cookies.json"
fi

docker build -t info-analyse:lowmem .
docker rm -f info-analyse >/dev/null 2>&1 || true

docker run -d --name info-analyse --restart unless-stopped --memory=1200m --cpus=1.0 --shm-size=512m -p ${AppPort}:8080 -e DEEPSEEK_API_KEY_FILE=/app/config/api.txt -v "`$BASE/data:/app/output" -v "`$BASE/config/zhihu_cookies.json:/app/zhihu_cookies.json" -v "`$BASE/config/api.txt:/app/config/api.txt:ro" info-analyse:lowmem

if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port=${AppPort}/tcp >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi
"@

$dockerfileContent = $dockerfileContent -replace "`r?`n", "`n"
$startScriptContent = $startScriptContent -replace "`r?`n", "`n"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($dockerfilePath, $dockerfileContent, $utf8NoBom)
[System.IO.File]::WriteAllText($startScriptPath, $startScriptContent, $utf8NoBom)

Write-Host "[3/6] Upload files..."
Copy-RemoteFile -LocalFile $jarPath -RemoteFile "$RemoteBase/app/app.jar"
Copy-RemoteFile -LocalFile $dockerfilePath -RemoteFile "$RemoteBase/app/Dockerfile"
Copy-RemoteFile -LocalFile $startScriptPath -RemoteFile "$RemoteBase/info-analyse-start.sh"

$cookiesPath = Join-Path $repoRoot "zhihu_cookies.json"
if (Test-Path $cookiesPath) {
    Copy-RemoteFile -LocalFile $cookiesPath -RemoteFile "$RemoteBase/config/zhihu_cookies.json"
} else {
    Write-Host "Warning: zhihu_cookies.json not found locally, keep remote file."
}

if (-not [string]::IsNullOrWhiteSpace($ApiKeyFile)) {
    if (-not (Test-Path $ApiKeyFile)) {
        throw "ApiKeyFile not found: $ApiKeyFile"
    }
    Copy-RemoteFile -LocalFile $ApiKeyFile -RemoteFile "$RemoteBase/config/api.txt"
} else {
    Write-Host "Warning: ApiKeyFile not specified, keep remote api.txt."
}

Write-Host "[4/6] Start service..."
Invoke-Remote "chmod +x $RemoteBase/info-analyse-start.sh && $RemoteBase/info-analyse-start.sh"

Write-Host "[5/6] Health check..."
$healthCommand = 'for i in $(seq 1 30); do code=$(curl -s -o /dev/null -w ''%{http_code}'' http://127.0.0.1:' + $AppPort + '/ || true); [ x$code = x200 ] && echo 200 && exit 0; sleep 2; done; echo health check failed; docker logs --tail 60 info-analyse 2>&1; exit 1'
Invoke-Remote $healthCommand
Invoke-Remote "docker ps --filter name=info-analyse --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

Write-Host "[6/6] Done."
Write-Host "URL: http://$ServerIp`:$AppPort"
