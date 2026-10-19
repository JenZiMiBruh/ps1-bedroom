package com.ps1bedroom.room

import com.ps1bedroom.room.Games.ALL_GAMES
import com.ps1bedroom.room.Games.BANDICOOT_INDEX
import com.ps1bedroom.room.Games.GTA_FIRST_INDEX
import com.ps1bedroom.room.Games.GTA_GAMES
import com.ps1bedroom.room.Games.PS1_GAMES
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.Euler
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Mat4
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.PerspectiveCamera
import com.ps1bedroom.room.engine.Quat
import com.ps1bedroom.room.engine.Texture
import com.ps1bedroom.room.engine.Vec3
import com.ps1bedroom.room.engine.Weather
import com.ps1bedroom.room.engine.createCanvasTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * GAME LIBRARY (HTML lines 4836-4860, 6242-6531, 6532-6900): the PS1 games on the hutch shelf, the GTA collection on
 * the wall shelf, picking a case up (it rides in front of the camera), putting it back, the disc swap into the
 * PlayStation, per-game disc labels, and each game's title screen and gameplay style on the TV.
 */
class GameLibrary(private val room: Room) {
    val caseMeshes = arrayOfNulls<Mesh>(ALL_GAMES.size)
    private class Slot(val parent: Object3D, val pos: Vec3, val rot: Euler)
    private val slots = arrayOfNulls<Slot>(ALL_GAMES.size)
    private val FLOOR_SLOT = BANDICOOT_INDEX
    private val slotOf = IntArray(ALL_GAMES.size) { it }
    var insertedGame = BANDICOOT_INDEX; private set
    var heldGame: Int? = null; private set
    lateinit var camera: PerspectiveCamera
    lateinit var ps1: Ps1

    // ---- Shelves ----
    /** Face-out display on the hutch (two left of the old poster spot, three to its right) and a spine-out row at the left end. */
    fun buildHutchShelf(deskGroup: Group, matJewelPlastic: Material) {
        val HUTCH_TOP_Y = 2.9975f
        val faceOutX = floatArrayOf(-1.07f, -0.80f, 0.80f, 1.07f, 1.34f)
        for (i in 0 until 5) {
            val lean = 0.14f
            val gameCase = Games.buildPs1JewelCase(PS1_GAMES[i], matJewelPlastic)
            gameCase.position.set(faceOutX[i], HUTCH_TOP_Y + 0.12f * cos(lean), -0.6f - 0.12f * sin(lean))
            gameCase.rotation.set(-lean, if (i % 2 == 1) 0.05f else -0.04f, 0f)
            deskGroup.add(gameCase); register(i, gameCase)
        }
        var spineX = -1.47f
        for (i in 5 until PS1_GAMES.size) {
            val thick = if (PS1_GAMES[i].discs > 1) 0.044f else 0.022f
            spineX += thick / 2
            val gameCase = Games.buildPs1JewelCase(PS1_GAMES[i], matJewelPlastic)
            gameCase.position.set(spineX, HUTCH_TOP_Y + 0.12f, -0.2f)
            gameCase.rotation.y = (PI / 2 + (i % 3 - 1) * 0.03).toFloat() // spine (left edge) faces the room
            deskGroup.add(gameCase); register(i, gameCase)
            spineX += thick / 2 + 0.006f
        }
    }
    /** The case for the disc that's in the console, lying face-up beside it. */
    fun registerFloorCase(mesh: Mesh) = register(BANDICOOT_INDEX, mesh)
    /** The case on the floor steps aside for bigger consoles (its home moves; it moves too if it's at home). */
    fun moveFloorCase(x: Float) {
        val s = slots[FLOOR_SLOT] ?: return
        // whichever game's case is lying in the floor spot right now
        for (gi in caseMeshes.indices) {
            val m = caseMeshes[gi] ?: continue
            if (slotOf[gi] != FLOOR_SLOT || inHand[gi]) continue
            if (m.parent === s.parent && abs(m.position.x - s.pos.x) < 0.001f && abs(m.position.z - s.pos.z) < 0.001f) m.position.x = x
        }
        s.pos.x = x
    }

