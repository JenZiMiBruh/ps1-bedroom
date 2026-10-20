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
