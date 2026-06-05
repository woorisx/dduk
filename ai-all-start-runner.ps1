$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
$backendDir = Join-Path $workspaceRoot "backend"
$frontendDir = Join-Path $workspaceRoot "frontend"
$aiDir = Join-Path $workspaceRoot "ai-server"
$rpaDir = Join-Path $workspaceRoot "rpa"

$tmpRootDir = Join-Path $workspaceRoot ".local-run"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$tmpDir = Join-Path $tmpRootDir $runId
$serviceStateFile = Join-Path $tmpRootDir "current-services.json"

$backendLog = Join-Path $tmpDir "backend.out.log"
$backendErrLog = Join-Path $tmpDir "backend.err.log"
$frontendLog = Join-Path $tmpDir "frontend.out.log"
$frontendErrLog = Join-Path $tmpDir "frontend.err.log"
$aiLog = Join-Path $tmpDir "ai-server.out.log"
$aiErrLog = Join-Path $tmpDir "ai-server.err.log"
$rpaLog = Join-Path $tmpDir "rpa-server.out.log"
$rpaErrLog = Join-Path $tmpDir "rpa-server.err.log"

function Get-EnvFileValue {
    param(
        [string]$FilePath,
        [string]$Key
    )

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

function Test-PortListening {
    param([int]$Port)

    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -ne $conn
}

function Test-HttpReady {
    param([string]$Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpReady -Url $Url) {
            return $true
        }
        Start-Sleep -Seconds 2
    }

    return $false
}

function Get-PythonCommand {
    if (Get-Command python -ErrorAction SilentlyContinue) {
        return "python"
    }

    if (Get-Command py -ErrorAction SilentlyContinue) {
        return "py"
    }

    throw "Python executable not found. Install python or py first."
}

function Get-JavaCommand {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) {
        return $java.Source
    }

    throw "Java executable not found. Install Java or ensure it is available on PATH."
}

function Test-PythonImports {
    param(
        [string]$PythonExe,
        [string[]]$Modules
    )

    if (-not (Test-Path $PythonExe)) {
        return $false
    }

    $moduleList = ($Modules | ForEach-Object { "'$_'" }) -join ", "
    $code = @"
import importlib.util
modules = [$moduleList]
missing = [name for name in modules if importlib.util.find_spec(name) is None]
raise SystemExit(1 if missing else 0)
"@

    & $PythonExe -c $code | Out-Null
    return $LASTEXITCODE -eq 0
}

