package com.ps1bedroom.room

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The native build's changelog, from 0.1 to the final release. Newest first. Add an entry with every version bump
 * (the version tag in the corner opens it; it moves into the Settings menu once the menus are ported).
 */
object Changelog {
    class Entry(val version: String, val title: String, val notes: List<String>)

    val entries = listOf(
        Entry("0.59", "Crash fixes and a crash reporter, GTA maps like the real ones, a living Tamagotchu", listOf(
            "Crash fixes: the Window widget's snapshots no longer pile up 10 MB native buffers until Android kills the app; a failed snapshot can't leave the blinds hidden or the camera moved any more; switching GTA games frees the old city's GPU memory; the TV's GTA picture isn't drawn in the background while you play; the app gets a bigger memory allowance",
            "If anything still goes wrong, the app now records it. The next time you open it you'll see what happened, with a Copy report button, so it can be fixed. A problem inside GTA closes the game and drops you back in the bedroom instead of freezing",
            "GTA maps now follow the real ones. III and LCS: Shoreside Vale, Staunton Island and Portland, joined by the Shoreside Lift Bridge and the Callahan Bridge. VC and VCS: the mainland (Downtown, Little Haiti, Little Havana), Starfish Island, and the beach island (Vice Point, Washington Beach, Ocean Beach) with its sand. SA: Los Santos with Santa Maria and Verona Beach, Downtown, Idlewood, Ganton, Jefferson, El Corona, the Vinewood hills and sign, and the airport. IV: Alderney, Algonquin with Middle Park, Bohan and Broker / Dukes, with their bridges and tunnel. V: Vinewood Hills (Franklin's house), Rockford Hills, Del Perro, Vespucci Beach, Downtown, Strawberry and the airport. VI: Vice City, Vice Beach and the Leonida Keys on the Overseas Highway",
            "The sea is real now: you can't drive or walk into the water, the district name shows as you cross into it, and the pause map shows the islands, beaches, bridges and names",
            "Tamagotchu is a real virtual pet: it hatches, grows from baby to child to teen to one of three adults depending on your care, and gets hungry and bored on the real clock (even while the app is closed). It sleeps at night (turn the light off), poops, gets sick, calls you (sometimes as a test, so scold it), and can leave if neglected",
            "Tap it on the dresser to pick up the device: the classic eight icons (food, light, play, medicine, toilet, status, scold and the call light) with A / B / C buttons, a left-or-right guessing game, and status pages for age, weight, hunger, happiness and discipline. Its little screen in the room shows the pet live"
        )),
        Entry("0.58", "Play the GTA games for real, sharper GTA covers", listOf(
            "Every 3D GTA on the TV is now playable: when one is in game, tap the TV to pick up the controller. Each game has its own small open city in its own style, with its own districts, cars, main character and places to go inside",
            "Drive any car (GAS / BRAKE, steer with the left side), get out, and take someone else's car (that raises your wanted level). Walk around on foot (push the stick all the way to run) and drag the right side to look around. Traffic stays in its lane, turns at junctions and stops for cars, people and you",
            "Go inside: walk into a glowing marker. Your safehouse lets you sleep to save and heal (III: Portland Safehouse, VC: Ocean View Hotel, SA: Grove Street, LCS: Toni's Safehouse, VCS: Vic's Apartment, IV: Roman's Apartment, V: Franklin's House, VI: Lucia's Apartment). Each game also has its own shops, like Ammu-Nation for armour, Cluckin' Bell, Burger Shot, Marco's Bistro, Ma Cipriani's, Cherry Popper and the 24/7 for food, and Gash, Binco, Perseus and Ponsonbys for new outfits",
            "Cash and health pickups, a wanted level that cools down over time, WASTED (you wake up at your safehouse $100 lighter), district and car names as you move around, and a game clock",
            "Each game's own HUD, laid out like the originals: the radar (round for III to IV with health and armour around it in IV, rectangular in V, rounded in VI), the weapon icon, clock, money, health and armour, and wanted stars, in that game's colours and lettering",
            "Pause menu (≡ or the back button) in each game's style, with a full map with blips for your safehouse and the shops, your stats, the controls, and Quit Game",
            "GTA covers look closer to the real box art: white gutters between the panels, comic-book halftone shading and lighting on every panel, and Rockstar's badge in the corner"
        )),
        Entry("0.57", "GTA traffic that never collides, a look for every GTA, new boot sounds, steadier frame rate", listOf(
            "GTA on the TV (III through VI): traffic now drives in real lanes, two each way. Cars keep their distance and slow down behind slower ones, and your car changes lanes only into a clear gap, overtakes through the oncoming lane only when it's empty, and brakes when it's boxed in. No more cars driving through each other",
            "Every GTA looks different now: Liberty City's elevated railway and rainy blue night (III) or grey fog (LCS), neon pink Vice City, low desert towns with hills and power lines in San Andreas, tall sepia-grey Algonquin towers in IV, Los Santos hills with the VINEWOOD sign in V, and the Leonida beach in VI. Each game also gets its own colour grade, traffic colours, driving speed and HUD",
            "PlayStation boot sounds redone to sound much more like the real ones. PS1: a dark swell that opens into a cascade of crystal chimes, then the deep ringing \"dooong\" on the PlayStation logo. PS2: a cold drone and a high shimmer. PS3: a warm swell and a soft chime. PS4: a bright chime run. PS5: a rising whoosh into a deep boom",
            "Frame rate: on 90 Hz screens the 60 fps cap was only giving 45 fps; it now holds 60. When the phone can't keep up, the resolution steps down a little on its own instead of the frame rate dropping straight to 30, and it steps back up once things are smooth",
            "TV weather, Simple view: adds humidity, chance of rain, and the next sunrise or sunset",
            "TV weather, Advanced view: fixed text running into other text or off the panel edges. The section titles are shorter, the city moved down to the lower third, the temperature and \"feels like\" no longer overlap, and every line now fits its box",
            "Leaves that blow in now land on whatever they hit: the bed, the pillows, the desk or the floor. No more floating above the pillows or sinking through the bed"
        )),
        Entry("0.56", "Quality preset crash fix, resting leaves, sharp Paper Toss, desk phone mute", listOf(
            "Fixed: picking a quality or resolution preset could break the app. Each change leaked the shadow maps, and the 4K / 1080p presets rendered far above the phone's own screen. Resolution now tops out at your screen's real resolution, nothing leaks, and if the GPU can't handle a size it steps down instead of failing. The 4K preset is now called Max (full screen res)",
            "Leaves that blow in through the window drift down, land flat on the floor or desk and stay still, then fade away after about 45 seconds. Turn on World › Weather / Sky › \"Leaves blown inside stay on the floor\" to keep them for good",
            "Paper Toss is drawn at 3x the resolution with smooth text, so every score, label and button is easy to read. The scenery keeps its chunky look",
            "Desk phone: Sound › Desk phone › Rings, Silent (calls go straight to the answering machine) or No calls",
            "Jasper now actually turns his head all the way toward thunder, the TV, the boombox and you. Before, it only got about a quarter of the way there",
            "Lava lamp wax rebuilds its surface much faster (normals come from the grid instead of re-sampling the wax 24 times per triangle), so it uses less CPU"
        )),
        Entry("0.55", "Paper Toss Deluxe, new weather card, seamless walls, leaves stay outside", listOf(
            "Paper Toss is a full game now: runs with strikes, stages that add wind, a sliding basket, a box in the way and gusts, combo multipliers and ON FIRE streaks, golden wads worth triple, coins, an upgrade shop (bigger basket, aim guide, steady hand, extra strike, lucky rim, golden touch, coin magnet) and lifetime stats",
            "Paper Toss looks better too: shaded basket and box, a window that follows the time of day, ball trails, confetti, screen shake and score pop-ups",
            "TV weather, Simple view: redone as one clean card. The background matches the sky outside, with a big icon, a huge temperature, the condition in plain words and one strip for high / low and wind",
            "Walls: the worn-paint texture is redone so it tiles cleanly, and every wall piece now uses the same scale, so there are no more seams or mismatched patches where the walls meet",
            "Leaves blown toward the house now stop at the outside of the wall instead of drifting through it into the room. Only the open window lets them in, and once inside they stay inside the room"
        )),
        Entry("0.54", "Real lava lamp wax, sound presets, Jasper reacts to the room", listOf(
            "Lava lamp: the wax is now one gooey surface instead of six balls. Blobs swell off the warm pool at the bottom, stretch tall as they pull free, round off and cool at the top, then sink back and melt into the pool, merging and pinching apart when they pass. When it's off the wax slumps into the pool",
            "Sound presets in Sound › Mixer: Default, Study, Rainy night, Sleep, Lively and Music first, one tap each. Save your own mix (up to 6) and remove them any time",
            "Jasper reacts: thunder makes him flinch, stare at the window and often hide on the bed; he looks over and trots to the TV when it or YouTube comes on, perks up at the boombox when music starts, watches a PlayStation boot, heads to bed when you turn the lights out at night, and glances at you when you walk or sit near him"
        )),        Entry("0.53.11", "Tune-up: YouTube restart fix, lighter widget alarms, less background work", listOf(
            "YouTube: stopping a video right after starting it could leave the TV holding a closed screen, so the next video never showed up. Fixed",
            "Window widget refreshes no longer wake the phone from sleep just to repaint; they update the next time the phone is awake (saves battery)",
            "The outdoor scenes (cars, swaying trees, chimney smoke, leaves on roofs) update about 6 times a second while the window is off screen instead of every frame",
            "Weather effects no longer build a new list every frame"
        )),        Entry("0.53.10", "4K Ultra HD Rendering & Resolution Scaling Up to 2160p", listOf(
            "4K Ultra HD resolution support: internal render target scaling now supports up to 4K (2160p / 3840×2160), delivering ultra-sharp geometry and immaculate fidelity",
            "Resolution presets & slider: expanded settings slider from 160p up to 2160p with dedicated 1-tap chips for 180p, 240p (PS1), 360p, 480p, 720p (HD), 1080p (FHD), 1440p (2K), and 2160p (4K UHD)",
            "Quality mode expansion: added 'Ultra (1080p)' and '4K UHD (2160p)' quality presets with enhanced volumetric ray steps and high-resolution shadow mapping",
            "Supersampling anti-aliasing (SSAA): intelligent post-processing downsampler automatically applies linear filtering when downscaling from 4K to physical 1080p/1440p displays for smooth, jagged-free edges, while retaining nearest-neighbor filtering for retro PS1 pixel modes",
            "Vertex wobble off toggle: vertex jitter slider can now be set to 0 ('Off - Crisp') for users who want pristine modern clarity at 4K without retro polygon wobbling",
            "GPU texture safety: queries GL_MAX_TEXTURE_SIZE at runtime to safely handle ultra-wide aspect ratios without exceeding hardware limits",
            "Debug tool resolution suite: added instant 1-tap resolution switchers and live resolution telemetry to the developer debug panel"
        )),
        Entry("0.53.9", "Physical Window Rain & Snow, Wind-Driven Precipitation & Leaves, Lightning Arc & Thunder EQ, Weather Channel & Calendar Overhaul", listOf(
            "Physical window glass rain & snow: overhauled window pane particles so raindrops physically strike, streak diagonally with live wind slant, and run down the pane; snowflakes stick to the glass, melt in the warmth, and trickle into dripping water rivulets",
            "Fixed lower pane fog glitch: eliminated duplicate depthWrite glass mesh that caused bottom window pane fog occlusion and gray clipping at glancing angles; upper pane is fixed and lower pane lifts with the sliding sash",
            "Wind-driven weather simulation: rain, snow flurries, and leaves now calculate lateral and depth drift directly from live wind direction and speed across all outdoor scenes",
            "Indoor leaf drift: when the window is raised open, occasional leaves fluttering near the open aperture are sucked inside by gusts, drift across the bedroom, and settle onto the desk or floor",
            "Lightning electric arc & thunder EQ: instant supersonic ionization whip (0.20s razor-sharp crackle) synced to the exact frame of the lightning flash, paired with configurable Thunder EQ modes (Full Spectrum 30Hz-16kHz, Low Bass Rumble sub-80Hz, and High Crackle shockwave)",
            "Weather Channel Simple View overhaul: ultra-bold 28px temperature with CRT drop shadow, animated 28x28 wind-slanted icon, prominent condition banner, high/low badges, wind vector compass & speed, and status deck clearly legible from default room view",
            "Weather Channel Advanced View (Local on the 8s): expanded 4-slide broadcast cycle featuring Doppler 4000 radar scope with drifting rain/snow cells, 5-day extended regional outlook, celestial moon & sun almanac, and regional holidays & climate normals",
            "Wall calendar active week highlight: amber highlight band frames the entire active week row across all 7 days in both 2001 nostalgia and live 2026 calendar modes",
            "Debug tool expansion: added 1-tap Thunder EQ controls (Full / Low / High), wind direction degree buttons, bedroom window sash toggle, and live weather refresh interval selectors"
        )),
        Entry("0.53.8", "Self-Sufficient Background Window Widget Refresh & Live Outdoor Engine", listOf(
            "Self-sufficient background Window Widget engine: home-screen Window Widget refreshes and updates dynamically even when the app is completely closed or terminated (no PiP mode required)",
            "Periodic background alarm scheduler: sets Android AlarmManager setAndAllowWhileIdle ticks matching the chosen widget refresh interval (1m - 60m), waking up to update without opening the activity",
            "Instant manual refresh & controls: tapping the widget refresh button (🔄) or cycling blinds, weather, and places triggers immediate background execution, live Open-Meteo weather fetch, and visual re-render without launching the room",
            "Dynamic 2D Canvas PS1 outdoor renderer: generates pixel-perfect outdoor scenes reflecting exact day/night/dawn/dusk sky gradients, sun/moon/stars, clouds, rain, snow, lightning, and 15 distinct environments (Downtown, Skyscraper, Neon City, Suburbs, Backyard, Trailer Park, Arizona, Beach, Lake, Forest, Mountains, Countryside)",
            "Window sash open indicator: widget window casing dynamically illustrates whether the bedroom window is raised open or closed shut"
        )),
        Entry("0.53.7", "Open Window Soundscape, Theme-Styled Toasts, 2001/2026 Wall Calendar, Dynamic Holiday Lights & Sleep Screen-Off", listOf(
            "Open window soundscape: lower window sash slides open and shut with smooth physics animation; opening the window floods the room with loud, crystal-clear 18 kHz outdoor audio (rain, wind, crickets, traffic pass-bys), decoupling outdoor sound from the blinds",
            "Theme-styled toasts: room toasts dynamically mirror the active UI color theme and accent color (PS1 pink, memory card cyan, game boy green, sunset orange, arcade red) on a sleek matching backdrop",
            "Suppressed boot sound toast: quieted audio initialization during the boot logo sequence to eliminate early toast popups before entering the bedroom",
            "2001 vs 2026 calendar toggle: switch between authentic retro October 2001 nostalgia (Wednesday 24th) and current live 2026 real-world calendar with exact days of the week, selectable via menu, debug tool, or direct room tap",
            "Holiday-themed string lights: hanging bedroom string lights dynamically transform their bulb color palettes and glow to celebrate every holiday (Halloween orange/purple, Christmas red/green/gold, Valentine pink/crimson, St. Patrick's emerald, Easter pastel, Summer 4th red/white/blue, Thanksgiving harvest amber, New Year's champagne gold)",
            "Expanded holiday decorations: added bespoke 3D decor for St. Patrick's (clover garland, desk pot of gold, windowsill leprechaun hat), Easter (woven wicker basket with painted pastel eggs), Thanksgiving (harvest pumpkin & squash cluster, autumn maple garland), New Year's (celebratory top hat with 2001/2026 band, noisemaker, star streamer), and Summer 4th of July bunting",
            "Sleep mode screen-off: when the sleep timer ends, the display gracefully sets brightness to zero, clears screen lock flags, and relinquishes the screen to Android OS system sleep / display lock"
        )),
        Entry("0.53.6", "Developer Debug Suite, High-Legibility TV Weather View, Trinitron Remote, & Swings Fix", listOf(
            "In-App Developer Debug Suite: comprehensive retro PS1 debug tool with 6 tabs for instantaneous 1-tap testing of weather, time warp (1x - 300x), day of week, all 15 outside locations, TV channels, Jasper behaviors, and real-time engine diagnostics",
            "Instant lightning trigger & leaf flurries: dedicated debug buttons to immediately strike lightning with audio + window flash and spawn leaf flurries drifting into the window pane",
            "High-legibility TV Weather Simple View: giant 26px bold temperature, animated 28px weather icon, bold condition banner, and lower status strips easily readable from across the bedroom in default view",
            "Retro TV Remote Control: authentic Sony RM-90 style floating remote with power, mute, channel steppers (up/down), direct channel presets, weather view toggle, TV screen zoom, and YouTube",
            "TV Screen Zoom Camera: seamlessly zoom straight in front of the CRT display to watch broadcasts up close, then tap to return to bedroom orbit view",
            "Backyard swing set alignment: re-engineered A-frame geometry with inverted V blue metal legs meeting precisely at top crossbar apex, reinforced side braces, and centered swings",
            "Car leaves clean-up: removed bugged parked car leaf attachments while preserving roof and chimney leaf clusters across all outside environments"
        )),
        Entry("0.53.5", "Location weather dynamics: freeway traffic rain spray & leaves landing on roofs & cars", listOf(
            "Freeway traffic tire spray: cars driving on the elevated highway kick up realistic tire spray rooster tails that scale with vehicle speed and rain intensity",
            "Taillight spray glow & snow powder: rain spray catches glowing red taillight reflections at night, and switches to a billowing snow powder wake in winter",
            "Leaves on houses and roofs: fallen leaf clusters settle on sloped shingle roofs, hips, valleys, gutters, and flat roofs across suburbs, neighborhood, backyard, and countryside",
            "Leaves on cars: parked cars along curbs and in driveways collect fallen leaves on hoods and windshield wiper cowls, trembling in the wind and sliding down in gusts",
            "Barn weather vane & countryside straw: red barn rooftop rooster weather vane spins and points directly into live wind direction; straw and leaves scatter on barn roof",
            "Bespoke weather in every location: storm surf surges and foam crash on beach; tropical tiki torch flames sputter in rain; lake ripples expand with raindrops; desert tumbleweeds bounce with live wind; wet neon asphalt reflections deepen"
        )),
        Entry("0.53.4", "Window leaf hit slider & complete 90s WeatherStar 4000 weather channel overhaul", listOf(
            "Leaves hitting window slider: new 0% to 100% collision chance slider under Weather & Sky Debris settings modulating how many airborne leaves strike the window glass and sill",
            "Overhauled TV Weather Channel: transformed Channel 12 into an authentic nostalgic 1990s WeatherStar 4000 'Local on the 8s' broadcast with rotating multi-page local forecast slides",
            "Live Doppler Radar 4000: authentic radar scope with 360° rotating sweep line, range rings, crosshairs, and live weather reflectivity cells drifting with real wind speed & direction",
            "4-Day Extended Local Outlook: day-of-week columns, custom pixel weather icons, short forecast summaries, High/Low temperatures, and precipitation probabilities",
            "Celestial Almanac & Moon phase: exact astronomical pixel moon rendering showing live crescent/quarter/gibbous illumination, phase name, and sunrise/sunset times",
            "Upcoming holiday countdown & seasonal themes: tracks days remaining until Halloween, Thanksgiving, Christmas, New Year's, Valentine's, 4th of July, etc., with dynamic seasonal channel branding",
            "Double-deck lower-third crawler: always-on L-bar ticker showing real-time clock, date, month, day of week, city, temperature, and scrolling marquee weather & holiday reports"
        )),
        Entry("0.53.3", "Unified physical window leaves (hit, roll, fly off), real-life leaf speeds & weather API refresh interval", listOf(
            "Unified 3D window leaf physics: all leaves on the window are the exact same 3D leaves as in the wind (zero fake 2D canvas stamps or static meshes)",
            "Watch leaves hit and roll: observe leaves fall from the sky, collide with the window glass pane or sill, roll and slide across the window, and get swept into the air by wind gusts to fly off",
            "Real-life leaf speeds: calibrated terminal fall velocity (0.42-0.72 m/s) with authentic aerodynamic pendulum rocking, flutter and gentle crosswind drift",
            "Weather API refresh interval: user-configurable auto-refresh interval for Open-Meteo live weather (2m, 5m, 10m, 15m, 30m, 1h) plus manual instant refresh"
        )),
        Entry("0.53.2", "Visceral lightning DSP, flying window leaves, Open-Meteo live wind & days of the week", listOf(
            "Visceral lightning audio: redesigned thunder with an explosive acoustic supersonic crack on strike onset, decaying into deep reverberant sub-bass rumble with instant arrival (<0.04s-0.38s)",
            "Dynamic window leaves: exterior window sill leaves flutter, tremble in the wind, and periodically take flight, blowing across the pane and resetting naturally; leaves on glass slide and peel off in gusts",
            "Open-Meteo live wind: queries real-time wind speed & direction from Open-Meteo API, driving tree swaying, window leaves, debris slant, waves, wind chimes, laundry, and wind audio",
            "Days of the week simulation: tracks Sunday-Saturday across live clock, cycle time-lapse, and presets; modulates highway commute rush hours vs weekend afternoon leisure traffic",
            "Weekend house lights: houses, trailers, and apartments stay lit ~1.8 hours later into the night on Friday and Saturday, with earlier lights-out on work/school nights",
            "Bedroom calendar & UI: wall calendar highlights the active day-of-week column, and Time of Day menu displays day names with full weekday/weekend preset picking"
        )),
        Entry("0.53.1", "Launch crash fix, TV-native YouTube without overlays & performance optimizations", listOf(
            "100% reliable launch: eliminated CalledFromWrongThreadException by eagerly constructing all HUD sheets and views on the main UI thread",
            "YouTube plays directly on CRT TV: runs on the TV's off-screen display and feeds the 3D CRT texture just like channels do (zero window overlay)",
            "Edge-to-edge flush playback: eliminated all inner borders and shadows so video fills the CRT screen completely",
            "Responsive lightning: any lightning frequency adjustment immediately flashes and triggers thunder in real time",
            "Binder IPC protection: Window widget preview bitmaps are scaled to 360x240, safeguarding against TransactionTooLargeException",
            "Render loop hardening: guarded cold-boot surface metrics and wrapped onDrawFrame with fail-safe error isolation"
        )),
        Entry("0.53", "2D A* pathfinding for Jasper around bedroom obstacles (bed, heater, desk, TV & bowls)", listOf(
            "Full 2D A* navigation grid: Jasper computes optimal collision-free routes across the bedroom floor with obstacle inflation clearance",
            "No more getting stuck: routes safely around the space heater and foot of the bed without vibrating or oscillating against collision boundaries",
            "Smooth canine paths: line-of-sight raycast string pulling produces natural straight corridors and curves with zero clipping",
            "Precise bowl alignment: Jasper stands directly in front of the food and water bowls facing forward with mouth reaching naturally into the dish",
            "Dynamic anti-stuck watchdog: automatically replans path if dynamic obstacles (like the rolling chair) block forward progress"
        )),
        Entry("0.52", "100% reliable launch synchronization & CRT YouTube feed without overlays", listOf(
            "Eliminated launch timing race: guarded onDrawFrame with atomic completion check so rendering never runs before scene construction finishes",
            "YouTube plays directly ON the TV screen: runs on the TV's private display and feeds straight into the CRT texture (zero overlay on the bedroom)",
            "Asynchronous widget resume dispatch: decoupled widget updates from onResume so startup is instantaneous and smooth every single time"
        )),
        Entry("0.51", "Fix YouTube on bedroom TV & edge-to-edge flush CRT playback", listOf(
            "Fixed YouTube playback: removed VirtualDisplay/Presentation pipeline incompatibility that caused black/frozen player screens",
            "Restored direct hardware-accelerated playback with live origin verification and full audio/video stream decoding",
            "Zero border flush CRT fit: video fits edge-to-edge across the CRT tube face with PS1 scanlines and authentic room lighting",
            "Full controls & tap to pause: tap the TV directly to toggle play/pause, or use the floating movable bar and full-screen switch"
        )),
        Entry("0.50", "Launch crash fix & bulletproof widget updates", listOf(
            "Fixed launch crash: eliminated early lightning triggering during startup settings restoration",
            "Bulletproof widget updates: safely isolated all RemoteViews rendering, bitmap allocations, and widget broadcast dispatches with fail-safes so a widget refresh can never interrupt app launch or resume",
            "Window widget preview stability: robust canvas drawing and bitmap scaling ensures smooth updates without Binder memory overhead"
        )),
        Entry("0.49", "Edge-to-edge YouTube on TV, responsive lightning & live blinds on Window widget", listOf(
            "YouTube on TV border removed: the video now renders flush edge-to-edge with no margins, inset borders, or rounded letterboxing inside the CRT bezel",
            "Lightning frequency is instantly responsive: changing frequency immediately clears cooldown and triggers an instant flash & thunderclap. Constant mode strikes rapidly every ~0.7-1.6s",
            "Window widget preview updates immediately on tap: cycling blinds, stepping weather, changing place, or toggling room controls refreshes the widget preview instantly whether the app is open, in the background, or closed",
            "Closed blinds always show on the Window widget: when blinds are closed in the bedroom, the widget renders fully closed Venetian blinds with realistic beveled slats, overlapping seams, and pull cords"
        )),
        Entry("0.48", "YouTube really on the TV, Paper Toss, fixed Ferris wheel, winter chimney smoke & better widgets", listOf(
            "YouTube now plays ON the bedroom TV itself: the player runs on the TV's own display and becomes the CRT's picture, so it curves, wobbles and lights the room like any channel (no more floating overlay). Full screen still works",
            "NEW mini-game: PAPER TOSS. Tap the wastebasket by the desk and flick crumpled paper in. A different wind every throw, the basket backs away as your streak grows, swishes score a bonus, the ceiling fan adds drift, and your best is saved",
            "Beach Boardwalk: the Ferris wheel is fixed. It sits properly on the pier (no more sinking into the sea), with an A-frame, two rims, upright gondolas, a lit hub and neon at night",
            "Winter chimney smoke: real 3D smoke curls up from every chimney in winter and snow (suburbs, backyard neighbours, brownstones, trailers, the lakeside cabin, the mountain lodge and the farmhouse) and drifts with the wind",
            "Window widget: actually refreshes (a fresh snapshot whenever you change something from a widget and when you leave the app), shows when it was taken, and has room controls: light, TV, sound, weather, blinds and place",
            "Widgets redesigned with retro buttons: ceiling fan on Quick Controls, season button on Clock & Weather, alarm time on Sleep & Alarm, Pet on Jasper, and a 📻 next-station button on Now Playing",
            "The room's sound always shows a cover in the phone's media player card"
        )),
        Entry("0.47", "PS3/PS5 disc insertion fix, Jasper eating & drinking animations, lightning frequency & natural thunder", listOf(
            "Fixed PS3, PS4, and PS5 staying on the boot/menu screen when inserting a game: slot-loading consoles now properly close the menu state and transition cleanly through boot into the game's title screen and gameplay",
            "Fixed Jasper's food and water bowls: tapping the red or blue bowl (or pressing Feed / Water) now visibly fills the bowl with chunky kibble or glossy water above the rim, with pour/kibble sounds",
            "Fixed Jasper's eating and drinking animation: he now walks all the way to the specific bowl before starting his eating/drinking timer, bends his front legs and lowers his head and moving jaw/tongue right into the bowl while the food/water visibly depletes",
            "Added Lightning Frequency setting in Menu → World → Weather (Off, Rare, Normal, Frequent, Constant, defaulting to Normal)",
            "Redesigned thunder & lightning audio: replaced the harsh high-frequency white-noise snap and synth sine sweep with a warm, natural multi-echo acoustic thunder rumble"
        )),
        Entry("0.46", "New widgets, thunderstorm audio & crash fix, YouTube TV fix, dynamic world & Jasper care", listOf(
            "Fixed thunderstorm weather crash (ConcurrentModificationException on the audio thread) and added crisp electric lightning crack + deep rolling thunder audio",
            "Fixed YouTube TV playback on the CRT glass and ensured turning off the TV from any source immediately stops YouTube",
            "Added 4 new interactive home-screen widgets (Retro Clock & Weather, Room Quick Controls, Sleep & Sunrise Alarm, and Jasper Companion) plus configurable refresh intervals & manual refresh button for the Window widget",
            "Added optional Jasper care mechanics (Feed, Water, Play, Sleep) with tappable food & water bowls in the room",
            "Unified wind direction across 3D rain, snow, falling leaves, window raindrops, and chimney smoke, and tied falling leaves to weather & trees with options in World → Weather / Sky",
            "Parked cars in Suburbs, City Block, and Trailer Park now randomize on every visit and drive off / arrive over time (configurable in World → Sky)",
            "Lit windows across houses, trailers, apartments, and downtown skyscrapers gradually turn off late at night (configurable in World → Time / Sky)",
            "Fixed Trailer Park house side clipping, rebuilt the PlayStation symbol on the PS5 startup screen, and sped up the ceiling fan"
        )),
        Entry("0.45", "Thumb-friendly UI, full controller support, weather & room fixes", listOf(
            "Big-screen & thumb-friendly UI: moved the status bar, menu button, sub-tabs, Back/Close bar, YouTube bar, achievement pop-ups and toasts to the bottom so nothing is out of reach at the top",
            "Full controller support across the whole app: walk with the left stick or D-pad, look/orbit with the right stick, press A to interact at the center crosshair, L1/R1 to cycle postures or sub-tabs, and navigate all menus",
            "Falling leaves & outdoor debris sliders in World → Weather / Sky: control how much, how fast, and how often leaves, blossoms, pine needles and dust fall, with presets from Off to Heavy",
            "Richer seasonal & weather reactions outside: window ledge snowbanks and fallen leaves, wind-swaying 3D trees and flapping laundry, water rain ripples, chimney smoke in cold weather, and continuous window raindrops that grow and streak down the glass",
            "Downtown LED ticker and billboard now update in real time with live temperature, weather, forecast and auto-detected city",
            "Jasper now blinks naturally while awake, keeps his eyes closed while sleeping or on the bed, and no longer clips the Discman on the bed",
            "PC tower power LED stays solid green while the amber HDD LED blinks; cat frame sides use solid wood; all 5 PlayStation consoles have properly connected rear power/AV cords and controller strain-relief collars",
            "Turning off the TV now stops YouTube immediately, the YouTube bar stays clamped on screen, frame rate pacing stays smooth, and tapping any achievement shows how to unlock it"
        )),
        Entry("0.44", "A tidier menu, light mode and themes", listOf(
            "The menu opens on a home page: quick toggles up top (sound, light, sleep, photo, light / dark UI) and one card per category showing what's on right now",
            "Each category splits into short sub-tabs instead of one long scroll, and Android back steps back to the home page before closing",
            "Light mode, dark mode, follow the phone's dark mode, or follow the room's clock (light while the sun is up, dark after sunset)",
            "Six colour themes for the menus, top bar, dock, boombox and pop-ups: PS1 Night, Memory Card, Vaporwave, Game Boy, Sunset and Arcade"
        )),
        Entry("0.43", "YouTube on the TV, for real", listOf(
            "YouTube now plays on the TV's own screen inside the room: it sits exactly on the curved glass from any angle, with the PS1 look and the TV's glow, and never floats or clips",
            "Tap the TV (or ⏸ on the bar) to pause and play; ⛶ Full still opens the full player"
        )),
        Entry("0.42", "Lava lamp switch, posters, YouTube buttons", listOf(
            "The lava lamp switches off: tap it past the last colour (or use Menu → Room). The wax cools and sinks, and warms back up when you turn it on",
            "The poster above the boombox can be changed: Smash City, Midnight Drive, Mission 2001, Dino Island, Steel Angel, Surf's Up. Tap it, or pick in Menu → Room",
            "Put your own photo on either poster (Menu → Room → Use my photo)",
            "The YouTube buttons can be dragged anywhere by their grip, and tucked into a small tab"
        )),
        Entry("0.41", "Light with character, and YouTube fixed", listOf(
            "The light shafts have more character: thin bright streaks running along them, a slow breath, and their colour going from the light's own by the window to the sky's colour deeper in the room",
            "Dust in the light is bigger, brighter and more plentiful, each speck tinted between the sunlight (or moonlight) and the colour of the sky",
            "Fixed: YouTube on the TV showed up in the top-left corner on newer phones; it sits on the TV again, and hides when the TV is off screen"
        )),
        Entry("0.40", "Stylized light rays", listOf(
            "The light through the window breaks into distinct shafts that drift slowly, brighter near the glass, with specks of dust glinting in them, stepped like the rest of the PS1 picture",
            "Moonlight rays are fainter than sunlight's at the same Light rays setting"
        )),
        Entry("0.39", "The original PS3", listOf(
            "The PS3 is now the original glossy black PS3, matched to a photo of the real one: the domed top that curves side to side, the chrome strip following the front's curve, the card-reader flap, the silver slot panel with the PS logo, PLAYSTATION 3 along the top, the side vents",
            "Every part sits inside the curve of the body (nothing pokes out at the rounded ends)"
        )),
        Entry("0.38", "A properly round PS3", listOf(
            "The PS3 Slim is now one smooth curved shell like the real one: the dome rolling down over the front and sides, rounded corners, the bottom tucked in",
            "Its PS3 logo lies on the curve of the top, and the slot pocket, silver lip, SONY and USB ports sit on the real curved front"
        )),
        Entry("0.37", "Consoles, take two", listOf(
            "Fixed: switching consoles no longer makes the game in it (and its case) disappear",
            "Discs go into every console: the PS2 Slim's lid flips up and the disc drops in; the PS3, PS4 and PS5 pull it in through the slot",
            "The PS3, PS4 and PS5 Slim rebuilt from more photos of the real consoles (the PS3's domed top and slot pocket, the PS4's shell, gap and lower body, the PS5's thin top cover, black core and disc slot)",
            "The PS2, PS4 and PS5 boot-ups redone from recordings of the real ones, with a proper PlayStation symbol",
            "The start screen plays your console's boot-up full screen"
        )),
        Entry("0.36", "PS2, PS3, PS4 and PS5", listOf(
            "Pick the PlayStation under the TV in Menu → Room: the original PS1, or a PS2 Slim, PS3 Slim, PS4 Slim or PS5 Slim, each modelled from the real console's measurements and photos",
            "Each one has its own controller (DualShock 2, DualShock 3, DualShock 4, DualSense), lights (the PS4's light bar, the PS5's blue seams), and the PS2 Slim's lid flips up to show the disc",
            "Each console has its own boot-up and home screen on the TV and its own boot sound",
            "The start screen plays the boot-up of the console you picked",
            "Discs, the TV input and the games work the same on all of them",
            "Fixed: the PS1 controller's grips no longer dip into the rug"
        )),
        Entry("0.35", "Widgets and Android extras", listOf(
            "New Window widget: the real view out of your bedroom window (place, sky, weather), with a live clock, the date, the weather and temperature. Each one has its own options: clock, date, weather, place, window frame, blinds",
            "Quick Settings tile: Room sounds. Pull down the shade and tap to play or pause",
            "Long-press the app icon for shortcuts: Sleep mode, My Music, Photo mode, Use the PC",
            "Picture-in-picture: leave the app and the room keeps going in a small window (turn it off in Menu → Display)",
            "A light vibration tick when you tap things in the room and the dock buttons",
            "Photo mode has a 📤 SHARE button for your last photo or card"
        )),
        Entry("0.34", "Softer shadows and fixes", listOf(
            "Softer, cleaner shadows: twice the detail, smooth edges instead of stair-steps, and no speckle",
            "Fixed: the desk's front edge and top no longer flicker where they meet at the corners",
            "Fixed: small pictures across the room keep their details (the calendar's pumpkin has its face again)"
        )),
        Entry("0.33", "The door sign", listOf(
            "Fixed: the UNDER CONSTRUCTION sign on the door shows its lettering again (sharper, and it fits on the sign)"
        )),
        Entry("0.32", "Fixes", listOf(
            "Fixed: the title screen could slide down and show the room behind it (its layers shared one layout, so moving one moved them all)",
            "Jasper's custom colours are a proper picker now: named colours (Jet black, Chocolate, Tan, Silver...) with a preview of his three colours",
            "Fixed: the mouse on the desk no longer pokes its shell out through its casing"
        )),
        Entry("0.31", "The boombox player and fixes", listOf(
            "Tap the boombox to open its player: the stations and My Music, what's playing with its cover, play / pause / skip, shuffle and repeat, your albums, and adding songs",
            "Fixed: the flicker at the back of Jasper's head (the dark crown's back face sat exactly on the skull's, so the two fought)",
            "Fixed: the Screen cutout options no longer squash the room or shift the PC screen. Now: Full screen, Clear when sideways, or Always clear",
            "Jasper's custom colours are real colour swatches now instead of colour codes"
        )),
        Entry("0.30", "Jasper's neck, for real, and the last of the port", listOf(
            "Fixed properly: Jasper's neck is now part of his head and slopes back into his chest, so nothing pokes out of the back of his head at any angle",
            "Fixed: the Screen cutout choices now visibly change the screen on every phone (Keep clear leaves a black bar, Fill runs the room under the camera)",
            "The elevated train outside has its rumble and clack again",
            "The focus timer rings its soft bell when it's time to switch",
            "The sleep timer fades the sound out over its last minute, turns it off, and lets the phone's screen go to sleep",
            "WinBeats on the PC plays My Music through the boombox player"
        )),
        Entry("0.29", "Notch support and fixes", listOf(
            "New in Menu → Display: Screen cutout. Fill the screen (the room runs under the notch or hole-punch, buttons stay clear), fill everything, or keep clear. It tells you what your phone has: notch, hole-punch, curved edges, rounded corners",
            "Fixed: the menu sliders can be dragged again (the menu was redrawing itself under your finger)",
            "Fixed: the back of Jasper's neck no longer pokes through his head when he turns it",
            "Fixed: the TV's weather channel shows the room's real temperature in your °F / °C, not a fixed one per season"
        )),
        Entry("0.28", "Media controls, a widget, and fixes", listOf(
            "The phone now treats the room like a music app: the media player in your notification shade and lock screen, with the song, cover art, play / pause / skip and a seek bar for your music",
            "Headphone, Bluetooth, car and watch buttons control it; it pauses for phone calls and other players, and when headphones come out",
            "New home-screen widget: what's playing, with play / pause and skip",
            "Fixed: the Tamagotchu no longer shares a spot with the trophy, the grape soda sits on the nightstand, the pizza and magazine sit on the desk and bed, and the box no longer clips into the floor",
            "Fixed: the achievement pop-up was far too tall; it's a compact card now, and the list in Menu → About is tidier",
            "Optimization pass 2: the screen's refresh rate drops to match the frame cap (big battery saving on 90 / 120 Hz phones), Android's Battery Saver lowers the cap, power-saving audio output, memory freed when the app is in the background, a leaner build"
        )),
        Entry("0.27", "Optimization pass, part 1", listOf(
            "Settings are only written when something changed, instead of re-saving every 4 seconds",
            "The status bar only re-lays itself out when its text actually changes",
            "The final screen blit tells the GPU to skip reloading last frame's pixels (saves memory bandwidth on phone GPUs)",
            "YouTube on the TV does no extra work while the camera is still"
        )),
        Entry("0.26", "Jasper's coat and steadier textures", listOf(
            "Choose Jasper's coat in Menu → Room: black & tan, sable, black & silver, black & red, all black, white, or your own colours (saved)",
            "Far-away walls, carpet and posters no longer shimmer as you move: textures that never change get mip levels (still crunchy up close)"
        )),
        Entry("0.25", "Share cards and daily surprises", listOf(
            "Photo mode has a 💌 CARD button: a postcard of your room with the place, time, weather and temperature, saved to Pictures",
            "Something new turns up in the room every day (a package, cold pizza, a note from mom...): tap it to find it"
        )),
        Entry("0.24", "Achievements and the sunrise alarm", listOf(
            "18 achievements to unlock as you live in the room, each with its own trophy card, confetti and fanfare (list in Menu → About)",
            "Sunrise alarm (Menu → Timers): the room fills with warm light for 20 minutes, then your music or birdsong wakes you",
            "The bedside clock shows a dot while the alarm is set",
            "Sleep mode now also turns the sound on, the TV off, and sends Jasper to the bed"
        )),
        Entry("0.23", "YouTube on the TV", listOf(
            "Paste a YouTube link (Menu → Sound → YOUTUBE ON TV), or Share a video to PS1 Bedroom, and it plays on the bedroom CRT",
            "The player is pinned to the TV glass in 3D, with scanlines and an optional PS1 pixel grid that wobbles with the TV",
            "⛶ Full for full screen, recently watched list, and searches open YouTube so you can share a video back",
            "The TV's own channel sound steps aside while YouTube plays"
        )),
        Entry("0.22", "My Music", listOf(
            "Add your own songs, or a whole album folder, from your phone (Menu → Sound → My Music); the files stay where they are",
            "They play through the boombox like the radio: tap it to switch between the FM stations and MY MUSIC",
            "Albums with their covers, play / pause / skip, shuffle and repeat; the notification shows the song with controls",
            "The cover shows in the cassette window and as a jewel case on the dresser, and the tuner LCD scrolls the song name",
            "New \"Now playing\" poster with the album cover"
        )),
        Entry("0.21", "Phone calls and Jasper's voice", listOf(
            "The cordless phone rings every so often while the sound is on: tap it to answer and hear who it is",
            "Miss it and the answering machine takes a message: its red light blinks until you tap it to play them",
            "Jasper gives a soft \"boof\" when you pet him (with a new line every time) and snores while he sleeps",
            "The space heater hums while it runs, with toasts for its modes; posters click into place"
        )),
        Entry("0.20", "Sound", listOf(
            "A native sound engine (no WebView, no Tone.js): synths, noise, filters, envelopes, delay, reverb and a bus compressor, mixed on its own audio thread",
            "Every source is placed in the room and pans as you turn: the PC tower's fan and hard drive, the ceiling fan, the AC, the CRT, the lava lamp, the PlayStation's disc drive",
            "Weather through the window: rain, rain on the glass, downpours, wind, thunder after the lightning; crickets, birds, owls, frogs, leaves, the city hum, fog horns",
            "All the music: the PC's five channels, the TV's channels and tapes, both boombox stations, and every game's own soundtrack and title jingle",
            "The living room TV muffled through the door, each place outside with its own sounds and critters (coyotes, gulls, loons, the ice-cream truck...)",
            "Every tap has its sound, plus footsteps, the chair and the bed",
            "Menu → Sound: audio on / off, master volume and the full mixer; the dock's speaker button toggles it",
            "Keeps playing with the screen off or in other apps (with a notification and a Pause button)"
        )),
        Entry("0.19", "The PC", listOf(
            "The monitor comes alive: the XP desktop with its WinBeats equalizer and taskbar clock, plus four 2001-02 game parodies (Menu → Sound → PC SCREEN cycles them); its glow tints the desk",
            "Tap the tower to switch the PC off and on (\"DO NOT TURN OFF!\")",
            "Tap the monitor to roll up to the desk and use the PC: the island wallpaper and 24 desktop icons (each one a joke)",
            "Messenger with five buddies who chat back once you dial in, an away message, the Dial-Up connection",
            "WinBeats plays the boombox stations, and a full game of Minesweeper (long-press or flag mode to flag)",
            "The green start button logs off; Android back works too"
        )),
        Entry("0.18", "Start screen, how-to and photo mode", listOf(
            "The PS1-style start screen: PRESS START, the four-colour diamond on white, then the licence screen (tap to skip; turn it off in Display)",
            "The five-card how-to on first launch (and in About → HOW TO)",
            "Photo mode from the dock: the room without any buttons, a shutter that saves to Pictures / PS1 Bedroom, and HIDE for a clean screen (tap to bring it back)"
        )),
        Entry("0.17", "The menu and the top bar", listOf(
            "MENU with the World, Sound, Room, Display, Timers and About tabs (a bottom sheet in portrait, a side panel in landscape)",
            "World: live clock / pick a time / time-lapse, the 15 places, seasons, all 15 weathers, auto-changing weather, live weather for your city (Open-Meteo), sky styles, moon mode",
            "Room: blinds, lights, heater, fan, AC, TV, put the games back in order, posters, wall / carpet / sheet colours, decorations",
            "Display: Battery saver / Balanced / High, frame rate, eco idle, the looks (Signature, Warm VHS, Noir...), every PS1 effect slider, °F / °C",
            "Timers: sleep mode, the sleep timer (the screen fades to black when it ends) and the focus timer",
            "Top bar: tap the time, temperature, season, weather or place to step through them; hold one to open the World menu",
            "Every setting is saved and comes back the next time you open the app",
            "Android back closes the menu or stands you up; the test shortcuts are gone"
        )),
        Entry("0.16.1", "Fixes", listOf(
            "Jasper walks to the edge of the bed and jumps up instead of walking into it, and goes around the desk, dresser, nightstand, TV cart, chair and heater",
            "Power cords run up the face of the baseboard and leave the side of their plugs instead of going through them",
            "The cardboard box sits on the floor instead of sinking into it",
            "The wastebasket is properly full of crumpled paper",
            "The bed's comforter drapes over the sides and foot, and the folded top sheet lies on the mattress instead of hovering",
            "The Tamagotchu sits fully on the dresser"
        )),
        Entry("0.16", "Walk, sit and lie down", listOf(
            "The bottom dock: Walk, Sit, Bed and Free cam (Sound, Sleep and Photo arrive with the menus)",
            "Walk around the room in first person with the on-screen joystick; drag to look, pinch to zoom",
            "Sit in the office chair and roll it between the TV, the desk and the window (\"CHAIR →\"), or tap the chair",
            "Lie on the bed propped up on the pillows (tap the bed)",
            "Crosshair, pop-up messages when you tap things, and a PUT GAME BACK button while you hold a game",
            "Dust in the light beam now takes the colour of the sun or moon light instead of always blue",
            "PC tower: the power LED no longer clips into the floppy slot, a flickering amber drive LED, the MP3 cable plugs into the third front port, and the HP and Intel badges sit on the lower panel",
            "The version tag moved to the top-right corner"
        )),
        Entry("0.15.1", "Fixes", listOf(
            "Fixed: 0.15 crashed on launch while building the places outside the window"
        )),
        Entry("0.15", "15 places outside the window", listOf(
            "Downtown, a 40th-floor skyscraper, Neon City, the neighborhood, suburbs, a backyard, a trailer park, Arizona, a desert canyon, a tropical island, a beach boardwalk, a lake, a forest, the mountains and a farm",
            "Each place has its own painted horizon, 3D yard (houses, fences, palms, cacti, docks, a barn...), PS1-style sprite trees and its own ground",
            "Live details: the Ferris wheel and lighthouse, trains on the neon el, helicopters and street traffic far below, a smoking volcano, a windmill, swings and wind chimes, tumbleweeds",
            "Everything follows the season, the time and the weather: bare trees in winter, porch lights and neon at night, puddles in the rain",
            "Test shortcut until the menus: a three-finger tap in the middle of the screen changes the place (left: time, right: weather)",
            "Fixed: Jasper's head no longer clips or flickers when he turns it (the neck now turns with his head)"
        )),
        Entry("0.14", "Weather", listOf(
            "Rain outside the window with splashes on the sill and the ground, heavier and more slanted in a storm",
            "Snow that drifts and blows sideways in a blizzard; falling leaves in fall and blossoms in spring",
            "Fireflies blinking around the trees on clear summer nights, dust drifting in the sun or moon beam",
            "The window glass beads up with raindrops that run down, fogs over, and frosts at the edges in winter",
            "Seasonal ground outside (grass, flowers, fallen leaves, snow; darker when wet) and lightning that lights up the room",
            "Engine: GPU instancing, so each particle type is a single draw call",
            "Test shortcut until the menus arrive: a three-finger tap on the right half of the screen changes the weather (left half: the time)"
        )),
        Entry("0.13", "Tapes, stars and the little things", listOf(
            "Tap a VHS tape on the TV cart to put it in the VCR: Taped Off TV '99 (old commercials), Kung Fu Midnight and Ocean Dreams, each with a PLAY counter and tracking band",
            "Answering machine on the desk with a message light",
            "Glow-in-the-dark stars on the ceiling: the Big Dipper, Orion and Cassiopeia plus a moon sticker; they charge up in the light and glow when it's dark",
            "Space heater by the bed: the coils glow orange on winter nights (tap for auto / on / off)",
            "Poster frame above the desk: tap to cycle five original posters",
            "Seasonal decorations come out by date: a jack-o'-lantern and paper bats for Halloween, a little tree in winter, hearts for Valentine's, a box fan in summer",
            "Room colours (walls, carpet, sheets) are ready for the settings menu; tap the boombox to change stations"
        )),
        Entry("0.12", "The game library", listOf(
            "11 PS1 games on the hutch shelf and every Grand Theft Auto on the wall shelf, each with its own hand-drawn cover, spine and back",
            "Tap a case to pick it up (it rides in front of you), then tap the PlayStation: the lid pops, the discs swap, and it boots the new game",
            "Every game plays on the TV in its own style: skating, gliding, an RPG battle, web-swinging, a car chase, corridor shooters, broom flying, a card duel",
            "The top-down GTAs scroll a city with traffic and cops; GTA III to VI show a 3D chase-cam drive, each with its own city, sky and HUD",
            "Fixed: Jasper's neck no longer pokes out of the back of his head"
        )),
        Entry("0.11", "The PlayStation", listOf(
            "The grey PlayStation on the rug: POWER, RESET and OPEN buttons that press down, a hinged lid, the disc spinning up",
            "Turn it on and the TV switches to A/V 1: the publisher screen, the PlayStation logo, the title screen, then Bandi-coot Rush",
            "Open the lid for the system menu; tap the controller to make him jump",
            "DualShock with its cable, the game case lying beside the console, memory cards in both slots",
            "Real hookups: the A/V cable runs up the cart into the TV's yellow / white / red jacks, power cords run to a strip at the wall",
            "Fixed: the fan blade brackets no longer flicker, and the chair's height lever now meets its knob"
        )),
        Entry("0.10.1", "Fixes", listOf(
            "TV static no longer shows a stray \"CH 03\" next to the real channel number",
            "Ceiling fan light: the glass shade now sits snugly in its brass collar (no more dark notches at the seam)"
        )),
        Entry("0.10", "The CRT TV", listOf(
            "20\" MAGNAVOLT CRT with domed glass, scanlines, a rolling band and phosphor glow, on the walnut media cart",
            "VCR with the blinking 12:00, VHS tapes and game cases on the shelf, rabbit-ear antennas, A/V jacks",
            "Seven channels: late-night anime, the weather channel with today's forecast, the VCR, the shopping network, static, cartoons and the music video countdown",
            "Tap the TV to flip channels (past the last one it turns off); tap the red button for power",
            "The TV's glow flickers onto the room, and it only redraws while it's on and in view",
            "Glow-in-the-dark stars and the wall outlet; baseboards now match the web version exactly"
        )),
        Entry("0.9", "Jasper", listOf(
            "Jasper the German Shepherd: black & tan pixel fur, ice-blue eyes, his left one half brown",
            "He wanders between his favourite spots: the rug, the door, the TV, the desk and any sunbeam on the floor",
            "Hops up on the bed to sleep at night and when there's a storm",
            "Jointed like a toy: he walks, sits and lies down, breathes, looks around, and his tail hangs and wags",
            "Tap him to pet him: tail wag and a head tilt (a sleeping Jasper wakes up)"
        )),
        Entry("0.8", "String lights, gadgets and furniture-kit models", listOf(
            "String lights along the top of the back and right walls: tap a bulb for warm white, rainbow or off",
            "Succulent and cactus on the windowsill",
            "Desk speakers, potted plants, coat rack and the open cardboard box (Kenney Furniture Kit, CC0)",
            "Discman with earbuds on the bed, Game Kid Advance on the nightstand, Noka brick phone and Tamagotchu",
            "Fixed: the office chair's backrest is now properly connected to the seat"
        )),
        Entry("0.7", "Ceiling fan, dresser, nightstand and more", listOf(
            "Ceiling fan: blades spin up and coast down, the glass shade glows when the light is on, pull chains jump",
            "Tap the fan to toggle its light, tap the blades or motor to start / stop it, tap the AC to switch it",
            "AC louvre sweeps while the unit runs",
            "Dresser with drawers, boombox, family photo, trophy and the SMASH CITY band poster",
            "Nightstand with a reading lamp (tap it), water glass and a book",
            "Wastebasket with crumpled paper (and one missed shot), backpack by the dresser",
            "Wall calendar showing today's month like 2001, with a pixel picture for every month and the days crossed off",
            "THE MATRIKS movie poster over the bed"
        )),
        Entry("0.6", "The bed, the chair and an in-app changelog", listOf(
            "The bed: bookcase headboard with turned posts and finials, rails, footboard, shoe box and skateboard underneath",
            "Fitted sheet, wrinkled space comforter, folded top sheet and plump pillows",
            "Jumbo LED clock radio on the nightstand showing the room's time",
            "2001 office chair with casters, gas lift, contoured seat and lumbar backrest",
            "Wall shelf with the cat photo (tap it for night-vision eyes), plant and books",
            "Tap the lava lamp to switch its colours",
            "Baseboard now runs all the way around the room, right up to the door casing",
            "This changelog (tap the version tag in the corner)"
        )),
        Entry("0.5", "The desk corner", listOf(
            "Desk and hutch with the live-edge front, CD rack, strategy guide and a loose CD",
            "HP Pavilion tower with the \"DO NOT TURN OFF\" sticky note, monitor, keyboard and mouse",
            "Cordless phone, MP3 player and its cable, Surge can, lava lamp with rising blobs",
            "Wall AC unit, the construction sign on the door, hinges, knobs and the light under the door",
            "Weathering maps now match the web version surface for surface"
        )),
        Entry("0.4", "The city outside", listOf(
            "3D towers with lit windows by night and glass by day, roof beacons and a back row of towers",
            "Highway traffic that follows the clock and the weather: rush hours, quiet nights, police, wet roads",
            "Commuter train on the viaduct, tri-vision billboards and the LED ticker, trees swaying with the seasons"
        )),
        Entry("0.3", "The sky", listOf(
            "Sky dome with time-of-day colours, sunset glow, twinkling stars and the Milky Way",
            "Sun and moon where the real sky puts them, with tonight's moon phase",
            "Drifting clouds, horizon ring and the painted city backdrop",
            "Airliners, birds, shooting stars, rainbows, distant rain and snow, haze"
        )),
        Entry("0.2.1", "Fixes", listOf(
            "Fixed the bright wedge and stripes from the shadows",
            "Objects no longer disappear before they leave the screen",
            "Faster texture updates with no memory churn"
        )),
        Entry("0.2", "Performance, blinds and time of day", listOf(
            "Zero-garbage renderer, merged static furniture, shadows redrawn only when something moves",
            "60 fps cap, 30 / 20 fps when idle, and automatic cool-down when the phone gets warm",
            "Window blinds with all four states; tap the blinds or the light switch",
            "Real sun and moon positions: the light through the window follows the time of day"
        )),
        Entry("0.1", "First native build", listOf(
            "Kotlin + OpenGL ES 3 engine: three.js-identical geometry, the PS1 shader and the post pass",
            "The room shell: walls, floor, ceiling, rug, door and the room lights",
            "Orbit camera with drag, pinch and two-finger pan"
        ))
    )

