#requires -Version 7.0
param(
  [string]$JdkHome,
  [string]$OutputPath,
  [string]$CourseFolder,
  [ValidateRange(1,65535)][int]$BackendPort = 8080,
  [ValidateRange(1,65535)][int]$FrontendPort = 5173
)
. (Join-Path $PSScriptRoot 'common.ps1')
if (-not $IsWindows) { throw '此工作区生成器面向 Windows；其他平台按开发指南手动配置自己的编辑器。' }
Set-ProjectJava -JdkHome $JdkHome
$taskTools = Join-Path $taskRepoRoot '.tools'
New-Item -ItemType Directory -Path $taskTools -Force | Out-Null
@{jdkHome=$env:JAVA_HOME} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskTools 'environment.local.json') -Encoding utf8
if (-not $OutputPath) {
  $OutputPath = Join-Path (Split-Path -Parent $taskRepoRoot) ((Split-Path -Leaf $taskRepoRoot) + '.local.code-workspace')
}
$taskOutput = [IO.Path]::GetFullPath($OutputPath)
$taskRepoBoundary = [IO.Path]::GetFullPath($taskRepoRoot) + [IO.Path]::DirectorySeparatorChar
if ($taskOutput.StartsWith($taskRepoBoundary,[StringComparison]::OrdinalIgnoreCase)) {
  throw '工作区包含本机路径，请将 -OutputPath 指向代码仓库外。'
}
$taskWorkspace = Get-Content -LiteralPath (Join-Path $taskRepoRoot 'config\vscode\workspace-template.json') -Raw | ConvertFrom-Json -AsHashtable
$taskWorkspace.folders = @(@{name='campus-mental-health'; path=$taskRepoRoot})
if ($CourseFolder) {
  $taskWorkspace.folders += @{name='课程资料'; path=(Resolve-Path -LiteralPath $CourseFolder).Path}
}
$taskMavenUser = Join-Path $taskTools 'maven-user'
$taskM2 = Join-Path $taskTools 'm2'
New-Item -ItemType Directory -Path $taskMavenUser -Force | Out-Null
$taskEscapedRepository = [Security.SecurityElement]::Escape($taskM2)
$taskMavenSettings = Join-Path $taskMavenUser 'settings.xml'
@"
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>$taskEscapedRepository</localRepository>
</settings>
"@ | Set-Content -LiteralPath $taskMavenSettings -Encoding utf8
$taskMavenOpts = '-Dmaven.repo.local="' + $taskM2 + '"'
$taskSettings = $taskWorkspace.settings
$taskSettings['java.jdt.ls.java.home'] = $env:JAVA_HOME
$taskSettings['java.configuration.runtimes'] = @(@{name='JavaSE-21'; path=$env:JAVA_HOME; default=$true})
$taskSettings['java.configuration.maven.userSettings'] = $taskMavenSettings
$taskSettings['maven.executable.path'] = Join-Path $taskRepoRoot 'backend\mvnw.cmd'
$taskSettings['terminal.integrated.env.windows'] = @{
  JAVA_HOME=$env:JAVA_HOME
  MAVEN_USER_HOME=$taskMavenUser
  MAVEN_OPTS=$taskMavenOpts
  SERVER_PORT=$BackendPort.ToString()
  VITE_DEV_PORT=$FrontendPort.ToString()
  VITE_BACKEND_URL=('http://127.0.0.1:' + $BackendPort)
  PATH=(Join-Path $env:JAVA_HOME 'bin') + ';${env:PATH}'
}
foreach ($taskEntry in $taskWorkspace.tasks.tasks) {
  if ($taskEntry.label -eq 'Campus: 启动后端') {
    $taskEntry.options.env = @{SERVER_PORT=$BackendPort.ToString()}
  }
  if ($taskEntry.label -eq 'Campus: 启动前端') {
    $taskEntry.options.env = @{VITE_DEV_PORT=$FrontendPort.ToString(); VITE_BACKEND_URL=('http://127.0.0.1:' + $BackendPort)}
    $taskEntry.problemMatcher.background.endsPattern = 'Local:.*127\.0\.0\.1:' + $FrontendPort
  }
}
foreach ($taskLaunch in $taskWorkspace.launch.configurations) {
  if ($taskLaunch.type -eq 'java') { $taskLaunch.env = @{SERVER_PORT=$BackendPort.ToString()} }
  if ($taskLaunch.type -eq 'pwa-chrome') { $taskLaunch.url = 'http://127.0.0.1:' + $FrontendPort }
}
$taskSettings['maven.terminal.customEnv'] = @(
  @{environmentVariable='JAVA_HOME'; value=$env:JAVA_HOME},
  @{environmentVariable='MAVEN_USER_HOME'; value=$taskMavenUser},
  @{environmentVariable='MAVEN_OPTS'; value=$taskMavenOpts}
)
if (Test-Path -LiteralPath $taskOutput) {
  # Preserve unrelated settings and folders, and keep a backup before refreshing our entries.
  $taskPrevious = Get-Content -LiteralPath $taskOutput -Raw | ConvertFrom-Json -AsHashtable
  $taskBackups = Join-Path $taskTools 'workspace-backups'
  New-Item -ItemType Directory -Path $taskBackups -Force | Out-Null
  Copy-Item -LiteralPath $taskOutput -Destination (Join-Path $taskBackups ([Guid]::NewGuid().ToString('N') + '.code-workspace'))
  foreach ($taskKey in $taskPrevious.Keys) {
    if ($taskKey -notin @('folders','settings','tasks','launch')) { $taskWorkspace[$taskKey] = $taskPrevious[$taskKey] }
  }
  foreach ($taskKey in $taskPrevious.settings.Keys) {
    if (-not $taskSettings.ContainsKey($taskKey)) { $taskSettings[$taskKey] = $taskPrevious.settings[$taskKey] }
  }
  foreach ($taskFolder in $taskPrevious.folders) {
    if ($taskFolder.name -notin @('campus-mental-health','课程资料')) { $taskWorkspace.folders += $taskFolder }
  }
  foreach ($taskExistingTask in $taskPrevious.tasks.tasks) {
    if ($taskExistingTask.label -notlike 'Campus:*') { $taskWorkspace.tasks.tasks += $taskExistingTask }
  }
  foreach ($taskExistingLaunch in $taskPrevious.launch.configurations) {
    if ($taskExistingLaunch.name -notlike 'Campus:*') { $taskWorkspace.launch.configurations += $taskExistingLaunch }
  }
}
New-Item -ItemType Directory -Path (Split-Path -Parent $taskOutput) -Force | Out-Null
$taskWorkspace | ConvertTo-Json -Depth 25 | Set-Content -LiteralPath $taskOutput -Encoding utf8
Write-Output ('已生成仓库外工作区：' + $taskOutput)
Write-Output '在 VS Code 打开此文件；安装推荐扩展后重载窗口，并新建终端。'