    /** Collector's wall shelf on the left wall, between the calendar and the band poster. */
    fun buildGtaShelf(matJewelPlastic: Material) {
        val gtaShelf = Group()
        gtaShelf.position.set(-3.715f, 0f, 0.82f)
        gtaShelf.rotation.y = (PI / 2).toFloat() // local +Z faces into the room, local +X runs toward the back wall
        room.scene.add(gtaShelf)
        val shelfWood = PS1.material(map = room.tex.wood, color = 0x9a7a62)
        val SHELF_Y = floatArrayOf(3.12f, 2.62f, 2.12f)
        val SHELF_DEPTH = 0.13f
        PS1.syncGrime(226)
        val matShelfBracket = PS1.material(color = 0x2b2b30)
        for (y in SHELF_Y) {
            val board = Mesh(Geo.box(1.34f, 0.035f, SHELF_DEPTH, 4, 1, 1), shelfWood)
            board.position.set(0f, y, SHELF_DEPTH / 2); board.castShadow = true; board.receiveShadow = true; gtaShelf.add(board)
            val lip = Mesh(Geo.box(1.34f, 0.016f, 0.01f), shelfWood)
            lip.position.set(0f, y + 0.025f, SHELF_DEPTH - 0.005f); gtaShelf.add(lip)
            for (bx in floatArrayOf(-0.655f, 0.655f)) {
                val bracket = Mesh(Geo.box(0.022f, 0.1f, SHELF_DEPTH - 0.02f), matShelfBracket)
                bracket.position.set(bx, y - 0.0675f, (SHELF_DEPTH - 0.02f) / 2); gtaShelf.add(bracket)
            }
        }
        // Little brass plaque on the top shelf
        val plaqueTex = createCanvasTex(96, 16) { ctx, w, h ->
            ctx.fillStyle = "#b8892b"; ctx.fillRect(0, 0, w, h)
            ctx.fillStyle = "#3a2508"; ctx.font = "bold 9px Georgia, serif"; ctx.textAlign = "center"; ctx.fillText("THE COLLECTION", w / 2, 12)
        }.tex
        val plaque = Mesh(Geo.plane(0.34f, 0.06f), PS1.material(map = plaqueTex))
        plaque.position.set(0f, SHELF_Y[0] - 0.045f, SHELF_DEPTH - 0.01f); gtaShelf.add(plaque)
        GTA_GAMES.forEachIndexed { j, game ->
            val gi = GTA_FIRST_INDEX + j
            val row = j / 4; val col = j % 4
            val mesh = if (game.platform == "ps1") Games.buildPs1JewelCase(game, matJewelPlastic) else Games.buildKeepCase(game)
            val hgt = mesh.userData["caseHeight"] as Float
            val lean = 0.08f
            mesh.position.set(-0.48f + col * 0.32f, SHELF_Y[row] + 0.018f + (hgt / 2) * cos(lean), 0.022f + (hgt / 2) * sin(lean))
            mesh.rotation.set(-lean, if (col % 2 == 1) 0.03f else -0.025f, 0f)
            gtaShelf.add(mesh); register(gi, mesh)
        }
    }
    private fun register(gi: Int, mesh: Mesh) {
        caseMeshes[gi] = mesh
        room.registerInteractable(mesh, "gamebox:$gi")
        slots[gi] = Slot(mesh.parent!!, mesh.position.clone(), Euler().copy(mesh.rotation))
    }
    /** Cases move, so none of them may be merged into a static batch. */
    fun batchExcluded(): List<Object3D> = caseMeshes.filterNotNull()

    // ---- Disc labels (made on first use) ----
    private val discLabelCache = HashMap<String, Texture>()
    fun discLabelTex(game: Game): Texture = discLabelCache.getOrPut(game.title) {
        createCanvasTex(128, 128, weather = Weather(seed = (game.year + game.title.length * 3).toDouble(), scratches = 10, blotch = 0.05)) { ctx, w, h ->
            val g = ctx.createRadialGradient(64, 64, 10, 64, 64, 64)
            g.addColorStop(0, "#f1f5f9"); g.addColorStop(0.5, game.accent); g.addColorStop(1, "#111827")
            ctx.fillStyle = g; ctx.fillRect(0, 0, w, h)
            Games.drawPsMark(ctx, 58, 12, 6)
            ctx.fillStyle = "#0b0b0f"; ctx.font = "bold 10px Impact, sans-serif"; ctx.textAlign = "center"
            val words = game.title.uppercase().split(" ")
            val mid = ceil(words.size / 2.0).toInt()
            ctx.fillText(words.subList(0, mid).joinToString(" "), 64, 40, 116)
            ctx.fillText(words.subList(mid, words.size).joinToString(" "), 64, 96, 116)
            ctx.font = "bold 6px sans-serif"
            ctx.fillText("${game.pub.uppercase()} · ${game.year}", 64, 108, 110)
            ctx.fillStyle = "#1f2937"; ctx.beginPath(); ctx.arc(64, 64, 9, 0, PI * 2); ctx.fill()
        }.tex
    }

    // ---- World-space tweens for cases (arcing flights, or following the camera while held) ----
    private class Tween(val mesh: Mesh, val gi: Int, val target: Int, val dur: Float, val arc: Float, val onDone: (() -> Unit)?) {
        var t = 0f; val fromPos = Vec3(); val fromQuat = Quat()
    }
    private val tweens = ArrayList<Tween>()
    private val HAND = -1
    private val HAND_OFFSET = Vec3(0.24f, -0.2f, -0.62f)
    private val HAND_TILT = Quat().setFromEuler(Euler(0.15f, -0.35f, 0.05f))
    private val tmpTo = Vec3(); private val tmpToQ = Quat(); private val tmpQ = Quat(); private val tmpM = Mat4(); private val tmpM2 = Mat4()
    private fun handTransform(outPos: Vec3, outQuat: Quat) {
        camera.updateMatrixWorld()
        outPos.copy(HAND_OFFSET).applyMatrix4(camera.matrixWorld)
        tmpQ.setFromRotationMatrix(camera.matrixWorld)
        outQuat.multiplyQuaternions(tmpQ, HAND_TILT)
    }
    private fun slotWorld(slot: Slot, outPos: Vec3, outQuat: Quat) {
        slot.parent.updateMatrixWorld()
        tmpM.compose(slot.pos, tmpQ.setFromEuler(slot.rot), Vec3(1f, 1f, 1f))
        tmpM2.multiplyMatrices(slot.parent.matrixWorld, tmpM)
        outPos.setFromMatrixPosition(tmpM2); outQuat.setFromRotationMatrix(tmpM2)
    }
    private fun flyCase(gi: Int, target: Int, dur: Float, arc: Float, onDone: (() -> Unit)? = null) {
        val mesh = caseMeshes[gi]!!
        tweens.removeAll { it.mesh === mesh }
        room.scene.attach(mesh)
        val tw = Tween(mesh, gi, target, dur, arc, onDone)
        tw.fromPos.copy(mesh.position); tw.fromQuat.setFromEuler(mesh.rotation)
        tweens += tw
        room.onShadowsChanged()
    }
    private val inHand = BooleanArray(ALL_GAMES.size)
    private fun updateCaseTweens(t: Float, dt: Float) {
        var i = tweens.size - 1
        while (i >= 0) {
            val tw = tweens[i]
            tw.t = min(1f, tw.t + dt / tw.dur)
            if (tw.target == HAND) handTransform(tmpTo, tmpToQ) else slotWorld(slots[tw.target]!!, tmpTo, tmpToQ)
            val k = tw.t * tw.t * (3 - 2 * tw.t)
            tw.mesh.position.set(tw.fromPos.x + (tmpTo.x - tw.fromPos.x) * k, tw.fromPos.y + (tmpTo.y - tw.fromPos.y) * k + sin(PI.toFloat() * k) * tw.arc, tw.fromPos.z + (tmpTo.z - tw.fromPos.z) * k)
            tw.mesh.setRotationFromQuat(tmpQ.slerpQuaternions(tw.fromQuat, tmpToQ, k))
            if (tw.t >= 1) {
                tweens.removeAt(i)
                if (tw.target != HAND) {
                    val slot = slots[tw.target]!!
                    slot.parent.add(tw.mesh)
                    tw.mesh.position.copy(slot.pos); tw.mesh.rotation.copy(slot.rot)
                    if (!room.interactables.contains(tw.mesh)) room.interactables += tw.mesh
                    room.onShadowsChanged()
                } else inHand[tw.gi] = true
                tw.onDone?.invoke()
            }
            i--
        }
        // A held case rides along in front of the camera
        val held = heldGame
        if (held != null && inHand[held]) {
            val mesh = caseMeshes[held]!!
            handTransform(mesh.position, tmpToQ); mesh.setRotationFromQuat(tmpToQ)
            mesh.position.y += sin(t * 2.2f) * 0.006f
        }
    }
    private fun releaseFromInteractables(mesh: Mesh) { room.interactables.remove(mesh) }
    var sfx: (String) -> Unit = {}
    /** A game went into the PlayStation (achievements). */
    var onInserted: () -> Unit = {}

