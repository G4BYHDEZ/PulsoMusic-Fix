$ErrorActionPreference = 'Stop'
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $PSScriptRoot
try {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $studioJava)) { $env:JAVA_HOME = $studioJava }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    # Java's Windows local sockets require a short temporary directory.
    $pulsoTemp = Join-Path $env:LOCALAPPDATA 'Temp\pulso-java'
    New-Item -ItemType Directory -Force -Path $pulsoTemp | Out-Null
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=$pulsoTemp"
    & .\gradlew.bat --console plain :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'La compilación falló. Revisa los mensajes anteriores.' }
    New-Item -ItemType Directory -Force -Path 'instaladores' | Out-Null
    Copy-Item -LiteralPath 'phone\build\outputs\apk\debug\phone-debug.apk' -Destination 'instaladores\pulso-telefono.apk'
    Copy-Item -LiteralPath 'wear\build\outputs\apk\debug\wear-debug.apk' -Destination 'instaladores\pulso-reloj.apk'
    Write-Host 'Listo. APK actualizados en instaladores/.'
} finally { $env:JAVA_TOOL_OPTIONS = $previousJavaOptions; Pop-Location }
