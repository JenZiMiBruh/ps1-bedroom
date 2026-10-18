# PS1 Bedroom — Changelog

The in-app version of this list lives in `src/ps1_bedroom.html` (search for `const CHANGELOG`). Update both for each release, and bump `APP_VERSION`.

## v1.4 — in progress (on the `beta` branch)
**Added**
- **Wall calendar** shows the real month, crosses off the days gone by, circles today, and has its own pixel-art picture each month
- **Tap the top bar:** time, temperature, season and weather each step to the next option, plus a new **location** button for the place outside; hold any of them for the World menu
- **Achievements** unlock with a trophy card, pixel confetti and a little fanfare, with a different colour, move and tune for each one
- **PS1 look on YouTube** (Menu → Display, on by default): the video wobbles with the TV and gets the room's chunky pixels

**Changed**
- **New default sound mix:** the bedroom TV is clearly louder than the muffled one down the hall, the fan and AC sit under the rain, and music comes first. Layer volumes reset to the new mix once (master volume is kept)
- Rain is just as full but with far lower peaks, so it no longer slams the limiter (another source of crackle)
- **New sky:** one seamless dome around the whole world (no more visible seams or corners), with the sun, moon, stars and clouds drifting all the way round
- The alarm clock is now a big LED clock radio on the nightstand, readable from the default view (tap it for the timers and alarm)
- **Performance:** drops to 20 fps after 2 minutes untouched, the window redraws less often, and lighter audio processing on phones (less heat, fewer crackles)

**Fixed**
- Ceiling fan blades were missing their brackets
- The game shelf's brackets and lip covered the game cases
- The handheld console on the nightstand clipped into the clock
- The achievement card peeked out under the notch on the title screen, and achievements could unlock before the room had even loaded
- Posters, the calendar and rugs flickered and clipped into the wall from far away
- Textures far away shimmered and flickered (distant surfaces now use smaller mip levels; still crunchy up close)
- YouTube on the TV: jagged black bits around the picture and the video overlapping the TV's frame
- The TV border, the album cover on the dresser, the cassette window and the boombox display flickered: they were flat sheets stacked a millimetre apart, so they now get real depth or are part of the object itself
- The summer box fan was standing inside the front wall

## v1.3 — Goodnight, Jasper
**Added**
- **Sleep mode:** one tap: lights down, fan on, you in bed with Jasper beside you, everything fades out in 45 minutes
- **Sunrise alarm:** the room glows warmer for 20 minutes before your alarm, then your music (or birdsong) plays
- **Share cards:** a postcard of your room with place, time and weather (Photo mode → CARD)
- **18 achievements** (Menu → About)
- **Daily surprises:** something new in the room each day
- **Jasper** has a name, a collar and tag, and a new chunky PS1 model with clean sit/lie poses
- **Jasper's coat colours:** 6 presets or your own
- An UNDER CONSTRUCTION sign on the door

**Changed**
- The living room TV is properly muffled (down the hall, behind a door) and much quieter
- Android: after the sleep timer, the screen turns off on its normal timeout

**Fixed**
- TV hard to turn off: big red power button; flipping past the last channel turns it off (1.2.1)
- Photo mode's HIDE no longer hides the YouTube video; the old TV picture no longer shows around it
- Office chair post floating below the seat
- Backpack sinking into the floor
- Messages not fully sliding off screen
- Audio crackle on loud moments

**Removed**
- The hallway, for now (being rebuilt)

## v1.2.1 — TV power
**Fixed**
- The TV was hard to turn off: a bigger red power button with a standby light (easier to tap), and flipping past the last channel switches it off

