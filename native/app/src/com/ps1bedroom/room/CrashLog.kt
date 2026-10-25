package com.ps1bedroom.room

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.59: records crashes so they can be fixed. A crash on any thread (and the first error the GL frame loop
 * swallows each session) is written to files/last_crash.txt with the version, time, memory and stack trace; the next
 * launch shows it with a Copy button.
 */
object CrashLog {
    private var file: File? = null
    private var version = "?"
    @Volatile private var frameErrorSaved = false

    fun install(ctx: Context) {
        file = File(ctx.filesDir, "last_crash.txt")
        version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (_: Throwable) { "?" }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("CRASH on thread \"${t.name}\"", e)
            prev?.uncaughtException(t, e)
        }
    }

    /** A caught error worth knowing about (the first one per session is kept). */
    fun recordNonFatal(where: String, e: Throwable) {
        if (frameErrorSaved) return
        frameErrorSaved = true
        write("ERROR (caught) in $where", e)
    }

    private fun write(title: String, e: Throwable) {
        try {
            val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
            val rt = Runtime.getRuntime()
            val mem = "heap ${(rt.totalMemory() - rt.freeMemory()) / 1048576} / ${rt.maxMemory() / 1048576} MB"
            val text = "$title\nPS1 Bedroom native v$version · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} · $mem\n\n$sw"
            file?.writeText(text.take(20000))
        } catch (_: Throwable) { }
    }

    /** On launch: if the last session crashed, show what happened (with Copy) and clear it. */
    fun showIfAny(act: Activity) {
        val f = file ?: return
        if (!f.exists()) return
        val text = try { f.readText() } catch (_: Throwable) { return }
        f.delete()
        act.runOnUiThread {
            val tv = TextView(act).apply { setText(text); setTextIsSelectable(true); textSize = 11f; setPadding(32, 24, 32, 24) }
            AlertDialog.Builder(act)
                .setTitle("The app hit a problem last time")
                .setView(ScrollView(act).apply { addView(tv) })
                .setPositiveButton("Copy report") { _, _ ->
                    (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PS1 Bedroom crash", text))
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }
}
