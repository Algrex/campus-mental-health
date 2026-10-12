. (Join-Path $PSScriptRoot 'common.ps1')
Set-ProjectJava
$taskRepository = Join-Path $taskRepoRoot '.tools\m2'
Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'backend') -Command '.\mvnw.cmd' -Arguments @("-Dmaven.repo.local=$taskRepository", 'spotless:apply')
