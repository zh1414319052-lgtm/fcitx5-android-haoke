param(
    [string]$BuildDirectory = 'D:\haoke-build\fcitx5-source-20261106',
    [string]$SdkDirectory = 'C:\Users\Administrator\AppData\Local\Android\Sdk',
    [string]$JavaDirectory = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$HostTools = 'D:\haoke-build\fcitx5-toolchain\ucrt64',
    [string]$Version = '0.1.3-haoke-qa.7'
)
$ErrorActionPreference = 'Stop'
if ($BuildDirectory -match '[^\x00-\x7F]') { throw 'Use an ASCII build directory: Windows AIDL does not support this project in a Chinese path.' }
foreach ($required in @("$JavaDirectory\bin\java.exe", "$SdkDirectory\cmake\3.22.1\bin\cmake.exe", "$HostTools\bin\msgfmt.exe", "$HostTools\share\ECM\cmake\ECMConfig.cmake")) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
# No deletion or mirroring: the Chinese delivery directory remains authoritative.
& robocopy $PSScriptRoot $BuildDirectory /E /XD build .gradle .cxx .kotlin /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw "Source copy failed: $LASTEXITCODE" }
$env:JAVA_HOME = $JavaDirectory
$env:ANDROID_HOME = $SdkDirectory
$env:ECM_DIR = "$HostTools/share/ECM/cmake"
$env:PATH = "$JavaDirectory\bin;$HostTools\bin;$env:PATH"
Push-Location $BuildDirectory
try {
    & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug '-PbuildABI=armeabi-v7a,arm64-v8a,x86,x86_64' '-PbuildUniversalApk=true' '-PcmakeVersion=3.22.1' '-PbuildTools=36.0.0' "-PbuildVersionName=$Version" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "QA build/check failed: $LASTEXITCODE" }
    $universalApk = Join-Path $BuildDirectory "app\build\outputs\apk\debug\org.fcitx.fcitx5.android-$Version-universal-debug.apk"
    if (!(Test-Path -LiteralPath $universalApk)) { throw "Universal QA APK missing: $universalApk" }
    # Check the actual artifact, so an environment ABI override cannot silently
    # turn a successful build into another single-architecture delivery.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($universalApk)
    try {
        foreach ($abi in @('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')) {
            foreach ($library in @('native-lib', 'anthy', 'chewing', 'hangul', 'unikey', 'sayura', 'rime', 'jyutping', 'IMEJyutping', 'ime_jyutpingdict')) {
                if (!$archive.GetEntry("lib/$abi/lib$library.so")) {
                    throw "Universal QA APK is missing $library for $abi"
                }
            }
        }
        foreach ($asset in @('bundled/clearurls.json', 'usr/share/rime-data/luna_pinyin.schema.yaml',
                'usr/share/rime-data/haoke_pinyin_t9.schema.yaml',
                'usr/share/fcitx5/inputmethod/haoke-pinyin-t9.conf')) {
            if (!$archive.GetEntry("assets/$asset")) { throw "Bundled extension data missing: $asset" }
        }
    } finally { $archive.Dispose() }
    Write-Output "Universal QA APK: $universalApk"
} finally { Pop-Location }
