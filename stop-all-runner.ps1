$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env"
$serviceStateFile = Join-Path (Join-Path $PSScriptRoot ".local-run") "current-services.json"

function Get-EnvFileValue {
    param([string]$FilePath, [string]$Key)

    if (-not (Test-Path $FilePath)) {
        return $null
    }

    foreach ($rawLine in Get-Content $FilePath) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }

        $index = $line.IndexOf("=")
        $currentKey = $line.Substring(0, $index).Trim()
        if ($currentKey -ceq $Key) {
            $value = $line.Substring($index + 1).Trim()
            return ($value -replace "^['`"]|['`"]$")
        }
    }

    return $null
}

function Get-RecordedServiceState {
    param([string]$FilePath)

    if (-not (Test-Path $FilePath)) {
        return $null
    }

    try {
        return Get-Content $FilePath -Raw | ConvertFrom-Json
    } catch {
        Write-Host "Could not parse service state file: $FilePath" -ForegroundColor Yellow
        return $null
    }
}

function Get-RecordedService {
    param(
        $State,
        [int]$Port
    )

    if ($null -eq $State -or $null -eq $State.services) {
        return $null
    }

    return $State.services | Where-Object { $_.port -eq $Port } | Select-Object -First 1
}

function Get-ListeningPid {
    param([int]$Port)

    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if ($null -eq $conn) {
        return $null
    }

    return $conn.OwningProcess
}