    fun pickUpGame(gi: Int) {
        if (heldGame != null) putBackHeld()
        heldGame = gi
        inHand[gi] = false
        releaseFromInteractables(caseMeshes[gi]!!)
        flyCase(gi, HAND, 0.45f, 0.15f)
        sfx("casePickup")
    }
    /** Every case back on its own shelf spot in the original order (the game in the console stays on the floor). */
    fun sortGameShelves() {
        if (heldGame != null) putBackHeld()
        val ins = insertedGame
        var moved = 0
        for (gi in ALL_GAMES.indices) {
            val target = if (gi == ins) FLOOR_SLOT else if (gi == BANDICOOT_INDEX) ins else gi
            if (slotOf[gi] == target) continue
            slotOf[gi] = target
            releaseFromInteractables(caseMeshes[gi]!!)
            flyCase(gi, target, 0.7f + moved * 0.06f, 0.45f)
            moved++
        }
        sfx(if (moved > 0) "casePutBack" else "ui")
    }
    fun putBackHeld() {
        val gi = heldGame ?: return
        heldGame = null
        inHand[gi] = false
        flyCase(gi, slotOf[gi], 0.5f, 0.2f)
        sfx("casePutBack")
    }
    /** Tap on a case: the empty one beside the console stays put; any other is picked up. */
    fun tapCase(gi: Int) { if (slotOf[gi] != FLOOR_SLOT) pickUpGame(gi) }

