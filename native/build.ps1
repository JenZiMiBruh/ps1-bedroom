# Builds the native (Kotlin) PS1 Bedroom APK. No Gradle:
# aapt2 -> kotlinc -> d8 -> zipalign -> apksigner, with the same signing key as the web-wrapper app.
$ErrorActionPreference = 'Stop'
$here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$root  = Split-Path -Parent $here
$tools = "$env:LOCALAPPDATA\PS1BedroomBuildTools"   # Java, Kotlin + Android build tools (kept out of OneDrive)
foreach ($p in 'jdk', 'kotlin', 'build-tools', 'platform') { if (-not (Test-Path "$tools\$p")) { throw "Build tools not found: $tools\$p" } }
$jdk     = (Get-ChildItem "$tools\jdk" -Directory)[0].FullName
$java    = "$jdk\bin"
$kotlinc = "$tools\kotlin\bin\kotlinc.bat"
$stdlib  = "$tools\kotlin\lib\kotlin-stdlib.jar"
$bt      = (Get-ChildItem "$tools\build-tools" -Directory)[0].FullName
$aj      = "$tools\platform\android.jar"
$app     = "$here\app"
$out     = "$env:TEMP\ps1bedroom-native-build"
$env:JAVA_HOME = $jdk

function Run($exe, [string[]]$argv) {
  & $exe @argv
  if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($exe)) failed with exit code $LASTEXITCODE" }
}

$manifest = [IO.File]::ReadAllText("$app\AndroidManifest.xml")
if ($manifest -notmatch 'android:versionName="([^"]+)"') { throw 'versionName not found' }
$ver = $Matches[1]
Write-Host "Building PS1 Bedroom native v$ver"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

Write-Host '1/6 resources'
Run "$bt\aapt2.exe" @('compile', '--dir', "$app\res", '-o', "$out\res.zip")
$link = @('link', '-I', $aj, '--manifest', "$app\AndroidManifest.xml", '--java', "$out\gen", '-o', "$out\base.apk", "$out\res.zip")
if (Test-Path "$app\assets") { $link += @('-A', "$app\assets") }
Run "$bt\aapt2.exe" $link

Write-Host '2/6 kotlin'
$srcs = @(Get-ChildItem -Recurse "$app\src" -Include *.kt, *.java | ForEach-Object FullName) + @(Get-ChildItem -Recurse "$out\gen" -Filter *.java | ForEach-Object FullName)
& $kotlinc @('-jvm-target', '1.8', '-language-version', '1.9', '-api-version', '1.9', '-no-reflect', '-nowarn', '-cp', $aj, '-d', "$out\classes") @srcs 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -notmatch '^warning:' }
if ($LASTEXITCODE -ne 0) { throw "kotlinc failed with exit code $LASTEXITCODE" }
# R.java comes out of aapt2 as Java: compile it too (kotlinc only reads it)
$gen = @(Get-ChildItem -Recurse "$out\gen" -Filter *.java | ForEach-Object FullName)
if ($gen.Count) { Run "$java\javac.exe" (@('-source', '8', '-target', '8', '-Xlint:-options', '-nowarn', '-encoding', 'UTF-8', '-bootclasspath', $aj, '-cp', "$out\classes", '-d', "$out\classes") + $gen) }

Write-Host '3/6 dex'
$classes = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName)
Run "$java\java.exe" (@('-cp', "$bt\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--min-api', '24',
  '--lib', $aj, '--output', "$out\dex") + $classes + @($stdlib))

Write-Host '4/6 package'
Push-Location "$out\dex"
Run "$bt\aapt.exe" @('add', "$out\base.apk", 'classes.dex') | Out-Null
Pop-Location

Write-Host '5/6 align'
Run "$bt\zipalign.exe" @('-p', '-f', '4', "$out\base.apk", "$out\aligned.apk")

Write-Host '6/6 sign'
$sign = "$root\android\signing"
$ks = "$sign\ps1bedroom.keystore"
$pw = ((Get-Content "$sign\keystore.properties") -replace '^storePassword=', '').Trim()
New-Item -ItemType Directory -Force "$root\releases" | Out-Null
$apk = "$root\releases\PS1-Bedroom-native-v$ver.apk"
Run "$java\java.exe" @('-jar', "$bt\lib\apksigner.jar", 'sign', '--v4-signing-enabled', 'false', '--ks', $ks, '--ks-key-alias', 'ps1bedroom',
  '--ks-pass', "pass:$pw", '--out', $apk, "$out\aligned.apk")
Run "$java\java.exe" @('-jar', "$bt\lib\apksigner.jar", 'verify', '--min-sdk-version', '24', $apk)
Remove-Item -Recurse -Force $out

Write-Host ''
Write-Host "Done: releases\PS1-Bedroom-native-v$ver.apk ($([math]::Round((Get-Item $apk).Length / 1KB)) KB)"
