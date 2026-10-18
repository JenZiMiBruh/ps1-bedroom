# PS1 Bedroom — native Android port (Kotlin)

Goal: the same app, 1-to-1, as a real Android app. No WebView, no HTML.
Why: all-night audio with no crackle, a phone that stays cool, a proper background media service.

## Stack

| Part | HTML version | Native version |
|---|---|---|
| Language | JavaScript | **Kotlin** |
| 3D | three.js r128 (WebGL) | **OpenGL ES 3.0** with a small scene graph that mirrors three.js (Group / Mesh / materials / camera), so positions, sizes and colours port number-for-number |
| PS1 look | `makePS1Material` vertex snap + affine warp + grime, post pass (dither, light rays, AO) | The same GLSL, ported to GLES 3.0 |
| Textures | HTML canvas drawing | **android.graphics.Canvas**, the same drawing calls (fillRect, arcs, gradients, text) |
| Models | Kenney / Quaternius GLB embedded as base64 | The same GLB files as assets, small glTF loader |
| Audio | Tone.js synthesising ~100 live nodes | **Every layer recorded once from the HTML engine into seamless loops** (identical sound), mixed natively with **AudioTrack** inside a foreground media service |
| Menus / HUD | HTML + CSS | Android Views drawn in the same pixel style |
| Android extras | Java (widgets, lock screen, notification, share) | Kept and moved to Kotlin |
| Build | aapt2 → javac → d8 → apksigner (no Gradle) | aapt2 → **kotlinc** → d8 → apksigner (still no Gradle) |

## Porting order (each phase ships a test APK)

1. **Engine core**: GL surface, scene graph, camera + orbit controls, PS1 material, post pass, frame pacing
2. **The room shell**: walls, floor, ceiling, window, blinds, lighting, shadows, the light rays
3. **Sky and outside**: sky dome, sun / moon / stars / clouds, the 15 places, weather particles, time and seasons
4. **Audio service**: recorded loops, native mixer, positional volume, sleep timer fade, lock-screen controls. *Sleep-ready test build here.*
5. **Furniture and props**: desk, PC, bed, nightstand + clock radio, dresser, boombox, shelves, posters, calendar, Kenney models
6. **TV + PlayStation**: CRT channels, PS1 with buttons and lid, game library and shelf, VCR tapes
7. **Jasper**, the phone, decorations, daily surprises, achievements
8. **Menus and top bar**: all six menu tabs, dock, photo mode, share card, settings saved
9. **The PC desktop**, **My Music** (albums, cover art), **YouTube on the TV**
10. **Parity pass**: side-by-side screenshots against the HTML version from the same camera spots, and the flicker detector

## HTML source map (src/ps1_bedroom.html)

| Lines | Section |
|---|---|
| 585–700 | PS1 material (vertex snap, affine warp, grime) |
| 1686–2200 | Environment: sun, moon, weather kinds, sky painting |
| 2200–2500 | Renderer, camera, orbit controls, post pass, render targets |
| 2500–3500 | The room: walls, floor, rug, door, window, blinds, TV cart and CRT |
| 3494–4400 | Exterior world, sky dome, buildings, traffic, billboards |
| 5362–5770 | PlayStation, hookups |
| 5771–6700 | Game library, GTA collection, cover art |
| 7500–8000 | Bed, ceiling fan, desk props |
| 8225–8670 | Weather and season particle FX |
| 8670–10650 | Sound engine (Tone.js) |
| 10650–12008 | Player modes, input, interactions, animate loop |
| 12008–12509 | Dresser, boombox, nightstand, calendar, posters |
| 12509–12794 | Jasper |
| 12794–13110 | VCR tapes, phone calls |
| 13110–13298 | Posters, room colours, decorations |
| 13298–14130 | My Music, boombox player |
| 14130–14241 | Static batching |
| 14241–15720 | The 15 places outside |
| 15720–16519 | App shell: menus, settings, live weather, sleep timer, photo mode |
| 16519–16936 | The PC desktop |
| 16936–17178 | YouTube on the TV |
| 17178–17480 | Sleep mode, alarm, share card, achievements, surprises |