    // ---- Disc swap: lid pops, old disc lifts out, new disc arcs in from its case, lid shuts, console boots ----
    private class InsertAnim(val start: Float, val gi: Int, val old: Int) { var stage = 0 }
    private var insertAnim: InsertAnim? = null
    val swapping get() = insertAnim != null
    fun insertHeldGame(t: Float) {
        if (insertAnim != null) return
        val gi = heldGame ?: return
        val old = insertedGame
        heldGame = null
        inHand[gi] = false
        // Swap slots: the new case takes the floor spot, the old (empty) case goes up to the new game's shelf spot
        val shelfSlot = slotOf[gi]
        slotOf[gi] = FLOOR_SLOT; slotOf[old] = shelfSlot
        flyCase(gi, FLOOR_SLOT, 0.9f, 0.35f)
        releaseFromInteractables(caseMeshes[old]!!)
        flyCase(old, shelfSlot, 1.3f, 0.9f)
        insertAnim = InsertAnim(t, gi, old)
        sfx("caseOpen")
    }
    private fun updateInsertAnim(t: Float) {
        val a = insertAnim ?: return
        val e = t - a.start
        val disc = ps1.disc
        val path = ps1.consoles?.discPath()
        if (path != null && !path.lid) { updateSlotInsert(a, t, e, path); return }
        val home = path?.home ?: ps1.discHome
        val ds = path?.scale ?: 1f
        if (a.stage == 0 && e > 0.3f) { a.stage = 1; ps1.setLid(true); sfx("ps1LidOpen") }
        if (a.stage == 1) {
            // Old disc rises out of the tray and shrinks away into its case
            val k = min(1f, max(0f, (e - 0.55f) / 0.45f))
            disc.position.set(home.x - k * 0.3f, home.y + sin(k * PI.toFloat() * 0.8f) * 0.3f, home.z + k * 0.15f)
            val s = (1 - k * 0.9f) * ds; disc.scale.set(s, s, s)
            if (e > 1.0f) { a.stage = 2; ps1.showDiscLabel(discLabelTex(ALL_GAMES[a.gi])); sfx("discOut") }
        }
        if (a.stage == 2) {
            // New disc arcs from its case on the floor into the tray
            val from = slots[FLOOR_SLOT]!!.pos
            val k = min(1f, max(0f, (e - 1.0f) / 0.7f))
            val s = k * k * (3 - 2 * k)
            disc.position.set(from.x + (home.x - from.x) * s, from.y + 0.05f + (home.y - from.y - 0.05f) * s + sin(PI.toFloat() * s) * 0.32f, from.z + (home.z - from.z) * s)
            val sc = (0.4f + 0.6f * s) * ds; disc.scale.set(sc, sc, sc)
            if (k >= 1) { a.stage = 3; disc.position.copy(home); disc.scale.set(ds, ds, ds); sfx("discIn") }
        }
        if (a.stage == 3 && e > 1.95f) { a.stage = 4; ps1.setLid(false); sfx("ps1LidClose") }
        if (a.stage == 4 && e > 2.25f) {
            insertedGame = a.gi; onInserted()
            insertAnim = null
            ps1.gameInserted(t)
        }
    }
    /**
     * Slot-loading consoles (PS3, PS4, PS5): the old disc slides out of the slot and drops into its case, the new
     * one arcs over from its case, lines up with the slot and slides in (out of sight inside the console).
     */
    private fun updateSlotInsert(a: InsertAnim, t: Float, e: Float, p: Consoles.DiscPath) {
        val disc = ps1.disc; val ds = p.scale
        fun smooth(k: Float) = k * k * (3 - 2 * k)
        if (a.stage == 0 && e > 0.25f) { a.stage = 1; disc.visible = true; sfx("ps1LidOpen") }
        if (a.stage == 1) {
            // out of the slot (0.25 - 0.55 s), then up and away into its case (0.55 - 1.0 s)
            val k1 = smooth(min(1f, max(0f, (e - 0.25f) / 0.3f)))
            if (e < 0.55f) { disc.position.set(p.home.x + (p.front.x - p.home.x) * k1, p.home.y, p.home.z + (p.front.z - p.home.z) * k1); disc.scale.set(ds, ds, ds) }
            else {
                val k = min(1f, (e - 0.55f) / 0.45f)
                disc.position.set(p.front.x - k * 0.3f, p.front.y + sin(k * PI.toFloat() * 0.8f) * 0.3f, p.front.z + k * 0.15f)
                val s = (1 - k * 0.9f) * ds; disc.scale.set(s, s, s)
            }
            if (e > 1.0f) { a.stage = 2; ps1.showDiscLabel(discLabelTex(ALL_GAMES[a.gi])); sfx("discOut") }
        }
        if (a.stage == 2) {
            // the new disc arcs from its case to just in front of the slot
            val from = slots[FLOOR_SLOT]!!.pos
            val s = smooth(min(1f, max(0f, (e - 1.0f) / 0.7f)))
            disc.position.set(from.x + (p.front.x - from.x) * s, from.y + 0.05f + (p.front.y - from.y - 0.05f) * s + sin(PI.toFloat() * s) * 0.3f, from.z + (p.front.z - from.z) * s)
            val sc = (0.4f + 0.6f * s) * ds; disc.scale.set(sc, sc, sc)
            if (e >= 1.7f) a.stage = 3
        }
        if (a.stage == 3) {
            // ...and the drive pulls it in
            val k = smooth(min(1f, max(0f, (e - 1.75f) / 0.35f)))
            disc.position.set(p.front.x + (p.home.x - p.front.x) * k, p.front.y, p.front.z + (p.home.z - p.front.z) * k)
            if (k >= 1f) { a.stage = 4; disc.visible = false; sfx("discIn") }
        }
        if (a.stage == 4 && e > 2.25f) {
            insertedGame = a.gi; onInserted()
            insertAnim = null
            ps1.gameInserted(t)
        }
    }
    fun update(t: Float, dt: Float) { updateCaseTweens(t, dt); updateInsertAnim(t) }

