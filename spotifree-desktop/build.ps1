$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools = if ($env:WEBVIEW2_SDK_DIR) { $env:WEBVIEW2_SDK_DIR } else { Join-Path $env:TEMP 'spotifree-tools-20261012\webview2' }
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$output = Join-Path $project 'dist'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$lib = Join-Path $tools 'lib\net462'
if (!(Test-Path (Join-Path $lib 'Microsoft.Web.WebView2.Core.dll'))) { $lib = $output }
if (!(Test-Path (Join-Path $lib 'Microsoft.Web.WebView2.Core.dll'))) {
    $tools = Join-Path $project '.tools\webview2'
    $package = Join-Path $project '.tools\webview2.nupkg'
    $archive = Join-Path $project '.tools\webview2.zip'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $package) | Out-Null
    Invoke-WebRequest -Uri 'https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.4258.31/microsoft.web.webview2.1.0.4258.31.nupkg' -OutFile $package
    $expected = '56f7f4b8bf9aee4b8efefbbdd4f67d5f74ebd1b100ed0806da71bf76af481aa9'
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $package).Hash.ToLowerInvariant() -ne $expected) { throw 'WebView2 package checksum mismatch' }
    Copy-Item $package $archive -Force
    Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
    $lib = Join-Path $tools 'lib\net462'
}
$exe = Join-Path $output 'Spotifree.exe'
$core = Join-Path $lib 'Microsoft.Web.WebView2.Core.dll'
$forms = Join-Path $lib 'Microsoft.Web.WebView2.WinForms.dll'
$source = Join-Path $project 'Program.cs'
& $compiler /nologo /target:winexe "/out:$exe" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:Microsoft.VisualBasic.dll "/reference:$core" "/reference:$forms" $source
if ($LASTEXITCODE -ne 0) { throw 'C# compilation failed' }
if ($lib -ne $output) {
    Copy-Item (Join-Path $lib 'Microsoft.Web.WebView2.Core.dll') $output -Force
    Copy-Item (Join-Path $lib 'Microsoft.Web.WebView2.WinForms.dll') $output -Force
}
$loader = Join-Path $tools 'runtimes\win-x64\native\WebView2Loader.dll'
if (Test-Path $loader) { Copy-Item $loader $output -Force }
elseif (!(Test-Path (Join-Path $output 'WebView2Loader.dll'))) { throw 'WebView2Loader.dll missing.' }
Copy-Item (Join-Path $project 'blocked-hosts.txt') $output -Force
Write-Host "Built $output\Spotifree.exe"
