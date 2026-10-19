package com.ps1bedroom.room

import android.content.Context
import com.ps1bedroom.room.engine.Geo
import com.ps1bedroom.room.engine.Group
import com.ps1bedroom.room.engine.Mesh
import com.ps1bedroom.room.engine.PS1
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** HTML 17410-17432: DAILY SURPRISES. Something new in the room each day, found by tapping it. */
class Surprises(private val app: App, ctx: Context) {
    private class Surprise(val id: String, val msg: String, val build: (Group) -> Unit)
    private fun m(color: Int, emissive: Int = 0, ei: Float = 1f) = if (emissive != 0) PS1.material(color = color, emissive = emissive, emissiveIntensity = ei) else PS1.material(color = color)
    private val SURPRISES = listOf(
        Surprise("package", "📦 A package by the door! It's a memory card. 15 blocks free. Finally.") { g ->
            Mesh(Geo.box(0.4f, 0.3f, 0.32f), m(0xb8864b)).apply { position.set(1.9f, 0.15f, 2.9f); g.add(this) }
            Mesh(Geo.box(0.42f, 0.04f, 0.06f), m(0xe8dcc0)).apply { position.set(1.9f, 0.3f, 2.9f); g.add(this) } },
        Surprise("pizza", "🍕 Leftover pizza on the desk. Cold pizza is the best pizza.") { g ->
            Mesh(Geo.box(0.42f, 0.04f, 0.42f), m(0xd9c9a3)).apply { position.set(-1.5f, 1.53f, -0.95f); g.add(this) } },
        Surprise("balloon", "🎈 A balloon from somebody's party drifted in and got stuck on the ceiling.") { g ->
            Mesh(Geo.sphere(0.16f, 8, 6), m(0xef4444, 0x330000, 0.3f)).apply { scale.y = 1.2f; position.set(0.2f, 4.7f, 1.2f); g.add(this) }
            Mesh(Geo.cylinder(0.004f, 0.004f, 1.2f, 3), m(0xffffff)).apply { position.set(0.2f, 4.0f, 1.2f); g.add(this) } },
        Surprise("note", "📝 A note under the door: \"dinner's in the fridge. love you. -mom\"") { g ->
            Mesh(Geo.box(0.2f, 0.005f, 0.14f), m(0xfef9c3)).apply { position.set(2.3f, 0.01f, 3.2f); rotation.y = 0.4f; g.add(this) } },
        Surprise("soda", "🥤 A can of grape soda on the nightstand. Still cold, somehow.") { g ->
            Mesh(Geo.cylinder(0.035f, 0.035f, 0.12f, 8), m(0x7c3aed)).apply { position.set(1.05f, 1.24f, -2.35f); g.add(this) } },
        Surprise("ball", "🎾 Jasper left his tennis ball in the middle of the floor. He would like you to throw it.") { g ->
            Mesh(Geo.sphere(0.06f, 8, 6), m(0xd9f99d)).apply { position.set(0.3f, 0.06f, 1.0f); g.add(this) } },
        Surprise("magazine", "📰 The new issue of GamePro on the bed. Cheat codes on page 74.") { g ->
            Mesh(Geo.box(0.3f, 0.01f, 0.4f), m(0xdc2626)).apply { position.set(2.6f, 0.9f, 0.6f); rotation.y = 0.3f; g.add(this) } }
    )
    private val prefs = ctx.getSharedPreferences("ps1bedroom", Context.MODE_PRIVATE)
    val group = Group()
    private lateinit var today: Surprise
    /** placeSurprise: after everything else is built (it's never batched). */
    fun build() {
        val day = (System.currentTimeMillis() / 86400000L).toInt()
        today = SURPRISES[day % SURPRISES.size]
        today.build(group)
        for (o in group.children) if (o is Mesh) { o.castShadow = true; app.room.registerInteractable(o, "surprise") }
        app.scene.add(group)
    }
    fun find() {
        app.toast(today.msg); app.sfx("caseClick")
        val days = try { JSONObject(prefs.getString("surpriseDays", "{}")!!) } catch (e: Exception) { JSONObject() }
        days.put(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), today.id)
        prefs.edit().putString("surpriseDays", days.toString()).apply()
        app.achievements.achieve("surprise"); if (days.length() >= 7) app.achievements.achieve("surprise7")
    }
}
