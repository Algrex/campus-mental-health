#requires -Version 7.0
param([switch]$IncludeLifestyle, [string]$LifestyleArchive)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDirectory = Join-Path $taskRoot 'data/downloads'
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
$taskSources = @(@{Csv='student-mental-health.csv'; Zip='student-mental-health.zip'; Hash='c0d1300dd5225b426f91ee75c81e5068efdadf528416628994a5a6e013063f0a'; Slug='shariful07/student-mental-health'; License='CC0: Public Domain'; Lifestyle=$false})
if ($IncludeLifestyle -or $LifestyleArchive) {
  $taskSources += @{Csv='student_lifestyle_100k.csv'; Zip='student-lifestyle.zip'; Hash='9dc57047a826daff9a442a9975fb8566339335bc8f351ae8189794aa89fc7b6d'; Slug='aldinwhyudii/student-depression-and-lifestyle-100k-data'; License='MIT'; Lifestyle=$true}
}
foreach ($taskSource in $taskSources) {
  $taskDestination = Join-Path $taskDirectory $taskSource.Csv
  if ((Test-Path -LiteralPath $taskDestination) -and ((Get-FileHash -LiteralPath $taskDestination -Algorithm SHA256).Hash -ieq $taskSource.Hash)) {
    Write-Host "Pinned dataset already available: $taskDestination"
    continue
  }
  $taskZip = Join-Path $taskDirectory $taskSource.Zip
  if ($taskSource.Lifestyle -and $LifestyleArchive) {
    Copy-Item -LiteralPath (Resolve-Path -LiteralPath $LifestyleArchive).Path -Destination $taskZip -Force
  } else {
    $taskArguments = @((Join-Path $PSScriptRoot 'download-demo-data.mjs'))
    if ($taskSource.Lifestyle) { $taskArguments += '--lifestyle' }
    & node @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Dataset download failed; check network access to Kaggle.' }
  }
  $taskStage = Join-Path $taskDirectory ('extract-' + [guid]::NewGuid().ToString('N'))
  Expand-Archive -LiteralPath $taskZip -DestinationPath $taskStage
  $taskCsv = Get-ChildItem -LiteralPath $taskStage -Filter '*.csv' -Recurse -File
  if (@($taskCsv).Count -ne 1) { throw "Unexpected dataset files in $taskStage" }
  $taskHash = (Get-FileHash -LiteralPath $taskCsv.FullName -Algorithm SHA256).Hash
  if ($taskHash -ine $taskSource.Hash) { throw "Upstream dataset changed; review source/license before updating the pinned hash. Download retained at $taskStage" }
  if (Test-Path -LiteralPath $taskDestination) { Copy-Item -LiteralPath $taskDestination -Destination (Join-Path $taskDirectory ('previous-' + [guid]::NewGuid().ToString('N') + '.csv')) }
  Copy-Item -LiteralPath $taskCsv.FullName -Destination $taskDestination -Force
  @{source=('https://www.kaggle.com/datasets/' + $taskSource.Slug); license=$taskSource.License; sha256=$taskHash.ToLowerInvariant(); downloadedAt=[DateTimeOffset]::Now.ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath ($taskDestination + '.source.json') -Encoding utf8
  Write-Host "Ready: $taskDestination. Download directory is ignored by Git."
}
Write-Host 'Demo profiles are initialized once per database. ML prefers the 100k lifestyle data; a small survey is the fallback.'
