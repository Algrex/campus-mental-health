#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$taskRepoRoot = Split-Path -Parent $PSScriptRoot
$taskCode = Get-Command code -ErrorAction Stop
$taskManifest = Get-Content -LiteralPath (Join-Path $taskRepoRoot 'dev-environment.json') -Raw | ConvertFrom-Json
$taskRecordDirectory = Join-Path $taskRepoRoot '.tools\environment-state'
New-Item -ItemType Directory -Path $taskRecordDirectory -Force | Out-Null
$taskBefore = @(& $taskCode.Source --list-extensions --show-versions)
if ($LASTEXITCODE -ne 0) { throw '无法记录扩展安装前清单。' }
$taskBefore | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRecordDirectory 'reproduction-extensions-before.json') -Encoding utf8
foreach ($taskExtension in $taskManifest.editorExtensions) {
  & $taskCode.Source --install-extension ($taskExtension.id + '@' + $taskExtension.version)
  if ($LASTEXITCODE -ne 0) { throw ('扩展安装失败：' + $taskExtension.id) }
}
$taskAfter = @(& $taskCode.Source --list-extensions --show-versions)
if ($LASTEXITCODE -ne 0) { throw '无法记录扩展安装后清单。' }
$taskAfter | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRecordDirectory 'reproduction-extensions-after.json') -Encoding utf8
Write-Output '已安装或复用清单中的扩展；请重载 VS Code。实际版本以安装后清单为准。'
