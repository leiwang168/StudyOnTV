param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Keystore = "$env:USERPROFILE\.android\studyontv-recovery-debug.keystore"
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($SdkRoot) -or !(Test-Path -LiteralPath $SdkRoot)) { throw 'Set -SdkRoot to your Android SDK directory.' }
if (!(Test-Path -LiteralPath "$JavaHome\bin\java.exe")) { throw 'Set -JavaHome to JDK 17 or newer.' }
if (!(Test-Path -LiteralPath $Keystore)) { throw 'Existing recovery signing key is required; do not replace it for an installed app.' }
$env:JAVA_HOME = $JavaHome
$repo = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$out = Join-Path $repo 'release-artifacts\recovery-personal'
$tools = Get-ChildItem "$SdkRoot\build-tools" -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$platform = Get-ChildItem "$SdkRoot\platforms" -Directory | Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending | Select-Object -First 1
if (!$tools -or !$platform) { throw 'Android SDK platform and build-tools are required.' }
$android = Join-Path $platform.FullName 'android.jar'
New-Item -ItemType Directory -Force "$out\classes", "$out\dex" | Out-Null
$sources = Get-ChildItem "$PSScriptRoot\src" -Recurse -Filter '*.java' | ForEach-Object FullName
& "$JavaHome\bin\javac.exe" -source 8 -target 8 -encoding UTF-8 -cp $android -d "$out\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = Get-ChildItem "$out\classes" -Recurse -Filter '*.class' | ForEach-Object FullName
& "$($tools.FullName)\d8.bat" --min-api 23 --lib $android --output "$out\dex" $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& "$($tools.FullName)\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -I $android -F "$out\unsigned.apk" --min-sdk-version 23 --target-sdk-version 34
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
Push-Location "$out\dex"
try {
    & "$($tools.FullName)\aapt.exe" add "$out\unsigned.apk" classes.dex
    if ($LASTEXITCODE -ne 0) { throw 'aapt add failed' }
} finally { Pop-Location }
& "$($tools.FullName)\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& "$($tools.FullName)\apksigner.bat" sign --ks $Keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$out\StudyOnTV-Recovery-personal.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'signing failed' }
& "$($tools.FullName)\apksigner.bat" verify --verbose "$out\StudyOnTV-Recovery-personal.apk"
if ($LASTEXITCODE -ne 0) { throw 'signature verification failed' }
Write-Output "$out\StudyOnTV-Recovery-personal.apk"