## v1.2 — Stuff from 2001
**Added**
- A German Shepherd (blue eyes, his left one half brown) who wanders, sunbathes, watches TV, sits by you at the desk and sleeps on the bed at night; tap him for a tail wag
- A working PC: 2001 desktop with instant messenger, WinBeats player, Minesweeper and dial-up
- VCR tapes: Taped Off TV, Kung Fu Midnight, Ocean Dreams and the alien movie
- Phone calls and an answering machine
- MY MUSIC: your own songs on the boombox / WinBeats (stored on your device only)
- Traffic that follows the clock and weather: rush hours, taxis, buses, police, wet-road reflections, snowy roofs
- Rotating billboards picked for time, weather and season, and an LED ticker with time, temperature and conditions
- Rain on the window glass
- Glow-in-the-dark ceiling constellations
- Space heater for winter nights
- Poster picker, and wall / carpet / bedsheet colours
- Seasonal decorations by date (Halloween, holidays, Valentine's, summer)
- Focus timer on the bedside clock
- New GTA radio-style soundtracks (80s pop, electro, modern pop, trap, pentatonic)
- Tappable 2000s gadgets: CD player, handheld console, brick phone, virtual pet
- Props from Kenney's Furniture Kit (CC0): desk speakers, plants, coat rack, box of old stuff
- More detailed GTA cover tributes
- Outside the window: 15 places, each with its own backdrop, props, sounds, climate and temperature: Downtown, Skyscraper (40th floor, rooftop billboards), Neon City, Neighborhood, Suburbs, Backyard, Trailer Park, Arizona, Desert Canyon, Tropical Island, Beach Boardwalk, Lakeside, Forest, Mountains, Countryside Farm
- Boombox music player: play/pause, next/previous, shuffle, repeat, seek, volume, albums with covers, all songs, FM stations
- Album folders: reads album, artist, track order and cover art (embedded or cover.jpg); the cover shows on a CD case on the dresser, the boombox cassette window and optionally the wall poster
- YouTube on the bedroom TV (paste a link, or Share from the YouTube app on Android), on the CRT or full screen
- Frame rate setting (Auto / 30 / 60 / 120 fps) and Eco idle
- GAMES BACK IN ORDER button for the shelves
- Android: song, artist and cover on the lock screen, with previous / next
- Ambient occlusion (soft contact shadows), with a slider in PS1 effects
- A sky box around the outside: no more black void or visible end of the world from any angle

**Changed**
- Full detail pass on the outside places: PS1-style textures, real 3D houses, brownstones, barn and silo, mountains, overwater bungalows, sprite trees and grass, a rooftop city below the skyscraper
- Night windows outside vary (warm lamps, TV blue, dark rooms)
- The PC desktop matches the one on the monitor (wallpaper, icon grid, buddy list, music player)
- Per-pixel lighting (no more blotchy triangles), sharper shadows, gentler light rays, sparkling dust
- Battery: frame cap on 120 Hz screens, off-screen screens and sky stop redrawing, no drawing in the background, silent sound layers switch off
- Long sessions stay smooth: automatic quality steps down when the device is hot or busy and back up after
- Android: notification and widgets update about once a minute instead of every second
- Android: the app now runs from a secure local address (needed for YouTube), so settings and My Music start fresh once after updating

**Fixed**
- Audio clipping / crackle on loud moments and with your own music
- Frame rate sinking after hours of running
- Choosing a seasonal decoration by hand left the Halloween pumpkin on the sill

**Removed**
- The laundry pile in front of the bed

## v1.1.4 — Temperature
**Added**
- The top bar always shows the temperature (live for your city, or an estimate for the season, hour and weather)
- Choose Fahrenheit or Celsius in Menu → Display; the notification and widgets follow it too

## v1.1.3 — Dust in the light
**Fixed**
- The floating dust stayed in one spot; it now drifts inside the light beam wherever the sun or moon shines in from, and fades when the light does

## v1.1.2 — Little touches
**Changed**
- The alarm clock by the bed shows the room's real time (with the PM dot)
- Took down the EXCELLENCE poster above the desk

## v1.1.1 — Fixes
**Fixed**
- Light rays could leak through the wall beside and above the window when the sun was high or off to the side; rays now only shine through the window opening
- Removed the clicking crackle that played outside in fall (the soft leaf rustle stays)

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
