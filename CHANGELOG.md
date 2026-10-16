# PS1 Bedroom — Changelog

The in-app version of this list lives in `src/ps1_bedroom.html` (search for `const CHANGELOG`). Update both for each release, and bump `APP_VERSION`.

## v1.1 — The living room update
**Added**
- Real-time day and night: the sun and moon follow your clock along real paths, with real sunrise/sunset times for the season (and your city); the room light comes from whichever is up
- Tonight's real moon phase; Cinematic or Real-astronomy moon path
- Time modes: Live clock, Pick a time (11 presets that follow sunrise and sunset + exact-time slider), Time-lapse (1 day in 5/20/60 min)
- Seasons follow today's date automatically (or pick one)
- 15 weathers: Clear, Partly Cloudy, Overcast, Windy, Drizzle, Rain, Downpour, Sun Shower (rainbow), Thunderstorm, Heat Lightning, Snow Flurries, Snowfall, Blizzard, Light Mist, Heavy Fog
- Live weather for your city (Open-Meteo) and an Auto-change mode
- Sky styles: Natural, Vaporwave, Northern Lights, Meteor Shower, Blood Moon
- New sky: drifting tinted clouds, twinkling stars, Milky Way, shooting stars, planes, birds, forked lightning
- Bigger city: two-layer skyline with hour-aware windows, day/night facades, rooftop neon billboards, an elevated train with sound, streetlights and headlights after dark
- Blinds roll all the way up for uninterrupted light rays
- String lights (warm / rainbow) and windowsill plants
- The whole GTA collection on a wall shelf (GTA, London 1969, GTA 2, III, Vice City, San Andreas, LCS, VCS, IV, Chinatown Wars, V, VI) in their console cases with recreated covers
- GTA on the TV: top-down chases for the classics, real 3D drives for III–VI, GTA VI sunset beach cruise, loading screens, three new soundtracks
- PlayStation-style start screen with its own boot chime
- New interface: status bar, dock and tabbed menu (World, Sound, Room, Display, Sleep, About)
- Settings remembered between sessions
- Sleep timer (15–90 min) that fades everything out and can let the screen turn off
- Photo mode (Android saves to Pictures/PS1 Bedroom)
- Quality presets and Looks (Signature, Raw PS1, Warm VHS, Cool Night, Neon Dream, Noir)
- First-launch how-to tour
- Android: background playback with the screen off, lock-screen media controls, "My Window" and "Controls" widgets for home and lock screen, portrait and landscape

**Changed**
- Higher eye level in the chair
- Bright daytime, golden sunrise/sunset light
- Sharper TV with CRT scanlines, glow, channel-change static and channel number; real time on the PC clock and weather channel
- Square pixels on any screen shape; master volume slider

**Fixed**
- Repeating textures broken up with large-scale variation

## v1.0 — First full release
**Fixed**
- Crackling / popping audio in the Android app: the audio engine now asks the phone for larger, steadier audio buffers and schedules sounds further ahead

**Added**
- In-app changelog (CHANGELOG button)
- Version number shown in the player-mode panel
- One-click `Build APK.bat` for rebuilding the Android app

## v0.8 — Android app (beta)
**Added**
- Android APK: full-screen landscape app that runs offline (all libraries bundled)
- Touch controls: drag to look, pinch to zoom, walk joystick, tap to interact
- "PUT GAME BACK" button while holding a game
- Phone-friendly hints and messages
- Audio pauses and rendering stops in the background; screen stays on while open
- Pixel-art app icon

**Changed**
- Settings panel starts hidden on touch screens; compact layout for phones held sideways
- Two-finger touches no longer fight each other

## v0.7 — The big room update (beta)
**Fixed**
- Sun / moon rays through the window were barely visible
- Lag spikes: static furniture merged (~540 → ~235 draw calls), shadow updates ~3× faster
- Floor game case flickered / vanished when zoomed out
- Chair and bed camera views pointed in odd directions; desk seat faced away from the monitor

**Added**
- Fully built room; door moved opposite the foot of the bed, opening onto a hallway and living room
- Clickable light switch, nightstand lamp, dresser, boombox radio (two stations)
- New props: posters, calendar, trophy, photo, laundry, backpack, wastebasket
- Two new TV channels (Saturday-morning cartoons, late-night music videos)
- Grab games and put them in the PS1, with disc-swap animation, title screen, gameplay and music per game
- Chair rolls between TV, desk and window; drag to look while seated or in bed
- Debug option to let the free camera leave the room
- New rain, splashes, snow, leaves, fireflies, lightning; seasonal window and ground

**Changed**
- New chair and wall AC models; free camera stays inside the room; brighter hallway light

## v0.6 — Textures & real games (alpha)
- Added worn, imperfect textures; shelf games styled as real 2000–2002 PS1 cases
- Fixed over-bright PS1 top and a white square near the PS1 when zoomed in

## v0.5 — PlayStation overhaul (alpha)
- Rebuilt PS1 with working POWER / RESET / OPEN and opening lid; boot screen and game loop on the TV with sound
- PS1 cabled to the TV and wall; improved TV and stand models

## v0.4 — Sound overhaul (alpha)
- Full layered, looping sound design with a sound for every interaction
- Fixed total audio silence (rebuilt as a lightweight engine), overlapping boot jingles and timing errors
- Removed the odd object right of the door

## v0.3 — Polish pass (alpha)
- Fixed fan chain clipping the light, moon drawing in front of buildings, door panel and rug clipping, excessive wobble, many small bugs
- Added a second fan pull chain; removed the red object on the bed

## v0.2 — Bug fixes (alpha)
- Fixed five bugs from the first review; removed unused libraries

## v0.1 — Original (alpha)
- The original PS1-style bedroom scene