function Start-DetachedCommand {
    param(
        [string]$WorkingDirectory,
        [string]$CommandLine
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c $CommandLine"
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    $process.Start() | Out-Null

    return $process
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

try {
    if (-not (Test-Path $backendDir)) { throw "backend directory not found: $backendDir" }
    if (-not (Test-Path $frontendDir)) { throw "frontend directory not found: $frontendDir" }
    if (-not (Test-Path $aiDir)) { throw "ai-server directory not found: $aiDir" }
    if (-not (Test-Path $rpaDir)) { throw "rpa directory not found: $rpaDir" }

    if (-not (Test-Path $tmpRootDir)) {
        New-Item -ItemType Directory -Path $tmpRootDir | Out-Null
    }
    if (-not (Test-Path $tmpDir)) {
        New-Item -ItemType Directory -Path $tmpDir | Out-Null
    }

    New-Item -ItemType File -Path $backendLog -Force | Out-Null
    New-Item -ItemType File -Path $backendErrLog -Force | Out-Null
    New-Item -ItemType File -Path $frontendLog -Force | Out-Null
    New-Item -ItemType File -Path $frontendErrLog -Force | Out-Null
    New-Item -ItemType File -Path $aiLog -Force | Out-Null
    New-Item -ItemType File -Path $aiErrLog -Force | Out-Null
    New-Item -ItemType File -Path $rpaLog -Force | Out-Null
    New-Item -ItemType File -Path $rpaErrLog -Force | Out-Null

    $envFile = Join-Path $workspaceRoot ".env"

    $aiPortEnv = Get-EnvFileValue -FilePath $envFile -Key "AI_SERVER_PORT"
    $aiPort = if ($aiPortEnv) { [int]$aiPortEnv } else { 5000 }

    $rpaPortEnv = Get-EnvFileValue -FilePath $envFile -Key "RPA_SERVER_PORT"
    $rpaPort = if ($rpaPortEnv) { [int]$rpaPortEnv } else { 5050 }

    $backendUrl = "http://localhost:8080/"
    $frontendUrl = "http://localhost:5500/index.html"
    $aiUrl = "http://localhost:$aiPort/health"
    $rpaUrl = "http://localhost:$rpaPort/health"

    $pythonCommand = Get-PythonCommand
    $javaCommand = Get-JavaCommand

    $aiVenvPython = Join-Path $aiDir "venv\Scripts\python.exe"
    $aiPip = Join-Path $aiDir "venv\Scripts\pip.exe"
    if (-not (Test-Path $aiVenvPython) -or -not (Test-Path $aiPip)) {
        Write-Host ">>> Creating AI server virtualenv..." -ForegroundColor Cyan
        if (Test-Path (Join-Path $aiDir "venv")) {
            Remove-Item -Recurse -Force (Join-Path $aiDir "venv") -ErrorAction SilentlyContinue
        }
        Push-Location $aiDir
        try {
            & $pythonCommand -m venv venv
        } finally {
            Pop-Location
        }
        if (-not (Test-Path $aiVenvPython) -or -not (Test-Path $aiPip)) {
            throw "AI virtualenv creation failed: $aiDir"
        }
        Write-Host ">>> Installing AI server dependencies..." -ForegroundColor Cyan
        Push-Location $aiDir
        try {
            & $aiPip install -r requirements.txt
        } finally {
            Pop-Location
        }
    } elseif (-not (Test-PythonImports -PythonExe $aiVenvPython -Modules @("flask", "flask_cors", "dotenv", "google.generativeai", "waitress"))) {
        Write-Host ">>> AI virtualenv exists but dependencies are incomplete. Reinstalling AI requirements..." -ForegroundColor Yellow
        Push-Location $aiDir
        try {
            & $aiPip install -r requirements.txt
        } finally {
            Pop-Location
        }
    }
    $aiPythonCmd = """$aiVenvPython"""

    $rpaVenvPython = Join-Path $rpaDir "venv\Scripts\python.exe"
    $rpaPip = Join-Path $rpaDir "venv\Scripts\pip.exe"
    if (-not (Test-Path $rpaVenvPython) -or -not (Test-Path $rpaPip)) {
        Write-Host ">>> Creating RPA virtualenv..." -ForegroundColor Cyan
        if (Test-Path (Join-Path $rpaDir "venv")) {
            Remove-Item -Recurse -Force (Join-Path $rpaDir "venv") -ErrorAction SilentlyContinue
        }
        Push-Location $rpaDir
        try {
            & $pythonCommand -m venv venv
        } finally {
            Pop-Location
        }
        if (-not (Test-Path $rpaVenvPython) -or -not (Test-Path $rpaPip)) {
            throw "RPA virtualenv creation failed: $rpaDir"
        }
        Write-Host ">>> Installing RPA dependencies..." -ForegroundColor Cyan
        Push-Location $rpaDir
        try {
            & $rpaPip install -r requirements.txt
        } finally {
            Pop-Location
        }

        $rpaPlaywright = Join-Path $rpaDir "venv\Scripts\playwright.exe"
        if (Test-Path $rpaPlaywright) {
            Write-Host ">>> Installing Playwright Chromium..." -ForegroundColor Cyan
            Push-Location $rpaDir
            try {
                & $rpaPlaywright install chromium
            } finally {
                Pop-Location
            }
        }
    } elseif (-not (Test-PythonImports -PythonExe $rpaVenvPython -Modules @("flask", "dotenv", "playwright", "requests"))) {
        Write-Host ">>> RPA virtualenv exists but dependencies are incomplete. Reinstalling RPA requirements..." -ForegroundColor Yellow
        Push-Location $rpaDir
        try {
            & $rpaPip install -r requirements.txt
        } finally {
            Pop-Location
        }

        $rpaPlaywright = Join-Path $rpaDir "venv\Scripts\playwright.exe"
        if (Test-Path $rpaPlaywright) {
            Write-Host ">>> Installing Playwright Chromium..." -ForegroundColor Cyan
            Push-Location $rpaDir
            try {
                & $rpaPlaywright install chromium
            } finally {
                Pop-Location
            }
        }
    }
    $rpaPythonCmd = """$rpaVenvPython"""

    $backendAlreadyRunning = (Test-PortListening -Port 8080) -and (Test-HttpReady -Url $backendUrl)
    $frontendAlreadyRunning = (Test-PortListening -Port 5500) -and (Test-HttpReady -Url $frontendUrl)
    $aiAlreadyRunning = (Test-PortListening -Port $aiPort) -and (Test-HttpReady -Url $aiUrl)
    $rpaAlreadyRunning = (Test-PortListening -Port $rpaPort) -and (Test-HttpReady -Url $rpaUrl)

    $backendProcess = $null
    $frontendProcess = $null
    $aiProcess = $null
    $rpaProcess = $null

    if (-not $backendAlreadyRunning) {
        if (Test-PortListening -Port 8080) {
            throw "Port 8080 is busy but backend is not responding. Stop the old process first."
        }
        Write-Host ">>> Building backend boot jar..." -ForegroundColor Cyan
        Push-Location $backendDir
        try {
            & .\gradlew.bat clean bootJar | Tee-Object -FilePath $backendLog
            if ($LASTEXITCODE -ne 0) {
                throw "Backend bootJar build failed. Check logs: $backendLog / $backendErrLog"
            }
        } finally {
            Pop-Location
        }

        $backendJar = Get-ChildItem -Path (Join-Path $backendDir "build\libs") -Filter "*.jar" -File |
            Where-Object { $_.Name -notlike "*-plain.jar" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if ($null -eq $backendJar) {
            throw "Backend boot jar not found under build\\libs after bootJar."
        }

        $backendCommand = "cd /d ""$backendDir"" && ""$javaCommand"" -DAI_SERVER_URL=http://localhost:$aiPort -jar ""$($backendJar.FullName)"" 1>""$backendLog"" 2>""$backendErrLog"""
        $backendProcess = Start-DetachedCommand -WorkingDirectory $backendDir -CommandLine $backendCommand
    }

    if (-not $frontendAlreadyRunning) {
        if (Test-PortListening -Port 5500) {
            throw "Port 5500 is busy but frontend is not responding. Stop the old process first."
        }
        $frontendCommand = "cd /d ""$frontendDir"" && ""$pythonCommand"" -m http.server 5500 1>""$frontendLog"" 2>""$frontendErrLog"""
        $frontendProcess = Start-DetachedCommand -WorkingDirectory $frontendDir -CommandLine $frontendCommand
    }

    if (-not $aiAlreadyRunning) {
        if (Test-PortListening -Port $aiPort) {
            throw "Port $aiPort is busy but AI Server is not responding. Stop the old process first."
        }
        $aiCommand = "cd /d ""$aiDir"" && $aiPythonCmd app.py 1>""$aiLog"" 2>""$aiErrLog"""
        $aiProcess = Start-DetachedCommand -WorkingDirectory $aiDir -CommandLine $aiCommand
    }

    if (-not $rpaAlreadyRunning) {
        if (Test-PortListening -Port $rpaPort) {
            throw "Port $rpaPort is busy but RPA Server is not responding. Stop the old process first."
        }
        $rpaCommand = "cd /d ""$rpaDir"" && $rpaPythonCmd app.py 1>""$rpaLog"" 2>""$rpaErrLog"""
        $rpaProcess = Start-DetachedCommand -WorkingDirectory $rpaDir -CommandLine $rpaCommand
    }

    $backendReady = if ($backendAlreadyRunning) { $true } else { Wait-HttpReady -Url $backendUrl -TimeoutSeconds 90 }
    $frontendReady = if ($frontendAlreadyRunning) { $true } else { Wait-HttpReady -Url $frontendUrl -TimeoutSeconds 30 }
    $aiReady = if ($aiAlreadyRunning) { $true } else { Wait-HttpReady -Url $aiUrl -TimeoutSeconds 30 }
    $rpaReady = if ($rpaAlreadyRunning) { $true } else { Wait-HttpReady -Url $rpaUrl -TimeoutSeconds 30 }

    if (-not $backendReady) { throw "Backend did not become ready. Check logs: $backendLog / $backendErrLog" }
    if (-not $frontendReady) { throw "Frontend did not become ready. Check logs: $frontendLog / $frontendErrLog" }
    if (-not $aiReady) { throw "AI Server did not become ready. Check logs: $aiLog / $aiErrLog" }
    if (-not $rpaReady) { throw "RPA Server did not become ready. Check logs: $rpaLog / $rpaErrLog" }

    $backendStatus = if ($backendAlreadyRunning) { "Reused existing server" } else { "Started new process (PID: $($backendProcess.Id))" }
    $frontendStatus = if ($frontendAlreadyRunning) { "Reused existing server" } else { "Started new process (PID: $($frontendProcess.Id))" }
    $aiStatus = if ($aiAlreadyRunning) { "Reused existing server" } else { "Started new process (PID: $($aiProcess.Id))" }
    $rpaStatus = if ($rpaAlreadyRunning) { "Reused existing server" } else { "Started new process (PID: $($rpaProcess.Id))" }

    $serviceState = [ordered]@{
        workspaceRoot = $workspaceRoot
        runId = $runId
        recordedAt = (Get-Date).ToString("o")
        services = @(
            [ordered]@{ name = "Backend"; port = 8080; pid = Get-ListeningPid -Port 8080 },
            [ordered]@{ name = "Frontend"; port = 5500; pid = Get-ListeningPid -Port 5500 },
            [ordered]@{ name = "AI Server"; port = $aiPort; pid = Get-ListeningPid -Port $aiPort },
            [ordered]@{ name = "RPA Server"; port = $rpaPort; pid = Get-ListeningPid -Port $rpaPort }
        )
    }
    $serviceState | ConvertTo-Json -Depth 5 | Set-Content -Path $serviceStateFile -Encoding UTF8

    cmd.exe /c start "" $frontendUrl | Out-Null

    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " DDUK ERP FULL STACK INTEGRATION RUNNING SUCCESSFULLY!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "Backend Status   : $backendStatus (URL: $backendUrl)"
    Write-Host "Frontend Status  : $frontendStatus (URL: $frontendUrl)"
    Write-Host "AI Server Status : $aiStatus (URL: $aiUrl)"
    Write-Host "RPA Server Status: $rpaStatus (URL: $rpaUrl)"
    Write-Host "=========================================================="
    Write-Host "Output Logs Location: $tmpDir"
    Write-Host "Service State File : $serviceStateFile"
    Write-Host "  - backend : $backendLog"
    Write-Host "  - frontend: $frontendLog"
    Write-Host "  - ai-serv : $aiLog"
    Write-Host "  - rpa-serv: $rpaLog"
    Write-Host "=========================================================="
    exit 0
} catch {
    Write-Host ""
    Write-Host "ai-all-start-runner failed" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Write-Host "Log folder: $tmpDir"

    if (Test-Path $backendErrLog) {
        Write-Host ""
        Write-Host "[backend.err.log]"
        Get-Content $backendErrLog -Tail 20 -ErrorAction SilentlyContinue
    }
    if (Test-Path $aiErrLog) {
        Write-Host ""
        Write-Host "[ai-server.err.log]"
        Get-Content $aiErrLog -Tail 20 -ErrorAction SilentlyContinue
    }
    if (Test-Path $rpaErrLog) {
        Write-Host ""
        Write-Host "[rpa-server.err.log]"
        Get-Content $rpaErrLog -Tail 20 -ErrorAction SilentlyContinue
    }
    exit 1
}
