PS1 BEDROOM — DEV FOLDER

src\ps1_bedroom.html     The game. Edit this. Double-click it to play in a browser.
Build APK.bat            Double-click to make the Android app from the HTML.
releases\                Finished APKs land here (PS1-Bedroom-v1.0.apk etc). Copy one to your phone and tap it.
CHANGELOG.md             What changed in each version.
android\                 The Android wrapper. You normally never touch this.
  android\signing\       The app's signing key. DO NOT DELETE: without it, new versions can't install over the old one.

Making a new version:
  1. Edit src\ps1_bedroom.html.
  2. Change  const APP_VERSION = '1.0';  to the new number (e.g. '1.1') and add an entry at the top of CHANGELOG in the same file (and CHANGELOG.md).
  3. Double-click Build APK.bat.

Build tools (Java + Android SDK bits, ~1 GB) live in %LOCALAPPDATA%\PS1BedroomBuildTools so OneDrive doesn't sync them.

Android widgets (after installing): long-press your home screen > Widgets > PS1 Bedroom.
  "My Window" = live pixel-art view out of the window with the clock and weather.
  "Controls"  = play / pause and a 30-minute sleep timer.
On Samsung, lock-screen widgets are under Settings > Lock screen > Widgets (One UI decides which apps it allows there).
The playing notification also gives lock-screen media controls.
