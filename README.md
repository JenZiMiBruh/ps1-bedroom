# PS1 Bedroom

**A cozy, late-night PlayStation-era bedroom you can leave running in the background.**

PS1 Bedroom is a little ambience app styled like a 1999 PlayStation game: wobbly low-poly 3D, dithered colour and volumetric light rays through the blinds. It follows the real clock, so the sun and moon move across the sky, the city outside wakes up and goes to sleep, and the weather can match your own city's. Every corner of the room has its own sound, and nearly everything can be tapped: the TV, the PlayStation, the games on the shelves, the lamp, the blinds, the boombox and the door.

## ⬇️ Download

**[Get the latest Android APK from Releases](../../releases/latest)**

1. Download `PS1-Bedroom-v1.1.apk` on your Android phone.
2. Tap it and allow installs from your browser or file manager when asked.
3. If Play Protect warns you (the app isn't from the Play Store), choose **Install anyway**.

Prefer the browser? Download `src/ps1_bedroom.html` and open it in any modern desktop browser (it needs internet the first time to load its 3D and audio libraries).

## ✨ Features

- **Real day and night.** The sun and moon follow real paths across the sky, with true sunrise and sunset times and tonight's actual moon phase. The room light shifts from golden sunrise to daylight to cool moonlight.
- **15 kinds of weather.** Pick one yourself, let it drift naturally, or sync live weather for your city.
- **Five skies:** Natural, Vaporwave, Northern Lights, Meteor Shower and Blood Moon.
- **A living city outside:** lit windows that follow the hour, neon billboards, traffic and an elevated train.
- **A PlayStation full of games,** including the whole Grand Theft Auto collection with 3D gameplay on the CRT.
- **TV channels, a PC, a boombox radio,** a ceiling fan, string lights, a lava lamp and more, each with its own sound.
- **Walk around, sit in the chair, or lie in bed.**
- **Sleep timer, photo mode, battery saver,** and colour "Looks" (Warm VHS, Neon Dream, Noir...).
- **Android extras:** keeps playing with the screen off, lock-screen media controls, and home / lock-screen widgets.

## 🛠️ Building it yourself (Windows)

The whole app is one file: `src/ps1_bedroom.html`. The `android/` folder is a tiny wrapper that turns it into an APK.

1. Edit `src/ps1_bedroom.html` (bump `APP_VERSION` and add a `CHANGELOG` entry).
2. Run `Build APK.bat`. It expects Java 17 and the Android build tools in `%LOCALAPPDATA%\PS1BedroomBuildTools`.
3. You'll need your own signing key in `android/signing/` (not included in this repo).

## 📜 Changelog

See **[CHANGELOG.md](CHANGELOG.md)** for everything that changed in each version. The changelog is also inside the app under **Menu → About → Changelog**.

## ⚠️ Notes

This is an unofficial fan project, not affiliated with Sony, Rockstar Games or any other company. Game boxes and covers in the room are hand-drawn tributes. Live weather comes from [Open-Meteo](https://open-meteo.com/); only the city name you type is sent.
