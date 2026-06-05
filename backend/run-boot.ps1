$workspaceRoot = Join-Path $PSScriptRoot ".."
$envFile = Join-Path $workspaceRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^(?<name>[^#\s=]+)=(?<value>.*)$") {
            $name = $Matches['name'].Trim()
            $value = $Matches['value'].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

./gradlew.bat bootRun
