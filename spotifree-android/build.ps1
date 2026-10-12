$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
    elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
    else { Join-Path $env:TEMP 'spotifree-tools-20261012\android-sdk' }
$buildTools = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
if (!(Test-Path $androidJar)) { throw 'Android SDK platform 35 not found; set ANDROID_SDK_ROOT.' }
$build = Join-Path $project 'build'
$compiled = Join-Path $build 'compiled'
$generated = Join-Path $build 'generated'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$dist = Join-Path $project 'dist'
$projectRoot = (Resolve-Path -LiteralPath $project).Path.TrimEnd('\')
foreach ($path in @($compiled, $generated, $classes, $dex)) {
    if (Test-Path -LiteralPath $path) {
        $resolved = (Resolve-Path -LiteralPath $path).Path
        if (!$resolved.StartsWith($projectRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe build-directory cleanup' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
foreach ($path in @($compiled, $generated, $classes, $dex, $dist)) { New-Item -ItemType Directory -Force -Path $path | Out-Null }

$aapt2 = Join-Path $buildTools 'aapt2.exe'
& $aapt2 compile --dir (Join-Path $project 'res') -o $compiled
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
$flats = @(Get-ChildItem -Path $compiled -Filter '*.flat' -Recurse -File | ForEach-Object FullName)
$unsigned = Join-Path $build 'resources.apk'
& $aapt2 link -o $unsigned --manifest (Join-Path $project 'AndroidManifest.xml') -I $androidJar --java $generated -A (Join-Path $project 'assets') @flats
if ($LASTEXITCODE -ne 0) { throw 'Resource linking failed' }

$javaFiles = @(Get-ChildItem -Path (Join-Path $project 'src') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
$javaFiles += @(Get-ChildItem -Path $generated -Filter '*.java' -Recurse -File | ForEach-Object FullName)
& javac.exe -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
$classesJar = Join-Path $build 'classes.jar'
& jar.exe --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Class packaging failed' }
& java.exe -cp (Join-Path $buildTools 'lib\d8.jar') com.android.tools.r8.D8 --min-api 24 --lib $androidJar --output $dex $classesJar
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed' }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$withDex = Join-Path $build 'with-dex.apk'
Copy-Item $unsigned $withDex -Force
$zip = [System.IO.Compression.ZipFile]::Open($withDex, [System.IO.Compression.ZipArchiveMode]::Update)
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null }
finally { $zip.Dispose() }
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $buildTools 'zipalign.exe') -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }

$key = Join-Path $build 'spotifree-debug.keystore'
if (!(Test-Path $key)) {
    & keytool.exe -genkeypair -keystore $key -alias spotifree-debug -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Spotifree Debug,O=Local Build,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed' }
}
$apk = Join-Path $dist 'Spotifree-debug.apk'
& (Join-Path $buildTools 'apksigner.bat') sign --ks $key --ks-key-alias spotifree-debug --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Write-Host "Built $apk"
