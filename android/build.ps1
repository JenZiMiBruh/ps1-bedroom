# Builds the Android APK from src\ps1_bedroom.html. Run via "Build APK.bat" in the main folder.
# Pipeline (no Gradle needed): aapt2 -> javac -> d8 -> zipalign -> apksigner
$ErrorActionPreference = 'Stop'
$here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$root  = Split-Path -Parent $here
$tools = "$env:LOCALAPPDATA\PS1BedroomBuildTools"   # Java + Android build tools (kept out of OneDrive)
if (-not (Test-Path "$tools\jdk")) { throw "Build tools not found in $tools" }
$jdk   = (Get-ChildItem "$tools\jdk" -Directory)[0].FullName
$java  = "$jdk\bin"
$bt    = (Get-ChildItem "$tools\build-tools" -Directory)[0].FullName
$aj    = "$tools\platform\android.jar"
$app   = "$here\app"
$out   = "$env:TEMP\ps1bedroom-build"
$env:JAVA_HOME = $jdk

function Run($exe, [string[]]$argv) {
  & $exe @argv
  if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($exe)) failed with exit code $LASTEXITCODE" }
}

# 0. Copy the page into the app, swapping the online script links for the bundled copies
$html = [IO.File]::ReadAllText("$root\src\ps1_bedroom.html")
$map = @{
  'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js' = 'three.min.js'
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js' = 'OrbitControls.js'
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/utils/BufferGeometryUtils.js' = 'BufferGeometryUtils.js'
  'https://cdnjs.cloudflare.com/ajax/libs/tone/14.8.49/Tone.js' = 'Tone.js'
}
foreach ($k in $map.Keys) { $html = $html.Replace($k, $map[$k]) }
if ($html -match 'https?://[^"''\s)]+\.js') { throw "Still loading a script from the internet: $($Matches[0])" }
[IO.File]::WriteAllText("$app\assets\index.html", $html, (New-Object Text.UTF8Encoding $false))

# Version comes from APP_VERSION in the HTML (e.g. '1.0' -> versionName 1.0, versionCode 100)
if ($html -notmatch "const APP_VERSION = '([0-9]+)\.([0-9]+)(?:\.([0-9]+))?'") { throw 'APP_VERSION not found in the HTML' }
$ver = $Matches[0] -replace ".*'(.*)'", '$1'
$code = [int]$Matches[1] * 10000 + [int]$Matches[2] * 100 + [int]("0" + $Matches[3])
$manifest = [IO.File]::ReadAllText("$app\AndroidManifest.xml")
$manifest = $manifest -replace 'android:versionCode="\d+"', "android:versionCode=`"$code`"" -replace 'android:versionName="[^"]*"', "android:versionName=`"$ver`""
[IO.File]::WriteAllText("$app\AndroidManifest.xml", $manifest, (New-Object Text.UTF8Encoding $false))
Write-Host "Building PS1 Bedroom v$ver"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

Write-Host '1/6 resources'
Run "$bt\aapt2.exe" @('compile', '--dir', "$app\res", '-o', "$out\res.zip")
Run "$bt\aapt2.exe" @('link', '-I', $aj, '--manifest', "$app\AndroidManifest.xml", '-A', "$app\assets",
  '--java', "$out\gen", '-o', "$out\base.apk", "$out\res.zip")

Write-Host '2/6 java'
$srcs = @(Get-ChildItem -Recurse "$app\src", "$out\gen" -Filter *.java | ForEach-Object FullName)
Run "$java\javac.exe" (@('-source', '8', '-target', '8', '-Xlint:-options', '-nowarn', '-encoding', 'UTF-8',
  '-bootclasspath', $aj, '-d', "$out\classes") + $srcs)

Write-Host '3/6 dex'
$classes = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName)
Run "$java\java.exe" (@('-cp', "$bt\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--min-api', '24',
  '--lib', $aj, '--output', "$out\dex") + $classes)

Write-Host '4/6 package'
Push-Location "$out\dex"
Run "$bt\aapt.exe" @('add', "$out\base.apk", 'classes.dex') | Out-Null
Pop-Location

Write-Host '5/6 align'
Run "$bt\zipalign.exe" @('-p', '-f', '4', "$out\base.apk", "$out\aligned.apk")

Write-Host '6/6 sign'
# Keep the signing folder: Android only installs updates signed with the same key.
$ks = "$here\signing\ps1bedroom.keystore"
$pw = ((Get-Content "$here\signing\keystore.properties") -replace '^storePassword=', '').Trim()
New-Item -ItemType Directory -Force "$root\releases" | Out-Null
$apk = "$root\releases\PS1-Bedroom-v$ver.apk"
Run "$java\java.exe" @('-jar', "$bt\lib\apksigner.jar", 'sign', '--v4-signing-enabled', 'false', '--ks', $ks, '--ks-key-alias', 'ps1bedroom',
  '--ks-pass', "pass:$pw", '--out', $apk, "$out\aligned.apk")
Run "$java\java.exe" @('-jar', "$bt\lib\apksigner.jar", 'verify', '--min-sdk-version', '24', $apk)
Remove-Item -Recurse -Force $out

Write-Host ''
Write-Host "Done: releases\PS1-Bedroom-v$ver.apk ($([math]::Round((Get-Item $apk).Length / 1KB)) KB)"
