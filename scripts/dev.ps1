param([ValidateSet('backend', 'frontend')][string]$Service = 'frontend')
. (Join-Path $PSScriptRoot 'common.ps1')

if ($Service -eq 'backend') {
  Set-ProjectJava
  $taskRepository = Join-Path $taskRepoRoot '.tools\m2'
  Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'backend') -Command '.\mvnw.cmd' -Arguments @("-Dmaven.repo.local=$taskRepository", 'spring-boot:run')
} else {
  Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'frontend') -Command 'npm.cmd' -Arguments @('run', 'dev')
}
