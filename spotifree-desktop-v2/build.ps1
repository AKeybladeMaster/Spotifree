$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$cacheCandidates = @((Join-Path $env:TEMP 'spotifree-tools-20261017\webview2'), (Join-Path $env:TEMP 'spotifree-tools-20261012\webview2'))
$sdk = @($env:WEBVIEW2_SDK_DIR) + $cacheCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'lib\net462\Microsoft.Web.WebView2.Core.dll')) } | Select-Object -First 1
if (!$sdk) {
    $sdk = Join-Path $env:TEMP 'spotifree-v2-tools\webview2'
    $package = Join-Path $env:TEMP 'spotifree-v2-tools\webview2.1.0.4258.31.nupkg'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $package) | Out-Null
    if (!(Test-Path -LiteralPath $package)) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri 'https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.4258.31/microsoft.web.webview2.1.0.4258.31.nupkg' -OutFile $package
    }
    $expected = '56f7f4b8bf9aee4b8efefbbdd4f67d5f74ebd1b100ed0806da71bf76af481aa9'
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $package).Hash.ToLowerInvariant() -ne $expected) { throw 'WebView2 NuGet package checksum mismatch.' }
    if (!(Test-Path -LiteralPath (Join-Path $sdk 'lib\net462\Microsoft.Web.WebView2.Core.dll'))) {
        New-Item -ItemType Directory -Force -Path $sdk | Out-Null
        Expand-Archive -LiteralPath $package -DestinationPath $sdk -Force
    }
}
$lib = Join-Path $sdk 'lib\net462'
$core = Join-Path $lib 'Microsoft.Web.WebView2.Core.dll'
$forms = Join-Path $lib 'Microsoft.Web.WebView2.WinForms.dll'
$loader = Join-Path $sdk 'runtimes\win-x64\native\WebView2Loader.dll'
foreach ($required in @($core, $forms, $loader)) { if (!(Test-Path -LiteralPath $required)) { throw "Missing WebView2 SDK file: $required" } }
$candidates = @('C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe','C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe')
$compiler = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (!$compiler) { throw 'The .NET Framework C# compiler (csc.exe) is required to build the Windows desktop app.' }
$output = Join-Path $project 'dist'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$exe = Join-Path $output 'Spotifree-windows.exe'
$sources = @(Get-ChildItem -Path (Join-Path $project 'src') -Filter '*.cs' -Recurse -File | ForEach-Object FullName)
if ($sources.Count -lt 3) { throw 'Expected Program, PlayerForm, and HiddenHostFilter C# source files.' }
& $compiler /nologo /target:winexe /platform:x64 "/out:$exe" /reference:System.dll /reference:System.Core.dll /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/reference:$core" "/reference:$forms" @sources
if ($LASTEXITCODE -ne 0) { throw 'Desktop C# compilation failed.' }
Copy-Item -LiteralPath $core -Destination $output -Force
Copy-Item -LiteralPath $forms -Destination $output -Force
Copy-Item -LiteralPath $loader -Destination $output -Force
Copy-Item -LiteralPath (Join-Path $project 'assets\blocked-hosts.txt') -Destination $output -Force
Write-Host "Desktop v2 built: $exe"
Write-Host 'Requires the Microsoft Edge WebView2 Runtime on the target Windows PC.'