    // ---- TV: per-game title screen and gameplay styles ----
    fun currentGame() = ALL_GAMES[insertedGame]
    fun drawTitleScreen(ctx: Canvas2D, w: Int, h: Int, g: Game, e: Float) {
        g.art(ctx, 0.0, w.toDouble(), h.toDouble())
        ctx.fillStyle = "rgba(0, 0, 0, 0.55)"; ctx.fillRect(0, h - 22, w, 22)
        ctx.textAlign = "center"
        if (g.title.startsWith("Grand Theft Auto")) {
            // GTA-style loading bar instead of PRESS START
            ctx.fillStyle = "#1f2937"; ctx.fillRect(w * 0.2, h - 15, w * 0.6, 4)
            ctx.fillStyle = g.accent; ctx.fillRect(w * 0.2, h - 15, w * 0.6 * min(1f, e / 3.8f), 4)
            ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"; ctx.fillText("LOADING", w / 2, h - 18)
        } else if (floor(e * 2.5).toInt() % 2 == 0) {
            ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px monospace"; ctx.fillText("PRESS START", w / 2, h - 11)
        }
        ctx.fillStyle = "#9ca3af"; ctx.font = "5px sans-serif"; ctx.fillText("© ${g.year} ${g.pub}", w / 2, h - 3)
        ctx.textAlign = "left"
    }
    /** drawInsertedGameplay: each template's game loop; also tints the TV's glow with the game's accent. */
    fun drawGameplay(ctx: Canvas2D, w: Int, h: Int, g: Float, t: Float) {
        val game = currentGame()
        when (game.template) {
            "skate" -> drawSkate(ctx, w, h, g, game)
            "glide" -> drawGlide(ctx, w, h, g)
            "rpg" -> drawRpg(ctx, w, h, g)
            "swing" -> drawSwing(ctx, w, h, g)
            "drive" -> drawDrive(ctx, w, h, g)
            "shooter" -> drawShooter(ctx, w, h, g, game)
            "broom" -> drawBroom(ctx, w, h, g)
            "cards" -> drawCards(ctx, w, h, g)
            "gtatop" -> drawGtaTop(ctx, w, h, g, game)
            "gta3d" -> { ctx.fillStyle = "#000"; ctx.fillRect(0, 0, w, h) } // the 3D feed replaces the canvas
            else -> ps1.drawPlatform(ctx, w, h, g, t)
        }
        room.bedroomTvLight.color.setStyle(game.accent)
    }
    private fun circle(ctx: Canvas2D, x: Number, y: Number, r: Number) { ctx.beginPath(); ctx.arc(x, y, r, 0, PI * 2); ctx.fill() }
    private fun drawSkate(ctx: Canvas2D, w: Int, h: Int, g: Float, game: Game) {
        ctx.fillStyle = "#374151"; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#4b5563"; var xx = 0; while (xx < w) { ctx.fillRect(xx, 0, 2, 50); xx += 16 }
        ctx.fillStyle = "#9ca3af"
        ctx.beginPath(); ctx.moveTo(8, 30); ctx.quadraticCurveTo(64, 140, 120, 30); ctx.lineTo(120, 96); ctx.lineTo(8, 96); ctx.closePath(); ctx.fill()
        ctx.strokeStyle = "#e5e7eb"; ctx.lineWidth = 2f
        ctx.beginPath(); ctx.moveTo(8, 30); ctx.quadraticCurveTo(64, 140, 120, 30); ctx.stroke()
        val p = sin(g * 1.6f)
        val x = 64 + p * 50
        val air = max(0f, abs(p) - 0.82f) * 140
        val y = 30 + (1 - p * p) * 52 - air
        Games.skaterSilhouette(ctx, x.toDouble(), (y - 6).toDouble(), 0.8, game.accent)
        if (air > 2) {
            ctx.fillStyle = "#fde047"; ctx.font = "bold 7px Impact, sans-serif"; ctx.textAlign = "center"
            ctx.fillText(arrayOf("KICKFLIP", "540 MCTWIST", "MADONNA", "NOSEGRIND")[floor(g / 2.0).toInt() % 4], x, y - 26)
            ctx.textAlign = "left"
        }
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 7px monospace"
        ctx.fillText((floor(g * 1250.0).toLong() % 999999).toString().padStart(6, '0'), 4, 10)
        ctx.fillText(("${max(0, 120 - floor(g.toDouble()).toInt()) % 120}").padStart(3, ' '), 104, 10)
    }
    private fun drawGlide(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        val sky = ctx.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, "#38bdf8"); sky.addColorStop(1, "#bae6fd")
        ctx.fillStyle = sky; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#16a34a"
        for (i in -1 until 5) { val x = i * 40 - (g * 30) % 40; ctx.beginPath(); ctx.ellipse(x + 20, 92, 30, 22 + (i % 2) * 6, 0, 0, PI * 2); ctx.fill() }
        for (i in 0 until 4) {
            val gx = (i * 37 - g * 40) % (w + 20)
            val x = if (gx < -10) gx + w + 20 else gx
            ctx.fillStyle = arrayOf("#ef4444", "#22c55e", "#3b82f6", "#facc15")[i]
            ctx.beginPath(); ctx.moveTo(x, 50 + i * 5); ctx.lineTo(x + 4, 54 + i * 5); ctx.lineTo(x, 58 + i * 5); ctx.lineTo(x - 4, 54 + i * 5); ctx.fill()
        }
        val y = 46 + sin(g * 2) * 8
        ctx.fillStyle = "#5b21b6"; ctx.beginPath(); ctx.moveTo(40, y); ctx.lineTo(22, y - 12 + sin(g * 12) * 4); ctx.lineTo(30, y + 4); ctx.fill()
        ctx.fillStyle = "#7c3aed"; ctx.beginPath(); ctx.ellipse(40, y + 2, 9, 5, 0, 0, PI * 2); ctx.fill()
        ctx.beginPath(); ctx.ellipse(50, y - 2, 5, 4, 0, 0, PI * 2); ctx.fill()
        ctx.fillStyle = "#facc15"; ctx.fillRect(47, y - 8, 2, 4); ctx.fillRect(51, y - 8, 2, 4)
        ctx.fillStyle = "#fde047"; circle(ctx, 30 + sin(g * 5) * 3, y - 10, 1.5)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 7px monospace"; ctx.fillText("GEMS ${floor(g * 7.0).toInt() % 400}", 4, 10)
    }
    private fun drawRpg(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        val bg = ctx.createLinearGradient(0, 0, 0, h)
        bg.addColorStop(0, "#1e3a8a"); bg.addColorStop(0.6, "#4c1d95"); bg.addColorStop(0.61, "#365314"); bg.addColorStop(1, "#1a2e05")
        ctx.fillStyle = bg; ctx.fillRect(0, 0, w, h)
        val hit = (g % 3) < 0.4f
        ctx.fillStyle = if (hit) "#fca5a5" else "#7f1d1d"
        ctx.beginPath(); ctx.ellipse(30, 44, 16, 14, 0, 0, PI * 2); ctx.fill()
        ctx.fillStyle = "#fde047"; ctx.fillRect(24, 40, 3, 3); ctx.fillRect(33, 40, 3, 3)
        for ((i, c) in arrayOf(Triple(90, 30, "#facc15"), Triple(100, 44, "#60a5fa"), Triple(88, 58, "#f472b6")).withIndex()) {
            val bob = sin(g * 3 + i) * 1.5f
            ctx.fillStyle = c.third; ctx.fillRect(c.first, c.second + bob, 7, 10)
            ctx.fillStyle = "#fde68a"; ctx.fillRect(c.first + 1, c.second - 4 + bob, 5, 5)
        }
        if (hit) { ctx.fillStyle = "#ffffff"; ctx.font = "bold 8px monospace"; ctx.fillText((400 + floor((g * 97.0) % 600).toInt()).toString(), 22, 26 - (g % 3) * 20) }
        ctx.fillStyle = "rgba(30, 58, 138, 0.9)"; ctx.fillRect(2, 70, w - 4, 24)
        ctx.strokeStyle = "#e5e7eb"; ctx.strokeRect(2.5, 70.5, w - 5, 23)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"
        arrayOf("ATTACK", "MAGIC", "ITEM").forEachIndexed { i, s -> ctx.fillText(s, 10, 78 + i * 7) }
        ctx.fillText("►", 4, 78 + (floor(g.toDouble()).toInt() % 3) * 7)
        arrayOf("ZID 812", "VIV 455", "GAR 630").forEachIndexed { i, s -> ctx.fillText(s, 70, 78 + i * 7) }
    }
    private fun drawSwing(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        ctx.fillStyle = "#0f172a"; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#1e293b"
        for (i in 0 until 9) {
            val x = ((i * 18 - g * 25) % (w + 18) + w + 18) % (w + 18) - 18
            val bh = 40 + ((i * 29) % 40)
            ctx.fillRect(x, h - bh, 16, bh)
            ctx.fillStyle = "#fde047"
            var y = h - bh + 4; while (y < h) { ctx.fillRect(x + 3 + ((y + i) % 2) * 6, y, 2, 2); y += 7 }
            ctx.fillStyle = "#1e293b"
        }
        val ang = sin(g * 2.2f) * 0.9f
        val ax = 64f; val ay = -10f; val len = 58f
        val hx = ax + sin(ang) * len; val hy = ay + cos(ang) * len
        ctx.strokeStyle = "#e5e7eb"; ctx.lineWidth = 1f
        ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(hx, hy); ctx.stroke()
        ctx.fillStyle = "#dc2626"; ctx.fillRect(hx - 3, hy, 6, 8)
        ctx.fillStyle = "#1d4ed8"; ctx.fillRect(hx - 3, hy + 8, 6, 6)
        ctx.fillStyle = "#dc2626"; circle(ctx, hx, hy - 2, 3)
    }
    private fun drawDrive(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        ctx.fillStyle = "#0b1530"; ctx.fillRect(0, 0, w, 44)
        ctx.fillStyle = "#334155"
        ctx.beginPath(); ctx.moveTo(52, 44); ctx.lineTo(76, 44); ctx.lineTo(128, 96); ctx.lineTo(0, 96); ctx.fill()
        ctx.fillStyle = "#1e293b"
        for (i in 0 until 6) {
            val z = ((i * 0.2f + g * 0.6f) % 1)
            val s = 0.2f + z * 1.6f
            ctx.fillRect(52 - s * 34, 44 - s * 22, s * 18, s * 22)
            ctx.fillRect(76 + s * 16, 44 - s * 22, s * 18, s * 22)
        }
        ctx.strokeStyle = "#fde047"; ctx.lineWidth = 2f
        for (d in 0 until 4) { val y = 46 + ((d * 13 + g * 40) % 50); ctx.beginPath(); ctx.moveTo(64, y); ctx.lineTo(64, y + 5); ctx.stroke() }
        val cx = 64 + sin(g * 1.8f) * 16
        ctx.fillStyle = "#b91c1c"; ctx.fillRect(cx - 14, 68, 28, 12)
        ctx.fillStyle = "#0f172a"; ctx.fillRect(cx - 10, 62, 20, 7)
        ctx.fillStyle = if (sin(g * 14) > 0) "#ef4444" else "#3b82f6"
        ctx.fillRect(cx - 4 + sin(g) * 10, 50, 8, 3)
        ctx.fillStyle = "#ffffff"; ctx.font = "bold 6px monospace"; ctx.fillText("FELONY", 4, 8)
        ctx.fillStyle = "#dc2626"; ctx.fillRect(34, 3, 30 + sin(g) * 10, 5)
    }
    private fun drawShooter(ctx: Canvas2D, w: Int, h: Int, g: Float, game: Game) {
        val s = game.shooter!!
        ctx.fillStyle = s.floor; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = s.wall
        ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(44, 30); ctx.lineTo(44, 66); ctx.lineTo(0, 96); ctx.fill()
        ctx.beginPath(); ctx.moveTo(128, 0); ctx.lineTo(84, 30); ctx.lineTo(84, 66); ctx.lineTo(128, 96); ctx.fill()
        ctx.fillStyle = "rgba(0, 0, 0, 0.5)"; ctx.fillRect(44, 30, 40, 36)
        val ex = 64 + sin(g * 1.4f) * 14
        ctx.fillStyle = s.foe
        if (s.dino) {
            ctx.beginPath(); ctx.ellipse(ex, 52, 12, 7, 0, 0, PI * 2); ctx.fill()
            ctx.fillRect(ex + 6, 42, 10, 6)
            ctx.fillStyle = "#facc15"; ctx.fillRect(ex + 12, 43, 2, 2)
        } else { circle(ctx, ex, 40, 4); ctx.fillRect(ex - 4, 44, 8, 16) }
        val fire = sin(g * 9) > 0.7f
        if (fire) { ctx.fillStyle = "#fde68a"; circle(ctx, 70, 70, 6) }
        ctx.fillStyle = "#111827"
        ctx.beginPath(); ctx.moveTo(66, 72 + (if (fire) 2 else 0)); ctx.lineTo(90, 96); ctx.lineTo(108, 96); ctx.lineTo(74, 70 + (if (fire) 2 else 0)); ctx.fill()
        ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 1f
        ctx.beginPath(); ctx.moveTo(64, 44); ctx.lineTo(64, 50); ctx.moveTo(61, 47); ctx.lineTo(67, 47); ctx.stroke()
        ctx.fillStyle = s.tint; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#fef3c7"; ctx.font = "bold 6px monospace"; ctx.fillText("HEALTH ${100 - floor(g * 3.0).toInt() % 60}", 4, 92)
    }
    private fun drawBroom(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        val sky = ctx.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, "#1e3a8a"); sky.addColorStop(1, "#f59e0b")
        ctx.fillStyle = sky; ctx.fillRect(0, 0, w, h)
        ctx.fillStyle = "#0f172a"
        ctx.beginPath(); ctx.moveTo(0, 96); ctx.lineTo(0, 78); ctx.lineTo(20, 78); ctx.lineTo(24, 58); ctx.lineTo(28, 78); ctx.lineTo(60, 78); ctx.lineTo(66, 50); ctx.lineTo(72, 78); ctx.lineTo(128, 78); ctx.lineTo(128, 96); ctx.fill()
        val r = (g * 0.5f) % 1
        ctx.strokeStyle = "#fbbf24"; ctx.lineWidth = 1 + r * 2
        ctx.beginPath(); ctx.ellipse(64, 40, 4 + r * 30, 6 + r * 34, 0, 0, PI * 2); ctx.stroke()
        val sx = 64 + sin(g * 3) * 30; val sy = 30 + cos(g * 4) * 10
        ctx.fillStyle = "#fde047"; circle(ctx, sx, sy, 1.5)
        val by = 56 + sin(g * 1.7f) * 4
        ctx.strokeStyle = "#7c2d12"; ctx.lineWidth = 2f
        ctx.beginPath(); ctx.moveTo(48, by + 6); ctx.lineTo(80, by + 2); ctx.stroke()
        ctx.fillStyle = "#111827"; ctx.fillRect(58, by - 8, 7, 10)
        ctx.fillStyle = "#fde68a"; circle(ctx, 61, by - 10, 3)
        ctx.fillStyle = "#7f1d1d"; ctx.fillRect(56, by - 4, 3, 6)
    }
    private fun drawCards(ctx: Canvas2D, w: Int, h: Int, g: Float) {
        ctx.fillStyle = "#1e1b4b"; ctx.fillRect(0, 0, w, h)
        ctx.strokeStyle = "#a78bfa"; ctx.lineWidth = 1f
        for (r in 0 until 2) for (c in 0 until 5) ctx.strokeRect(10 + c * 22, 12 + r * 48, 18, 24)
        val flip = abs(sin(g * 0.8f))
        arrayOf(0 to 1, 2 to 0, 3 to 1, 1 to 0).forEachIndexed { i, (c, r) ->
            val x = 10 + c * 22; val y = 12 + r * 48
            val ws = if (i == 0) flip else 1f
            ctx.fillStyle = if (i % 2 == 1) "#a16207" else "#7c2d12"
            ctx.fillRect(x + 9 - 9 * ws, y, 18 * ws, 24)
            ctx.fillStyle = "#fef3c7"; ctx.fillRect(x + 11 - 9 * ws, y + 3, 14 * ws, 10)
        }
        if ((g % 4) < 0.5f) { ctx.fillStyle = "rgba(253, 224, 71, 0.6)"; ctx.fillRect(0, 0, w, h) }
        ctx.fillStyle = "#facc15"; ctx.font = "bold 6px monospace"
        ctx.fillText("LP ${8000 - floor(g / 4.0).toInt() * 500 % 8000}", 4, 8)
        ctx.fillText("LP ${8000 - floor(g / 5.0).toInt() * 700 % 8000}", 84, 94)
    }

    // ---- GTA top-down era (GTA, London 1969, GTA 2, Chinatown Wars): a scrolling city drawn in 2D ----
    private class TopTheme(val ground: String, val road: String, val line: String, val roof: Array<String>, val car: String, val cop: Boolean, val hud: String, val tint: String?,
                           val bus: Boolean = false, val neon: Boolean = false, val cel: Boolean = false)
    private val TOP_THEMES = mapOf(
        "gta1" to TopTheme("#5b5f45", "#3a3b3f", "#e5d36a", arrayOf("#7c6f5a", "#8a8f99", "#6b5b4a", "#a08a76"), "#dc2626", true, "#facc15", null),
        "london" to TopTheme("#4b5a3e", "#2f3134", "#f5f5f4", arrayOf("#6b4f3a", "#7a6a5a", "#5c5045", "#8b3a2e"), "#0f5132", true, "#f5f5f4", "rgba(120, 100, 70, 0.12)", bus = true),
        "gta2" to TopTheme("#1f1638", "#2a2342", "#22d3ee", arrayOf("#3b2a6b", "#4c1d95", "#1e3a5f", "#5b21b6"), "#f97316", true, "#22d3ee", "rgba(80, 0, 120, 0.12)", neon = true),
        "ctw" to TopTheme("#2b2b2b", "#1c1c1c", "#facc15", arrayOf("#dc2626", "#f59e0b", "#16a34a", "#2563eb"), "#facc15", true, "#ffffff", null, cel = true)
    )
    private fun drawGtaTop(ctx: Canvas2D, w: Int, h: Int, g: Float, game: Game) {
        val th = TOP_THEMES[game.topTheme] ?: TOP_THEMES.getValue("gta1")
        val scroll = g * 34
        val block = 46f
        ctx.fillStyle = th.ground; ctx.fillRect(0, 0, w, h)
        val off = scroll % block
        // Avenue running up the middle, cross streets every block
        ctx.fillStyle = th.road
        ctx.fillRect(w / 2 - 14, 0, 28, h)
        var y = -block + off; while (y < h + block) { ctx.fillRect(0, y, w, 14); y += block }
        // Rooftops with a bit of shading, AC units and lit windows in the neon future
        val startIdx = floor(scroll / block.toDouble()).toInt()
        y = -block + off; var row = 0
        while (y < h + block) {
            val idx = startIdx - row
            for ((side, xs) in arrayOf(floatArrayOf(2f, w / 2f - 18), floatArrayOf(w / 2f + 16, w - 2f)).withIndex()) {
                val seed = abs(idx * 7 + side * 3)
                val col = th.roof[seed % th.roof.size]
                val bx = xs[0] + 2; val by = y + 16; val bw = xs[1] - xs[0] - 4; val bh = block - 20
                ctx.fillStyle = "rgba(0,0,0,0.35)"; ctx.fillRect(bx + 2, by + 2, bw, bh)
                ctx.fillStyle = col; ctx.fillRect(bx, by, bw, bh)
                ctx.fillStyle = "rgba(255,255,255,0.12)"; ctx.fillRect(bx, by, bw, 2)
                ctx.fillStyle = "rgba(0,0,0,0.25)"
                ctx.fillRect(bx + 4 + (seed % 9), by + 5, 6, 4)
                ctx.fillRect(bx + bw - 12, by + bh - 9 - (seed % 5), 7, 5)
                if (th.neon) { ctx.fillStyle = if (seed % 2 == 1) "#22d3ee" else "#f472b6"; ctx.fillRect(bx, by + bh - 1, bw, 1) }
                if (th.cel) { ctx.strokeStyle = "#000"; ctx.lineWidth = 1f; ctx.strokeRect(bx + 0.5, by + 0.5, bw - 1, bh - 1) }
            }
            y += block; row++
        }
        // Lane markings
        ctx.fillStyle = th.line
        var ly = -12 + (scroll % 12); while (ly < h) { ctx.fillRect(w / 2 - 0.5, ly, 1, 6); ly += 12 }
        // Pedestrians strolling on the sidewalks
        for (i in 0 until 10) {
            val py = ((i * 23 + scroll * (0.6f + (i % 3) * 0.1f)) % (h + 10)) - 5
            val px = if (i % 2 == 1) w / 2 - 17 + (i % 3) else w / 2 + 15 + (i % 3)
            ctx.fillStyle = arrayOf("#fca5a5", "#93c5fd", "#fde68a", "#c4b5fd")[i % 4]
            ctx.fillRect(px, py, 2, 2)
        }
        // Traffic going the same way (we overtake) and the other way
        fun drawCar(x: Float, yy: Float, col: String, ang: Float, cop: Boolean) {
            ctx.save(); ctx.translate(x, yy); ctx.rotate(ang)
            ctx.fillStyle = "rgba(0,0,0,0.35)"; ctx.fillRect(-3, -5, 7, 11)
            ctx.fillStyle = col; ctx.fillRect(-3.5, -6, 7, 12)
            ctx.fillStyle = "rgba(20, 30, 50, 0.85)"; ctx.fillRect(-2.5, -3.5, 5, 3); ctx.fillRect(-2.5, 2, 5, 2)
            if (cop) { ctx.fillStyle = if (sin(g * 14) > 0) "#ef4444" else "#3b82f6"; ctx.fillRect(-3, -1, 6, 1.5) }
            if (th.cel) { ctx.strokeStyle = "#000"; ctx.lineWidth = 0.6f; ctx.strokeRect(-3.5, -6, 7, 12) }
            ctx.restore()
        }
        for (i in 0 until 4) {
            val ty = ((i * 31 - g * 22) % (h + 30) + h + 30) % (h + 30) - 15
            drawCar(w / 2f + 7, ty, arrayOf("#94a3b8", "#16a34a", "#f8fafc", "#7c3aed")[i], 0f, false)
            if (th.bus && i == 1) { ctx.fillStyle = "#b91c1c"; ctx.fillRect(w / 2 + 3, ty - 30, 8, 18) }
        }
        for (i in 0 until 3) {
            val ty = ((i * 41 + g * 60) % (h + 30)) - 15
            drawCar(w / 2f - 7, ty, arrayOf("#eab308", "#0ea5e9", "#e11d48")[i], PI.toFloat(), false)
        }
        // Player weaving through traffic with the cops on our tail
        val px = w / 2f + 7 + sin(g * 1.3f) * 5 - (if (sin(g * 0.35f) > 0.6f) 14 else 0)
        val wanted = 1 + floor((g / 6.0) % 5).toInt()
        if (th.cop) drawCar(px + sin(g * 1.3f - 0.6f) * 3, h * 0.62f + 22, "#f8fafc", sin(g * 1.3f - 0.5f) * 0.25f, true)
        drawCar(px, h * 0.62f, th.car, cos(g * 1.3f) * 0.25f, false)
        if (th.tint != null) { ctx.fillStyle = th.tint; ctx.fillRect(0, 0, w, h) }
        // HUD: score / money, multiplier, wanted heads or stars
        ctx.fillStyle = "rgba(0,0,0,0.55)"; ctx.fillRect(0, 0, w, 11)
        ctx.fillStyle = th.hud; ctx.font = "bold 7px monospace"; ctx.textAlign = "left"
        ctx.fillText(if (game.topTheme == "ctw") "\$" + (floor(g * 911.0).toLong() % 1000000).toString().padStart(6, '0') else (floor(g * 1500.0).toLong() % 10000000).toString().padStart(7, '0'), 3, 8)
        ctx.fillText("x${1 + floor(g / 9.0).toInt() % 4}", 48, 8)
        for (i in 0 until 5) {
            ctx.fillStyle = if (i < wanted) (if (sin(g * 6) > 0) "#ef4444" else "#3b82f6") else "rgba(255,255,255,0.2)"
            circle(ctx, 84 + i * 9, 5.5, 3)
        }
        if (game.topTheme == "london") { ctx.fillStyle = "#f5f5f4"; ctx.font = "bold 5px monospace"; ctx.fillText("LONDON 1969", 3, h - 3) }
        if (game.topTheme == "gta2") { ctx.fillStyle = "#22d3ee"; ctx.font = "bold 5px monospace"; ctx.fillText("RESPECT  ZAIBATSU ▮▮▮▯  LOONIES ▮▯▯▯", 3, h - 3) }
    }
}
