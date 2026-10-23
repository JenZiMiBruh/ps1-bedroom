# PS1 Bedroom: launch kit

Everything here is a draft for you to post from your own accounts. Nothing has been posted.

## The biggest win first: let people try it in one tap

Most people won't install an APK from a stranger. The app is a single HTML file, so it can run in any browser:

1. **GitHub Pages web version.** Turn on Pages for the repo and host `src/ps1_bedroom.html` as `index.html`. Every post then links to "play it in your browser right now", with the APK for people who like it.
2. **itch.io page** (free). itch is where people browse for cozy and retro things. Upload the HTML as a web game (plays on the page) plus the APK as a download. Tags: `cozy`, `ps1`, `low-poly`, `ambient`, `relaxing`, `retro`, `lofi`, `sleep`.
3. **Google Play** later. It needs a $25 developer account, a privacy policy and a Gradle/AAB build. Worth it once there's interest.

## Where to post (in this order)

| Where | Why | Angle |
|---|---|---|
| r/PS1, r/psx, r/lowpoly | They love the look | The room, the shelf, the GTA tributes |
| r/cozygames, r/CozyGamers | Ambience and coziness | "A room to fall asleep in" |
| r/lofi, r/ambientmusic | Background ambience | Rain on the window, the boombox, sleep timer |
| r/androidapps, r/Android (Sunday app thread) | Android users | Widgets, lock screen, DeX on a TV |
| r/samsung, r/SamsungDex | DeX owners | "Turn your TV into a 2001 bedroom window" |
| r/indiegames, r/IndieDev | Devs boost devs | The making-of: one HTML file, no engine |
| r/threejs, r/webgl | Tech crowd | PS1 shader tricks, volumetric light |
| Hacker News (Show HN) | Big traffic if it lands | "One HTML file, three.js, no build step" |
| TikTok / YouTube Shorts / Instagram Reels | Fastest growth for visual stuff | 10–20 s clips, see below |

Read each subreddit's rules first. Many only allow self-promotion in a weekly thread or need you to have commented there before. Post as the person who made it, answer every comment, and space the posts out over two weeks rather than one day.

## Short video ideas (record from the app, 9:16)

1. **"POV: it's 2001 and it's raining"**: start on the window, rain on the glass, pan to the CRT, then the dog asleep on the bed. Use the app's own sound.
2. **Time-lapse**: 1 day = 5 min, sped up so a whole day passes in 15 s through the blinds.
3. **"Which window would you fall asleep to?"**: quick cuts through the 15 outside places (Arizona monsoon, skyscraper at night, island volcano, trailer park).
4. **The boombox**: drop in a real album, the cover appears on the CD case on the dresser.
5. **DeX on a TV**: the phone plugged into a TV, the room filling the screen at night.

Caption ending: "free, link in bio".

## Draft posts

### Reddit (r/PS1 / r/psx)
**Title:** I made a PS1-style bedroom you can leave running all night (free, browser + Android)

Body:
> It's a little 2001 bedroom with real-time day and night, real weather for your city, rain on the window, a CRT with a PlayStation full of games, a German Shepherd who naps in the sunbeam, and a boombox that plays your own albums. You can pick what's outside the window: downtown, a skyscraper 40 floors up, Arizona, a trailer park, a tropical island and more.
>
> It's all one HTML file (three.js + Tone.js), PS1 vertex wobble and affine textures included. Play it in your browser: [link] · Android APK: [link]
>
> Would love to know what you'd add to the room.

### Show HN
**Title:** Show HN: PS1 Bedroom, a cozy PS1-style room in one HTML file

> A real-time ambient room that follows your clock and weather, built as a single HTML file with three.js and Tone.js (everything is synthesized, no audio files). Volumetric light through the blinds, 15 outside environments, an Android wrapper with no Gradle (aapt2 → javac → d8 by hand). [link]

### r/androidapps / r/SamsungDex
**Title:** PS1 Bedroom: a cozy ambience app that turns your TV into a rainy 2001 window (free, no ads)

> Plug your phone into a TV with DeX and leave it on all night: the sky follows the real time, weather comes from your city, and there's a sleep timer that fades everything out. Home-screen widgets, lock-screen controls, your own music on the boombox. [link]

## Before promoting widely

- **Name and branding:** the GTA collection uses real game names and look-alike covers. Fine for a free fan project, but app stores and itch can pull listings over trademarks. For a store release, consider renaming those to parody titles.
- **Screenshots and GIFs:** refresh the README images with the dog, the PC, the boombox and a few outside places (planned for the v1.2 publish).
- **A short landing line:** "A cozy PS1 bedroom that lives on your real clock and weather." Use it everywhere.
- **Feedback loop:** turn on GitHub Discussions or Issues so people have somewhere to talk to you.
