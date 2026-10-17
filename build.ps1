[CmdletBinding()]
param([ValidateSet('all','android','desktop')][string]$Target = 'all')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$targets = if ($Target -eq 'all') { @('android','desktop') } else { @($Target) }
foreach ($name in $targets) {
    $script = if ($name -eq 'android') { Join-Path $projectRoot 'spotifree-android-v2\build.ps1' } else { Join-Path $projectRoot 'spotifree-desktop-v2\build.ps1' }
    if (!(Test-Path -LiteralPath $script)) { throw "Build script missing: $script" }
    Write-Host "Building Spotifree $name v2..."
    & $script
    if (!$?) { throw "Spotifree $name v2 build failed." }
}
Write-Host 'Build finished.'
if ($Target -eq 'all' -or $Target -eq 'android') { Write-Host (Join-Path $projectRoot 'spotifree-android-v2\dist\Spotifree-v2-debug.apk') }
if ($Target -eq 'all' -or $Target -eq 'desktop') { Write-Host (Join-Path $projectRoot 'spotifree-desktop-v2\dist\Spotifree-v2.exe') }