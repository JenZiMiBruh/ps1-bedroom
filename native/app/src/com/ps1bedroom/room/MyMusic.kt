package com.ps1bedroom.room

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.ps1bedroom.room.engine.Canvas2D
import com.ps1bedroom.room.engine.CanvasTex
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Material
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import com.ps1bedroom.room.engine.createCanvasTex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HTML 13297-14127: MY MUSIC + THE BOOMBOX PLAYER. Your own songs and albums (with their cover art) from this
 * device. Natively the files stay where they are: Android keeps the read permission, the tags and covers are read
 * with MediaMetadataRetriever, and the library (plus 512 px covers) is kept in the app's own storage. Nothing is
 * uploaded anywhere. Playback streams through the boombox speaker like the radio (StreamSource).
 */
class MyMusic(private val app: App, val ctx: Context) {
    class Track(val id: Long, val uri: String, val name: String, var title: String, var artist: String, val albumArtist: String,
                var album: String, val track: Int, val disc: Int, val artKey: String, val dir: String, val addedAt: Long)
    class Album(val key: String, val title: String, var artist: String, val tracks: ArrayList<Track>)

    val MY_MUSIC_STATION = 3
    @Volatile var tracks: List<Track> = emptyList(); private set
    private val dataFile = File(ctx.filesDir, "mymusic.json")
    private val artDir = File(ctx.filesDir, "art").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)

    // ---- Player state (MP) ----
    var queue: List<Track> = emptyList(); private set
    var pos = 0; private set
    var shuffle = false; private set
    var repeat = "all"; private set
    var paused = false; private set
    var current: Track? = null; private set
    var lastSource = "music"; private set
    private var lastFmStation = 1
    /** Changes for the boombox panel / menu (UI thread listens). */
    var onChanged: (() -> Unit)? = null

    // ---------------- Library ----------------
    fun load() {
        tracks = try {
            if (!dataFile.exists()) emptyList() else JSONArray(dataFile.readText()).let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { o ->
                    Track(o.getLong("id"), o.getString("uri"), o.optString("name"), o.optString("title"), o.optString("artist"), o.optString("albumArtist"),
                        o.optString("album", "Singles"), o.optInt("track"), o.optInt("disc"), o.optString("artKey"), o.optString("dir"), o.optLong("addedAt"))
                } }
            }
        } catch (e: Exception) { emptyList() }
        // First load: cue up the last song you played (paused), so its cover is already sitting on the dresser
        if (current == null && tracks.isNotEmpty()) {
            val id = prefs.getLong("lastTrack", -1)
            val all = albums().flatMap { it.tracks }
            all.firstOrNull { it.id == id }?.let { last -> queue = all; pos = all.indexOf(last); paused = true; mpLoad(last, false) }
        }
        changed()
    }
    private fun save() {
        val a = JSONArray()
        for (t in tracks) a.put(JSONObject().apply {
            put("id", t.id); put("uri", t.uri); put("name", t.name); put("title", t.title); put("artist", t.artist); put("albumArtist", t.albumArtist)
            put("album", t.album); put("track", t.track); put("disc", t.disc); put("artKey", t.artKey); put("dir", t.dir); put("addedAt", t.addedAt)
        })
        dataFile.writeText(a.toString())
    }
    fun albums(): List<Album> {
        val map = LinkedHashMap<String, Album>()
        for (t in tracks) map.getOrPut(t.artKey) { Album(t.artKey, t.album.ifEmpty { "Singles" }, t.albumArtist.ifEmpty { t.artist }, ArrayList()) }.tracks += t
        val albums = map.values.toList()
        for (a in albums) {
            a.tracks.sortWith(compareBy<Track> { it.disc }.thenBy { if (it.track == 0) 999 else it.track }.thenBy { it.title })
            val artists = a.tracks.map { it.artist }.filter { it.isNotEmpty() }.toSet()
            if (a.artist.isEmpty()) a.artist = if (artists.size > 1) "Various Artists" else artists.firstOrNull() ?: ""
        }
        return albums.sortedWith(compareBy<Album> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    }
    fun statusText(): String {
        val al = albums()
        return if (tracks.isNotEmpty()) "${tracks.size} song${if (tracks.size > 1) "s" else ""} in ${al.size} album${if (al.size > 1) "s" else ""}: ${al.take(3).joinToString(", ") { it.title }}${if (al.size > 3) "…" else ""}"
        else "No songs yet. Add songs or a whole album folder from your device."
    }
    private fun artFile(key: String) = File(artDir, "${key.hashCode().toUInt()}.jpg")
    private val artCache = HashMap<String, Bitmap?>()
    /** onTrimMemory: covers are re-read from disk when needed. */
    fun trim() { artCache.clear() }
    fun art(key: String): Bitmap? = artCache.getOrPut(key) { artFile(key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }

    // ---------------- Importing: songs, or whole album folders ----------------
    class Item(val uri: Uri, val name: String, val path: String, val mime: String)
    private fun isAudioName(n: String) = Regex("""\.(mp3|m4a|aac|mp4|flac|ogg|oga|opus|wav|wma|webm|3gp|amr)$""", RegexOption.IGNORE_CASE).containsMatchIn(n)
    private fun isImageName(n: String) = Regex("""\.(jpe?g|png|webp|gif|bmp)$""", RegexOption.IGNORE_CASE).containsMatchIn(n)
    /** "03 - Artist - Song.mp3" → track 3, artist, title. */
    private fun guessFromName(name: String): Triple<Int, String?, String> {
        var base = name.replace(Regex("""\.[^.]+$"""), "").replace('_', ' ').trim()
        var track = 0
        Regex("""^(\d{1,3})[\s.\-_)]+(.*)$""").find(base)?.let { track = it.groupValues[1].toInt(); base = it.groupValues[2].trim() }
        val parts = base.split(Regex("""\s+-\s+"""))
        return if (parts.size >= 2) Triple(track, parts[0], parts.drop(1).joinToString(" - ")) else Triple(track, null, base)
    }
    @Volatile private var importing = false
    /** Takes the read permission so the songs still play after a restart. */
    fun takePermission(uri: Uri) { try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { } }
    /** The files from a folder tree, with paths like "Folder/Sub/file.mp3". */
    fun listTree(tree: Uri): List<Item> {
        val out = ArrayList<Item>()
        fun walk(docId: String, path: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: ""; val mime = c.getString(2) ?: ""
                    val p = if (path.isEmpty()) name else "$path/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, p)
                    else out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, p, mime)
                }
            }
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = rootId.substringAfterLast(':').substringAfterLast('/')
        walk(rootId, rootName)
        return out
    }
    /** importMusic (runs on its own thread; toasts as it goes). */
    fun import(items: List<Item>) {
        if (importing) { app.toast("🎵 Still adding the last batch..."); return }
        importing = true
        Thread {
            try {
                fun dirOf(p: String) = if (p.contains('/')) p.substring(0, p.lastIndexOf('/')) else ""
                val audio = items.filter { it.mime.startsWith("audio/") || isAudioName(it.name) }
                val images = items.filter { it.mime.startsWith("image/") || isImageName(it.name) }
                // Folder pictures: cover.jpg / folder.jpg / front.jpg first, otherwise any picture in the folder
                val folderArt = HashMap<String, Pair<Int, Uri>>()
                for (im in images) {
                    val d = dirOf(im.path); val rank = if (Regex("^(cover|folder|front|albumart)", RegexOption.IGNORE_CASE).containsMatchIn(im.name)) 2 else 1
                    if ((folderArt[d]?.first ?: 0) < rank) folderArt[d] = rank to im.uri
                }
                val added = ArrayList<Track>()
                var nextId = (tracks.maxOfOrNull { it.id } ?: 0) + 1
                audio.forEachIndexed { idx, it ->
                    if (audio.size > 2) app.toast("🎵 Adding songs ${idx + 1} / ${audio.size}...")
                    val mmr = MediaMetadataRetriever()
                    var title: String? = null; var artist: String? = null; var albumArtist: String? = null; var album: String? = null; var trackN = 0; var discN = 0; var pic: ByteArray? = null
                    try {
                        mmr.setDataSource(ctx, it.uri)
                        title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        trackN = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
                        discN = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
                        pic = mmr.embeddedPicture
                    } catch (e: Exception) { } finally { try { mmr.release() } catch (e: Exception) { } }
                    val g = guessFromName(it.name)
                    val dir = dirOf(it.path)
                    val folderName = if (dir.isNotEmpty()) dir.substringAfterLast('/') else ""
                    val alb = album?.ifEmpty { null } ?: folderName.ifEmpty { "Singles" }
                    val aArtist = albumArtist ?: ""
                    val art = artist?.ifEmpty { null } ?: g.second ?: aArtist
                    // One picture per album: embedded cover art wins, then the folder's picture
                    val artKey = "${(aArtist.ifEmpty { art }).lowercase()}|${alb.lowercase()}"
                    takePermission(it.uri)
                    val t = Track(nextId++, it.uri.toString(), it.name.replace(Regex("""\.[^.]+$"""), ""), title?.ifEmpty { null } ?: g.third, art, aArtist, alb,
                        if (trackN > 0) trackN else g.first, discN, artKey, dir, System.currentTimeMillis())
                    if (!artFile(artKey).exists()) {
                        val bmp = pic?.let { p -> BitmapFactory.decodeByteArray(p, 0, p.size) }
                            ?: folderArt[dir]?.second?.let { u -> try { ctx.contentResolver.openInputStream(u)?.use { s -> BitmapFactory.decodeStream(s) } } catch (e: Exception) { null } }
                        if (bmp != null) shrinkArt(bmp, artFile(artKey))
                    }
                    added += t
                }
                app.post {
                    tracks = tracks + added
                    save(); artCache.clear()
                    changed()
                    if (audio.isNotEmpty()) app.toast("🎵 Added ${audio.size} song${if (audio.size > 1) "s" else ""}. They're on the boombox under MY MUSIC.")
                    else if (items.isNotEmpty()) app.toast("🎵 No songs found in there")
                }
            } finally { importing = false }
        }.start()
    }
    /** Shrink cover pictures to 512 px JPEGs. */
    private fun shrinkArt(src: Bitmap, out: File) {
        val s = min(1f, 512f / max(src.width, src.height))
        val b = if (s < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true) else src
        FileOutputStream(out).use { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    }
    fun removeAlbum(a: Album) {
        if (a.tracks.contains(current)) { current = null; queue = emptyList(); paused = true; app.sound.musicStream?.unload(); mpSync(); nowPlayingChanged(true) }
        val ids = a.tracks.map { it.id }.toSet()
        tracks = tracks.filter { it.id !in ids }
        artFile(a.key).delete(); artCache.remove(a.key)
        save(); changed()
    }
    fun clear() {
        tracks = emptyList(); save()
        artDir.listFiles()?.forEach { it.delete() }; artCache.clear()
        current = null; queue = emptyList(); app.sound.musicStream?.unload()
        if (app.extras.radioStation == MY_MUSIC_STATION) setRadioStation(0)
        nowPlayingChanged(true); changed()
        app.toast("🎵 MY MUSIC cleared")
    }

    // ---------------- The player ----------------
    private fun mpLoad(track: Track, autoplay: Boolean) {
        current = track
        app.sound.musicStream?.load(Uri.parse(track.uri))
        if (autoplay) paused = false
        mpSync()
        nowPlayingChanged(!autoplay)
        prefs.edit().putLong("lastTrack", track.id).apply()
    }
    fun playList(list: List<Track>, startIdx: Int = 0, shuf: Boolean = shuffle) {
        if (list.isEmpty()) return
        shuffle = shuf
        val first = list.getOrElse(startIdx) { list[0] }
        val q = list.toMutableList()
        if (shuf) { q.shuffle(); q.remove(first); q.add(0, first) }
        queue = q; pos = q.indexOf(first)
        tuneMyMusic()
        mpLoad(first, true)
    }
    fun step(dir: Int, auto: Boolean = false) {
        if (queue.isEmpty()) { if (tracks.isNotEmpty()) playList(albums().flatMap { it.tracks }); return }
        val s = app.sound.musicStream
        if (auto && repeat == "one") { s?.seek(0.0); mpSync(); return }
        // "Previous" restarts the song if it's a few seconds in, like every real player
        if (dir < 0 && (s?.positionSec ?: 0.0) > 4) { s?.seek(0.0); return }
        var p = pos + dir
        if (p >= queue.size) { if (repeat == "off" && auto) { paused = true; mpSync(); changed(); return }; p = 0 }
        if (p < 0) p = queue.size - 1
        pos = p
        mpLoad(queue[p], true)
    }
    fun shouldPlay() = app.sound.ready && app.sound.playing && app.extras.radioStation == MY_MUSIC_STATION && !paused && current != null
    /** Plays only while tuned to MY MUSIC, not paused, and the room's sound is on. */
    fun mpSync() { app.sound.musicStream?.playing = shouldPlay() }
    private fun tuneMyMusic() {
        lastSource = "music"
        if (app.extras.radioStation != MY_MUSIC_STATION) {
            app.extras.setStation(MY_MUSIC_STATION, app.furniture.radioDisplayMat)
            app.sfx("radioTune")
            app.sound.updateLayered()
            app.settings.changed()
        }
    }
    fun setRadioStation(st: Int) {
        if (st != 0 && st != MY_MUSIC_STATION) { lastFmStation = st; lastSource = "fm" }
        if (st == MY_MUSIC_STATION) lastSource = "music"
        app.extras.setStation(st, app.furniture.radioDisplayMat)
        app.sfx(if (st != 0) "radioTune" else "ui")
        app.sound.updateLayered()
        mpSync()
        app.settings.changed()
        if (st != 0 && st != MY_MUSIC_STATION) app.toast("📻 ${app.extras.RADIO_STATIONS[st]}")
        changed()
    }
    /** Shared by the boombox buttons, WinBeats and the notification. */
    var boomboxTab = "music"
    fun mediaControl(cmd: String, boomboxOpen: Boolean = false) {
        val st = app.extras.radioStation
        val onMusic = st == MY_MUSIC_STATION
        val starting = st == 0 && (cmd == "play" || cmd == "toggle")
        // From silence, play picks up what you were on: the open boombox tab, otherwise the last source used
        val wantMusic = onMusic || (starting && tracks.isNotEmpty() && (if (boomboxOpen) boomboxTab != "fm" else lastSource == "music"))
        if (wantMusic) {
            val wake = { paused = false; if (!app.sound.playing) app.toggleAudio() }
            when (cmd) {
                "play" -> { tuneMyMusic(); wake() }
                "pause" -> paused = true
                "toggle" -> if (onMusic && !paused) paused = true else { tuneMyMusic(); wake() }
                "next" -> step(1)
                "prev" -> step(-1)
                "stop" -> { paused = true; setRadioStation(0) }
            }
            if (current == null && tracks.isNotEmpty()) playList(albums().flatMap { it.tracks })
            mpSync()
        } else {
            // FM: next / previous change station, play / pause turns the radio on and off
            val fm = listOf(1, 2)
            when (cmd) {
                "next", "prev" -> { val i = fm.indexOf(st); setRadioStation(fm[if (i < 0) 0 else (i + (if (cmd == "next") 1 else -1) + fm.size) % fm.size]) }
                "stop", "pause" -> setRadioStation(0)
                "play", "toggle" -> setRadioStation(if (st != 0) 0 else lastFmStation)
            }
        }
        changed()
    }
    fun toggleShuffle() {
        shuffle = !shuffle
        current?.let { cur ->
            if (queue.isNotEmpty()) {
                val base = albums().flatMap { it.tracks }.filter { it in queue }
                val q = if (shuffle) base.shuffled().toMutableList() else base.toMutableList()
                q.remove(cur); if (shuffle) q.add(0, cur) else q.add(base.indexOf(cur), cur)
                queue = q; pos = q.indexOf(cur)
            }
        }
        app.toast(if (shuffle) "🔀 Shuffle on" else "🔀 Shuffle off"); changed()
    }
    fun cycleRepeat() {
        repeat = mapOf("all" to "one", "one" to "off", "off" to "all").getValue(repeat)
        app.toast(mapOf("all" to "🔁 Repeat all", "one" to "🔂 Repeat this song", "off" to "➡️ Repeat off").getValue(repeat)); changed()
    }
    fun seekFraction(f: Double) { val s = app.sound.musicStream ?: return; if (current != null && !s.durationSec.isNaN()) s.seek(f * s.durationSec) }
    /** The sound engine was (re)built: cue the current song on its new stream. */
    fun reattach() { current?.let { app.sound.musicStream?.load(Uri.parse(it.uri)) }; mpSync() }
    fun ended() = step(1, true)
    fun error() { current?.let { app.toast("📻 Can't play \"${it.title}\" on this device") }; step(1, true) }
    private fun changed() { onChanged?.invoke() }

    // ---------------- In the room: cassette window, jewel case, the LCD, the album poster ----------------
    private lateinit var cassetteObj: CanvasTex
    private lateinit var cassetteMat: Material
    private lateinit var jewelObj: CanvasTex
    private lateinit var jewelCase: Group
    private lateinit var lcdObj: CanvasTex
    var nowArt: Bitmap? = null; private set
    /** HTML 13789-13822 (grime cursor 355). */
    fun buildRoomParts() {
        cassetteObj = createCanvasTex(64, 64) { _, _, _ -> }
        cassetteMat = PS1.material(color = 0xffffff, map = cassetteObj.tex, emissive = 0xffffff, emissiveMap = cassetteObj.tex, emissiveIntensity = 0.25f)
        jewelObj = createCanvasTex(128, 128) { _, _, _ -> }
        jewelCase = Group()
        jewelCase.position.set(0.17f, 1.43f, 0.52f); jewelCase.scale.set(1.3f, 1.3f, 1.3f)
        jewelCase.rotation.set(0f, (PI / 2 + 0.35).toFloat(), 0f, "YXZ")
        app.furniture.dresserGroupRef.add(jewelCase)
        val jewelTilt = Group(); jewelTilt.rotation.x = -0.22f; jewelCase.add(jewelTilt)
        PS1.syncGrime(355)
        val jewelDark = PS1.material(color = 0x1b1d22)
        val jewelCover = PS1.material(color = 0xffffff, map = jewelObj.tex, emissive = 0xffffff, emissiveMap = jewelObj.tex, emissiveIntensity = 0.12f)
        val back = Mesh(Geo.box(0.2f, 0.18f, 0.014f), listOf(jewelDark, jewelDark, jewelDark, jewelDark, jewelCover, jewelDark)); back.position.set(0f, 0.09f, -0.008f); jewelTilt.add(back)
        val spine = Mesh(Geo.box(0.016f, 0.18f, 0.026f), PS1.material(color = 0x0b0c0f)); spine.position.set(-0.104f, 0.09f, -0.006f); jewelTilt.add(spine)
        val foot = Mesh(Geo.box(0.2f, 0.012f, 0.05f), PS1.material(color = 0x1b1d22)); foot.position.set(0f, 0.006f, -0.02f); jewelCase.add(foot)
        for (m in listOf(back, spine, foot)) { m.castShadow = true; m.receiveShadow = true; app.room.registerInteractable(m, "bbscreen") }
        jewelCase.visible = false
        lcdObj = createCanvasTex(96, 24) { _, _, _ -> }
        val lcdMat = PS1.material(color = 0x0b1a10, map = lcdObj.tex, emissive = 0xffffff, emissiveMap = lcdObj.tex, emissiveIntensity = 0.9f)
        app.furniture.radioDisplay.materials[0] = lcdMat // the scrolling song name is the display's own front face
        // The album poster option
        app.extras.POSTERS["album"] = { c, w, h ->
            c.fillStyle = "#0d0d12"; c.fillRect(0, 0, w, h)
            val a = nowArt
            if (a != null) c.drawImage(a, (w - h) / 2, 0, h, h)
            else { c.fillStyle = "#f97316"; c.font = "bold 12px monospace"; c.textAlign = "center"; c.fillText("NOW PLAYING", w / 2, h / 2 - 4); c.font = "8px monospace"; c.fillText("play an album on the boombox", w / 2, h / 2 + 10) }
        }
        app.extras.POSTER_NAMES["album"] = "Now playing (album cover)"
    }
    fun dynamicRoots() = listOf(jewelCase)
    private fun nowPlayingChanged(quiet: Boolean) {
        val t = current
        nowArt = t?.let { art(it.artKey) }
        updateCassetteArt()
        if (t != null && !quiet) app.toast("📻 ${t.title}${if (t.artist.isNotEmpty()) " · ${t.artist}" else ""}")
        app.onNowPlaying?.invoke(t?.title, t?.artist, nowArt)
        changed()
    }
    private fun updateCassetteArt() {
        val img = nowArt
        val f = app.furniture
        if (current == null || img == null) {
            f.cassetteDeck.materials[0] = f.cassetteDeckMat
            jewelCase.visible = false
        } else {
            // crop the square cover to the cassette window's wide shape
            val s = min(img.width, img.height)
            cassetteObj.ctx.clearRect(0, 0, 64, 64)
            cassetteObj.ctx.drawImage(img, (img.width - s) / 2, ((img.height - s * 0.57) / 2).toInt(), s, (s * 0.57).toInt(), 0, 0, 64, 64)
            cassetteObj.tex.needsUpdate()
            f.cassetteDeck.materials[0] = cassetteMat
            jewelObj.ctx.drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, 128, 128)
            jewelObj.tex.needsUpdate()
            jewelCase.visible = true
        }
        if (app.extras.posterChoice == "album") app.extras.setPoster("album")
        app.renderer.shadowsNeedUpdate = true
    }
    private var lcdScroll = 0f
    private var lastLcdText = ""
    private var lastLcd = 0L
    /** Every half second: keep the player in step and scroll the tuner LCD. */
    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastLcd < 500) return
        lastLcd = now
        mpSync()
        val st = app.extras.radioStation
        val text = if (st == 0) "" else if (st == MY_MUSIC_STATION) (current?.let { "${if (paused) "|| " else "> "}${it.title}${if (it.artist.isNotEmpty()) " - ${it.artist}" else ""}" } ?: "MY MUSIC") else app.extras.RADIO_STATIONS[st].substringBefore(':')
        val c = lcdObj.ctx
        c.font = "bold 14px monospace"
        val w = if (text.isNotEmpty()) c.measureText(text) else 0f
        if (text == lastLcdText && w <= 92) return
        c.fillStyle = "#06140b"; c.fillRect(0, 0, 96, 24)
        lastLcdText = text
        if (text.isNotEmpty()) {
            c.fillStyle = "#7dff9a"
            if (w <= 92) c.fillText(text, (96 - w) / 2, 17)
            else { lcdScroll = (lcdScroll + 8) % (w + 30); c.fillText(text, 4 - lcdScroll, 17); c.fillText(text, 4 - lcdScroll + w + 30, 17) }
        }
        lcdObj.tex.needsUpdate()
    }
    @Suppress("unused") private fun unusedCanvas(c: Canvas2D) = c
}
