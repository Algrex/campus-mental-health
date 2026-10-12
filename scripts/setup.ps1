#requires -Version 7.0
param(
  [string]$JdkHome,
  [string]$JdkArchive,
  [string]$WorkspacePath,
  [switch]$StrictVersions,
  [switch]$SkipFrontendInstall,
  [switch]$SkipEditorWorkspace,
  [switch]$InstallEditorExtensions
)
. (Join-Path $PSScriptRoot 'common.ps1')
if (-not $IsWindows) { throw '此初始化脚本面向 Windows；其他平台参照开发指南的手动命令。' }
if ($JdkHome -and $JdkArchive) { throw 'JdkHome 与 JdkArchive 二选一。' }
$taskManifest = Get-Content -LiteralPath (Join-Path $taskRepoRoot 'dev-environment.json') -Raw | ConvertFrom-Json
$taskTools = Join-Path $taskRepoRoot '.tools'
New-Item -ItemType Directory -Path $taskTools -Force | Out-Null

if ($JdkArchive) {
  $taskArchive = (Resolve-Path -LiteralPath $JdkArchive).Path
  $taskArchiveHash = (Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash
  if ($taskArchiveHash -ine $taskManifest.windowsJdkArchive.sha256) {
    throw 'JDK ZIP 与环境清单的 Windows x64 基准校验值不符；其他 JDK 21 请解压后用 -JdkHome 指定。'
  }
  $taskJdkDirectory = Join-Path $taskTools 'jdk'
  $taskBaselineJdk = Join-Path $taskJdkDirectory ('jdk-' + $taskManifest.baseline.jdk + '+1')
  if (-not (Test-Path -LiteralPath (Join-Path $taskBaselineJdk 'bin\javac.exe'))) {
    Expand-Archive -LiteralPath $taskArchive -DestinationPath $taskJdkDirectory
  }
  $JdkHome = $taskBaselineJdk
}
Set-ProjectJava -JdkHome $JdkHome
$taskCompilerVersion = (& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -version 2>&1 | Out-String).Trim() -replace '^javac\s+', ''
$taskNodeCommand = Get-Command node -ErrorAction Stop
$taskNpmCommand = Get-Command npm.cmd -ErrorAction Stop
$taskNodeVersion = (& $taskNodeCommand.Source --version).TrimStart('v')
if ($LASTEXITCODE -ne 0) { throw 'Node.js 版本检查失败。' }
$taskNpmVersion = (& $taskNpmCommand.Source --version).Trim()
if ($LASTEXITCODE -ne 0) { throw 'npm 版本检查失败。' }
if ([version]$taskNodeVersion -lt [version]$taskManifest.baseline.node -or ([version]$taskNpmVersion).Major -lt 11) {
  throw '需要 Node.js 24.15.0 或更高版本、npm 11 或更高版本。'
}
foreach ($taskPair in @(
  @{Name='JDK'; Actual=$taskCompilerVersion; Expected=$taskManifest.baseline.jdk},
  @{Name='Node'; Actual=$taskNodeVersion; Expected=$taskManifest.baseline.node},
  @{Name='npm'; Actual=$taskNpmVersion; Expected=$taskManifest.baseline.npm}
)) {
  if ($taskPair.Actual -ne $taskPair.Expected) {
    $taskNotice = "$($taskPair.Name) 实际版本 $($taskPair.Actual)，记录基准为 $($taskPair.Expected)"
    if ($StrictVersions) { throw $taskNotice } else { Write-Warning $taskNotice }
  }
}
@{jdkHome=$env:JAVA_HOME} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskTools 'environment.local.json') -Encoding utf8

$taskBootstrapJson = & $taskNodeCommand.Source (Join-Path $PSScriptRoot 'bootstrap-maven.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Maven 分发包准备失败。' }
$taskBootstrap = $taskBootstrapJson | ConvertFrom-Json
if (-not $taskBootstrap.cached) {
  $taskStage = Join-Path $taskTools ('maven-unpack-' + [Guid]::NewGuid().ToString('N'))
  $taskToolsBoundary = [IO.Path]::GetFullPath($taskTools) + [IO.Path]::DirectorySeparatorChar
  $taskSource = [IO.Path]::GetFullPath((Join-Path $taskStage $taskBootstrap.distribution))
  $taskTarget = [IO.Path]::GetFullPath($taskBootstrap.target)
  if (-not $taskSource.StartsWith($taskToolsBoundary,[StringComparison]::OrdinalIgnoreCase) -or -not $taskTarget.StartsWith($taskToolsBoundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Maven 路径超出本仓库工具目录。' }
  if (Test-Path -LiteralPath $taskTarget) { throw 'Maven 缓存目录存在但不完整，请确认后手动清理该缓存目录。' }
  Expand-Archive -LiteralPath $taskBootstrap.archive -DestinationPath $taskStage
  New-Item -ItemType Directory -Path (Split-Path -Parent $taskTarget) -Force | Out-Null
  Move-Item -LiteralPath $taskSource -Destination $taskTarget
  if (@(Get-ChildItem -LiteralPath $taskStage -Force).Count -eq 0) { Remove-Item -LiteralPath $taskStage }
}
$taskM2 = Join-Path $taskTools 'm2'
Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'backend') -Command '.\mvnw.cmd' -Arguments @("-Dmaven.repo.local=$taskM2", '-v')
if (-not $SkipFrontendInstall) {
  Invoke-ProjectCommand -Directory (Join-Path $taskRepoRoot 'frontend') -Command 'npm.cmd' -Arguments @('ci', '--offline=false', '--cache', (Join-Path $taskTools 'npm-cache'), '--no-audit', '--no-fund')
}
if (-not $SkipEditorWorkspace) {
  $taskWorkspaceArguments = @{JdkHome=$env:JAVA_HOME}
  if ($WorkspacePath) { $taskWorkspaceArguments.OutputPath = $WorkspacePath }
  & (Join-Path $PSScriptRoot 'configure-workspace.ps1') @taskWorkspaceArguments
}
if ($InstallEditorExtensions) { & (Join-Path $PSScriptRoot 'install-editor-extensions.ps1') }
Write-Output '初始化完成。接着运行 scripts/check.ps1，再分别启动前后端。'
