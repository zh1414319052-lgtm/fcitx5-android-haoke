param(
    [string]$BuildDirectory = 'D:\haoke-build\fcitx5-source-20261106',
    [string]$Version = '0.1.3-haoke.3',
    [string]$Identity = "$env:LOCALAPPDATA\Haoke\AndroidSigning\fcitx5-haoke-release\signing-identity.json",
    [bool]$UpdatesEnabled = $true,
    [switch]$Unsigned,
    [switch]$SkipTests,
    [string]$SignedOutput = ''
)
$ErrorActionPreference = 'Stop'
if ($BuildDirectory -match '[^\x00-\x7F]') { throw 'Use the established ASCII build directory.' }
$CertificateSha256 = ''
if (-not $Unsigned) {
    $publicIdentity = Get-Content -LiteralPath $Identity -Raw | ConvertFrom-Json
    if ($publicIdentity.applicationId -ne 'org.fcitx.fcitx5.android.haoke') { throw 'Wrong product signing identity.' }
    $CertificateSha256 = $publicIdentity.certificateSha256
    if ($CertificateSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid public release certificate fingerprint.' }
}
# Signing is external to Gradle. This script never creates or replaces a product key.
if ($env:SIGN_KEY -or $env:SIGN_KEY_PWD -or $env:SIGN_KEY_ALIAS) { throw 'Unset inherited signing variables; the prepared release must be unsigned.' }
$jbr = 'C:\Program Files\Android\Android Studio\jbr'
$sdk = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$hostTools = 'D:\haoke-build\fcitx5-toolchain\ucrt64'
& robocopy $PSScriptRoot $BuildDirectory /E /XD build .gradle .cxx .kotlin "$PSScriptRoot\third-party" /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw "Source copy failed: $LASTEXITCODE" }
$env:JAVA_HOME = $jbr
$env:ANDROID_HOME = $sdk
$env:ECM_DIR = "$hostTools/share/ECM/cmake"
$env:PATH = "$jbr\bin;$hostTools\bin;$env:PATH"
Push-Location $BuildDirectory
try {
    # An explicit release-only request may skip tests/lint; signing and artifact
    # identity checks remain mandatory. Ordinary builds retain the existing checks.
    $buildTasks = @(':app:assembleRelease')
    if (-not $SkipTests) { $buildTasks = @(':app:testDebugUnitTest', ':app:assembleRelease', ':app:lintRelease') }
    & .\gradlew.bat @buildTasks '-PbuildABI=armeabi-v7a,arm64-v8a,x86,x86_64' '-PbuildUniversalApk=true' '-PcmakeVersion=3.22.1' '-PbuildTools=36.0.0' "-PbuildVersionName=$Version" "-PhaokeCertificateSha256=$CertificateSha256" "-PhaokeUpdatesEnabled=$($UpdatesEnabled.ToString().ToLowerInvariant())" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Formal preparation/check failed: $LASTEXITCODE" }
    if ($Unsigned) {
        Write-Output 'UNSIGNED preparation only; not a formal distributable.'
    } else {
        $metadata = Get-Content -LiteralPath '.\app\build\outputs\apk\release\output-metadata.json' -Raw | ConvertFrom-Json
        $universal = @($metadata.elements | Where-Object { $_.filters.Count -eq 0 })
        if ($universal.Count -ne 1) { throw 'Expected exactly one universal release APK.' }
        $inputApk = Join-Path "$BuildDirectory\app\build\outputs\apk\release" $universal[0].outputFile
        $outputApk = if ($SignedOutput) { $SignedOutput } else { "$BuildDirectory\app\build\outputs\apk\release\fcitx5-android-$Version-universal-signed.apk" }
        & python "$PSScriptRoot\tools\sign_release.py" --identity $Identity --input $inputApk --output $outputApk --sdk $sdk --java-home $jbr --version $Version
        if ($LASTEXITCODE -ne 0) { throw 'Formal signing/identity verification failed.' }
        Write-Output "Verified signed formal APK: $outputApk"
    }
} finally { Pop-Location }
