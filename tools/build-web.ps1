# Builds the playable browser version of PS1 Bedroom:
#   releases\PS1-Bedroom-web-vX.zip  (index.html + bundled libraries, for itch.io "HTML" uploads)
#   docs\play\index.html              (GitHub Pages copy; loads the libraries from their CDNs)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$html = [IO.File]::ReadAllText("$root\src\ps1_bedroom.html")
if ($html -notmatch "const APP_VERSION = '([0-9.]+)'") { throw 'APP_VERSION not found' }
$ver = $Matches[1]

# GitHub Pages: the page as-is
New-Item -ItemType Directory -Force "$root\docs\play" | Out-Null
[IO.File]::WriteAllText("$root\docs\play\index.html", $html, (New-Object Text.UTF8Encoding $false))

# itch.io: everything offline in one folder, zipped
$out = "$env:TEMP\ps1bedroom-web"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null
$map = @{
  'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js' = 'three.min.js'
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js' = 'OrbitControls.js'
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/utils/BufferGeometryUtils.js' = 'BufferGeometryUtils.js'
  'https://cdnjs.cloudflare.com/ajax/libs/tone/14.8.49/Tone.js' = 'Tone.js'
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/loaders/GLTFLoader.js' = 'GLTFLoader.js'
}
foreach ($k in $map.Keys) {
  $html = $html.Replace($k, $map[$k])
  Copy-Item "$root\android\app\assets\$($map[$k])" "$out\$($map[$k])"
}
[IO.File]::WriteAllText("$out\index.html", $html, (New-Object Text.UTF8Encoding $false))
New-Item -ItemType Directory -Force "$root\releases" | Out-Null
$zip = "$root\releases\PS1-Bedroom-web-v$ver.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$out\*" -DestinationPath $zip
Remove-Item -Recurse -Force $out
Write-Host "Done: $zip and docs\play\index.html"
