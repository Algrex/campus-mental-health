#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskStatePath = Join-Path $taskRoot '.tools/demo-processes.json'
if (-not (Test-Path -LiteralPath $taskStatePath)) { Write-Host 'No recorded background demo. Stop foreground dev terminals with Ctrl+C.'; return }
$taskState = Get-Content -LiteralPath $taskStatePath -Raw | ConvertFrom-Json
foreach ($taskService in 'backend','frontend') {
  $taskId = $taskState.$taskService
  $taskStartField = $taskService + 'Start'
  $taskProcess = Get-Process -Id $taskId -ErrorAction SilentlyContinue
  if (-not $taskProcess) { Write-Host "$taskService is already stopped."; continue }
  if ($taskProcess.StartTime.ToUniversalTime().Ticks -ne ([datetime]$taskState.$taskStartField).ToUniversalTime().Ticks) { Write-Warning "PID $taskId was reused; leaving it untouched."; continue }
  $taskCommand = (Get-CimInstance Win32_Process -Filter "ProcessId=$taskId").CommandLine
  $taskExpected = if ($taskService -eq 'backend') { '*../.tools/demo-run/app.jar*' } else { '*node_modules/vite/bin/vite.js*' }
  if ($taskCommand -notlike $taskExpected) { Write-Warning "Unexpected command for $taskService; leaving it untouched."; continue }
  Stop-Process -Id $taskId
  $taskProcess.WaitForExit(5000) | Out-Null
  Write-Host "$taskService stopped. Business database and model artifacts were preserved."
}
