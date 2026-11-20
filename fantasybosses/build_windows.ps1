$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$version = '9.2.1'
if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (!(Test-Path $java)) { throw 'JAVA_HOME is invalid. Set it to a JDK 21 directory.' }
} else {
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $env:JAVA_HOME = Split-Path (Split-Path $java)
}
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { throw 'JDK required, not a JRE. Install JDK 21 and set JAVA_HOME.' }
$ErrorActionPreference = 'Continue'
$javaInfo = (& $java -version 2>&1 | Out-String)
$javaExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($javaExit -ne 0) { throw "Java execution failed: $javaInfo" }
if ($javaInfo -notmatch 'version "21(?:[."]|$)') { throw "Java 21 required. Found: $javaInfo" }
$tools = Join-Path $PSScriptRoot '.build-tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$gradle = Join-Path $tools "gradle-$version\bin\gradle.bat"
if (!(Test-Path $gradle)) {
    $url = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
    $zip = Join-Path $tools "gradle-$version-bin.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $checksumFile = "$zip.sha256"
    try {
        Write-Host 'Retrieving official Gradle SHA-256 checksum...'
        Invoke-WebRequest -UseBasicParsing -Uri "$url.sha256" -OutFile $checksumFile
        $checksumText = [System.IO.File]::ReadAllText($checksumFile)
        if ($checksumText -notmatch '^\s*([0-9a-fA-F]{64})(?:\s|$)') {
            throw 'Invalid SHA-256 response from official Gradle distribution server.'
        }
        $expected = $Matches[1]
    } finally {
        if (Test-Path -LiteralPath $checksumFile) { Remove-Item -LiteralPath $checksumFile -Force }
    }
    if (Test-Path -LiteralPath $zip) {
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash -eq $expected) {
            Write-Host 'Reusing previously downloaded Gradle ZIP (SHA-256 verified).'
        } else {
            Write-Host 'Existing Gradle ZIP failed SHA-256 verification; downloading again.'
            Remove-Item -LiteralPath $zip -Force
        }
    }
    if (!(Test-Path -LiteralPath $zip)) {
        Write-Host "Downloading Gradle $version from official distribution..."
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash -ne $expected) {
        Remove-Item -LiteralPath $zip -Force
        throw 'Gradle SHA-256 mismatch.'
    }
    Write-Host 'Gradle SHA-256 verification passed.'
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    Remove-Item -LiteralPath $zip
}
Write-Host 'Building FantasyBosses. First run downloads Minecraft/NeoForge/GeckoLib dependencies.'
$ErrorActionPreference = 'Continue'
& $gradle --no-daemon clean build --console=plain 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $PSScriptRoot 'build-output.log')
$buildExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($buildExit -ne 0) { throw 'Gradle build failed. Share build-output.log.' }
$jar = Join-Path $PSScriptRoot 'build\libs\fantasybosses-0.7.5.1.jar'
if (!(Test-Path $jar)) { throw 'Expected mod JAR not found.' }
Write-Host "SUCCESS: $jar"
