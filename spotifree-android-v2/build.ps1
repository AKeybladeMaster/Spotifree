$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$sdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:TEMP 'spotifree-tools-20261017\android-sdk'), (Join-Path $env:TEMP 'spotifree-tools-20261012\android-sdk')) | Where-Object { $_ }
$sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms\android-35\android.jar')) } | Select-Object -First 1
if (!$sdk) { throw 'Android SDK platform 35 was not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.' }
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
$tools = Join-Path $sdk 'build-tools\35.0.0'
if (!(Test-Path -LiteralPath $androidJar)) { throw "Android SDK platform 35 is missing at $androidJar. Set ANDROID_SDK_ROOT." }
foreach ($tool in @('aapt2.exe','zipalign.exe','apksigner.bat','lib\d8.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $tools $tool))) { throw "Required Android build tool missing: $tool" }
}
$build = Join-Path $project 'build'
$dist = Join-Path $project 'dist'
$generatedDirs = @('compiled','generated','classes','dex') | ForEach-Object { Join-Path $build $_ }
$projectPrefix = $project.TrimEnd('\') + '\'
foreach ($path in $generatedDirs) {
    if (Test-Path -LiteralPath $path) {
        $resolved = (Resolve-Path -LiteralPath $path).Path
        if (!$resolved.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to clean a build path outside $project" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$compiled = Join-Path $build 'compiled'
$generated = Join-Path $build 'generated'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$aapt2 = Join-Path $tools 'aapt2.exe'
& $aapt2 compile --dir (Join-Path $project 'res') -o $compiled
if ($LASTEXITCODE -ne 0) { throw 'Android resource compilation failed.' }
$flats = @(Get-ChildItem -Path $compiled -Filter '*.flat' -Recurse -File | ForEach-Object FullName)
$resourcesApk = Join-Path $build 'resources.apk'
& $aapt2 link -o $resourcesApk --manifest (Join-Path $project 'AndroidManifest.xml') -I $androidJar --java $generated -A (Join-Path $project 'assets') @flats
if ($LASTEXITCODE -ne 0) { throw 'Android resource linking failed.' }
$java = @(Get-ChildItem -Path (Join-Path $project 'src') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
$java += @(Get-ChildItem -Path $generated -Filter '*.java' -Recurse -File | ForEach-Object FullName)
& javac.exe -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes @java
if ($LASTEXITCODE -ne 0) { throw 'Android Java compilation failed.' }
$classesJar = Join-Path $build 'classes.jar'
& jar.exe --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Android class packaging failed.' }
& java.exe -cp (Join-Path $tools 'lib\d8.jar') com.android.tools.r8.D8 --min-api 23 --lib $androidJar --output $dex $classesJar
if ($LASTEXITCODE -ne 0) { throw 'Android DEX generation failed.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$withDex = Join-Path $build 'with-dex.apk'
Copy-Item -LiteralPath $resourcesApk -Destination $withDex -Force
$archive = [System.IO.Compression.ZipFile]::Open($withDex, [System.IO.Compression.ZipArchiveMode]::Update)
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null }
finally { $archive.Dispose() }
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $tools 'zipalign.exe') -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
$key = Join-Path $build 'spotifree-v2-debug.keystore'
if (!(Test-Path -LiteralPath $key)) {
    & keytool.exe -genkeypair -keystore $key -alias spotifree-v2-debug -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Spotifree Debug,O=Local Build,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'Debug signing key generation failed.' }
}
$apk = Join-Path $dist 'spotifree-android.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks $key --ks-key-alias spotifree-v2-debug --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
& (Join-Path $tools 'apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Write-Host "Android v2 built: $apk"