    private fun dp(a: Activity, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics).toInt()

    /** Small version tag in the top-right corner (the dock is along the bottom); tapping it opens the changelog. */
    fun addVersionTag(activity: Activity, root: FrameLayout) {
        val tag = TextView(activity).apply {
            text = "NATIVE v${entries.first().version}"
            setTextColor(Color.argb(200, 226, 232, 240))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(activity, 10f), dp(activity, 6f), dp(activity, 10f), dp(activity, 6f))
            background = GradientDrawable().apply { setColor(Color.argb(150, 8, 10, 24)); cornerRadius = dp(activity, 8f).toFloat(); setStroke(dp(activity, 1f), Color.argb(120, 167, 139, 250)) }
            setOnClickListener { show(activity) }
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.setMargins(0, dp(activity, 12f), dp(activity, 12f), 0)
        root.addView(tag, lp)
    }

    fun show(activity: Activity) {
        val sb = SpannableStringBuilder()
        for ((i, e) in entries.withIndex()) {
            if (i > 0) sb.append("\n\n")
            val start = sb.length
            sb.append("v${e.version}  ")
            sb.setSpan(ForegroundColorSpan(Color.rgb(167, 139, 250)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val t0 = sb.length
            sb.append(e.title.uppercase())
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.1f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(Color.rgb(248, 250, 252)), t0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            for (n in e.notes) sb.append("\n  · ").append(n)
        }
        val body = TextView(activity).apply {
            text = sb
            setTextColor(Color.rgb(203, 213, 225))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.2f)
            setPadding(dp(activity, 20f), dp(activity, 8f), dp(activity, 20f), dp(activity, 8f))
        }
        val scroll = ScrollView(activity).apply { addView(body) }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("PS1 Bedroom native: changelog")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }
}
