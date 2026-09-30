$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$toolsRoot = Join-Path $sdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-37.0\android.jar'
$jdkRoot = 'C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_HOME = $jdkRoot
$buildRoot = Join-Path $projectRoot 'build\direct'
$classesRoot = Join-Path $buildRoot 'classes'
$dexRoot = Join-Path $buildRoot 'dex'
New-Item -ItemType Directory -Force -Path $classesRoot, $dexRoot | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $jdkRoot 'bin\javac.exe') --release 17 -encoding UTF-8 -classpath $androidJar -d $classesRoot @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$classFiles = @(Get-ChildItem -LiteralPath $classesRoot -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $toolsRoot 'd8.bat') --min-api 28 --lib $androidJar --output $dexRoot @classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

$unsigned = Join-Path $buildRoot 'app-unsigned.apk'
& (Join-Path $toolsRoot 'aapt2.exe') link --manifest (Join-Path $projectRoot 'app\src\main\AndroidManifest.xml') -I $androidJar --min-sdk-version 28 --target-sdk-version 35 -o $unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
& (Join-Path $jdkRoot 'bin\jar.exe') uf $unsigned -C $dexRoot classes.dex
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

$aligned = Join-Path $buildRoot 'app-aligned.apk'
& (Join-Path $toolsRoot 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$keyStore = Join-Path $projectRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $keyStore)) {
    & (Join-Path $jdkRoot 'bin\keytool.exe') -genkeypair -noprompt -keystore $keyStore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\airwings-button-probe-debug.apk'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $apk) | Out-Null
& (Join-Path $toolsRoot 'apksigner.bat') sign --ks $keyStore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& (Join-Path $toolsRoot 'apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'signature verification failed' }
Write-Output $apk