function Test-ProcessExists {
    param([int]$ProcessId)

    if ($ProcessId -le 0) {
        return $false
    }

    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Stop-RecordedProcess {
    param(
        [string]$Name,
        [int]$ProcessId
    )

    if (-not (Test-ProcessExists -ProcessId $ProcessId)) {
        return [pscustomobject]@{
            Success = $true
            Message = "PID $ProcessId is already gone."
        }
    }

    try {
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
        Start-Sleep -Milliseconds 400
    } catch {
    }

    if (-not (Test-ProcessExists -ProcessId $ProcessId)) {
        return [pscustomobject]@{
            Success = $true
            Message = "Stopped PID $ProcessId with Stop-Process."
        }
    }

    try {
        $taskkillOutput = & taskkill.exe /PID $ProcessId /T /F 2>&1
        Start-Sleep -Milliseconds 600
    } catch {
        $taskkillOutput = $_.Exception.Message
    }

    if (-not (Test-ProcessExists -ProcessId $ProcessId)) {
        return [pscustomobject]@{
            Success = $true
            Message = "Stopped PID $ProcessId with taskkill /T /F."
        }
    }

    return [pscustomobject]@{
        Success = $false
        Message = "PID $ProcessId is still alive after Stop-Process and taskkill."
    }
}

$aiPortEnv = Get-EnvFileValue -FilePath $envFile -Key "AI_SERVER_PORT"
$aiPort = if ($aiPortEnv) { [int]$aiPortEnv } else { 5000 }

$rpaPortEnv = Get-EnvFileValue -FilePath $envFile -Key "RPA_SERVER_PORT"
$rpaPort = if ($rpaPortEnv) { [int]$rpaPortEnv } else { 5050 }

$portsToKill = @(8080, 5500, $aiPort, $rpaPort)
$processNames = @("Backend", "Frontend", "AI Server", "RPA Server")
$serviceState = Get-RecordedServiceState -FilePath $serviceStateFile

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Stopping DDUK ERP Background Services" -ForegroundColor Cyan
Write-Host "=========================================================="

if ($null -eq $serviceState) {
    Write-Host "No current-services.json found. Refusing to stop unknown processes by port only." -ForegroundColor Yellow
    Write-Host "Run ai-all-start.bat first or stop the process manually after confirming the PID." -ForegroundColor Yellow
    exit 1
}

for ($i = 0; $i -lt $portsToKill.Length; $i++) {
    $port = $portsToKill[$i]
    $name = $processNames[$i]
    $recordedService = Get-RecordedService -State $serviceState -Port $port
    if ($null -eq $recordedService -or $null -eq $recordedService.pid) {
        $activePid = Get-ListeningPid -Port $port
        if ($null -eq $activePid) {
            Write-Host "[$name] Port $port is already free (Not running)." -ForegroundColor DarkGray
        } else {
            Write-Host "[$name] Port $port is in use by PID $activePid, but there is no matching dduk service record. Skipping." -ForegroundColor Yellow
        }
        continue
    }

    $candidatePids = [System.Collections.Generic.List[int]]::new()
    $recordedPid = [int]$recordedService.pid
    $activePid = Get-ListeningPid -Port $port

    if ($recordedPid -gt 0 -and -not $candidatePids.Contains($recordedPid)) {
        $candidatePids.Add($recordedPid)
    }
    if ($null -ne $activePid -and [int]$activePid -eq $recordedPid -and -not $candidatePids.Contains([int]$activePid)) {
        $candidatePids.Add([int]$activePid)
    }

    if ($candidatePids.Count -eq 0) {
        Write-Host "[$name] Port $port is already free and recorded PID $recordedPid is not running." -ForegroundColor DarkGray
        continue
    }

    if ($null -ne $activePid -and [int]$activePid -ne $recordedPid) {
        Write-Host "[$name] Port $port is currently owned by PID $activePid while recorded dduk PID is $recordedPid. Will only stop recorded/current matched candidates." -ForegroundColor Yellow
    }

    $serviceStopped = $false
    foreach ($candidatePid in $candidatePids) {
        if (-not (Test-ProcessExists -ProcessId $candidatePid)) {
            Write-Host "[$name] PID $candidatePid is already gone." -ForegroundColor DarkGray
            continue
        }

        Write-Host "[$name] Attempting to stop recorded dduk PID $candidatePid..."
        $result = Stop-RecordedProcess -Name $name -ProcessId $candidatePid

        if ($result.Success) {
            Write-Host "  -> [$name] $($result.Message)" -ForegroundColor Green
        } else {
            Write-Host "  -> [$name] $($result.Message)" -ForegroundColor Yellow
        }
    }

    $remainingPid = Get-ListeningPid -Port $port
    if ($null -ne $remainingPid -and -not (Test-ProcessExists -ProcessId $remainingPid)) {
        Start-Sleep -Milliseconds 500
        $remainingPid = Get-ListeningPid -Port $port
    }

    if ($null -eq $remainingPid) {
        Write-Host "  -> [$name] Port $port is now free." -ForegroundColor Green
        $serviceStopped = $true
    } elseif ($remainingPid -eq $recordedPid -and (Test-ProcessExists -ProcessId $remainingPid)) {
        Write-Host "  -> [$name] Port $port is still held by recorded dduk PID $remainingPid. You may need Administrator privileges." -ForegroundColor Red
    } elseif ($remainingPid -eq $recordedPid) {
        Write-Host "  -> [$name] Port probe still reports PID $remainingPid, but the process is already gone. Treating it as stopped." -ForegroundColor Yellow
        $serviceStopped = $true
    } else {
        Write-Host "  -> [$name] Port $port is still held by PID $remainingPid, which does not match the recorded dduk PID. Leaving it untouched." -ForegroundColor Yellow
    }

    if ($serviceStopped -and (Test-ProcessExists -ProcessId $recordedPid) -eq $false) {
        $serviceState.services = @($serviceState.services | Where-Object { $_.port -ne $port }) + @(
            [ordered]@{ name = $name; port = $port; pid = $null }
        )
    }
}

$serviceState.recordedAt = (Get-Date).ToString("o")
$serviceState | ConvertTo-Json -Depth 5 | Set-Content -Path $serviceStateFile -Encoding UTF8

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " All specified services stop attempt completed." -ForegroundColor Cyan
Write-Host "=========================================================="
