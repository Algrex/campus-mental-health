$ErrorActionPreference = 'Stop'
$taskRepoRoot = Split-Path -Parent $PSScriptRoot

function Set-ProjectJava {
  if (-not $env:JAVA_HOME) {
    $taskPortableRoot = Join-Path $taskRepoRoot '.tools\jdk'
    $taskPortableJdk = Get-ChildItem -LiteralPath $taskPortableRoot -Directory -ErrorAction SilentlyContinue |
      Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
      Select-Object -First 1
    if ($taskPortableJdk) {
      $env:JAVA_HOME = $taskPortableJdk.FullName
    } else {
      $taskCompiler = Get-Command javac -ErrorAction SilentlyContinue
      if ($taskCompiler) {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $taskCompiler.Source)
      }
    }
  }
  if (-not $env:JAVA_HOME) { throw '需要 JDK 21。请参照环境配置清单准备 JAVA_HOME。' }
  $taskJavac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
  if (-not (Test-Path -LiteralPath $taskJavac)) { throw 'JAVA_HOME 必须指向含 javac 的 JDK 根目录。' }
  $taskVersion = (& $taskJavac -version 2>&1 | Out-String).Trim()
  if ($LASTEXITCODE -ne 0 -or $taskVersion -notmatch '^javac 21\.') { throw "要求 JDK 21，实际为 $taskVersion" }
  $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
  $env:MAVEN_USER_HOME = Join-Path $taskRepoRoot '.tools\maven-user'
}

function Invoke-ProjectCommand {
  param([string]$Directory, [string]$Command, [string[]]$Arguments)
  Push-Location -LiteralPath $Directory
  try {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command 失败，退出码 $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
}
