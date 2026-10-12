. (Join-Path $PSScriptRoot 'common.ps1')
Set-ProjectJava
$taskRepository = Join-Path $taskRepoRoot '.tools\m2'
Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'backend') -Command '.\mvnw.cmd' -Arguments @("-Dmaven.repo.local=$taskRepository", 'verify')
foreach ($taskStep in 'lint', 'test', 'format:check', 'build') {
  Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'frontend') -Command 'npm.cmd' -Arguments @('run', $taskStep)
}
