$toolchainRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\.toolchains')
$nodeHome = Join-Path $toolchainRoot 'node-v20.19.5-win-x64'
$jdkHome = Join-Path $toolchainRoot 'jdk-17'
$androidHome = Join-Path $toolchainRoot 'android-sdk'

if (-not (Test-Path (Join-Path $nodeHome 'node.exe'))) {
    throw "Node.js toolchain was not found at $nodeHome"
}

if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
    throw "JDK toolchain was not found at $jdkHome"
}

if (-not (Test-Path (Join-Path $androidHome 'platforms\android-35\android.jar'))) {
    throw "Android SDK 35 was not found at $androidHome"
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $androidHome
$env:Path = "$nodeHome;$jdkHome\bin;$androidHome\platform-tools;$env:Path"

Write-Host "Node.js $(& node --version), npm $(& npm --version)"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "This shell is ready for npm and Gradle commands."
