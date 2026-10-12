#requires -Version 7.0
. (Join-Path $PSScriptRoot 'common.ps1')
Set-ProjectJava
Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'backend') -Command '.\mvnw.cmd' -Arguments @("-Dmaven.repo.local=$taskRepoRoot/.tools/m2", '-DskipTests', 'package')
