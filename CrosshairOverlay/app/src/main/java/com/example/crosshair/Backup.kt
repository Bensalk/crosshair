package com.example.crosshair

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves the backup code as a text file in Download/Crosshair on the phone. */
object Backup {
    const val FILE_NAME = "crosshair-backup.txt"
    const val FOLDER = "Download/Crosshair"

    /** Name the last backup was actually saved under. After a reinstall Android 11+ hides the old
     *  file from the app, so it may save as "crosshair-backup (1).txt"; the message shows the real name. */
    var lastSavedName = FILE_NAME
        private set

    /** Needs a storage permission only on Android 8-9. */
    fun needsPermission(c: Context) = Build.VERSION.SDK_INT < 29 &&
        c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** Writes the file, overwriting the previous backup. Returns false if it couldn't. */
    fun save(c: Context): Boolean {
        val text = Cfg.backupCode(Cfg.prefs(c))
        val ok = try {
            if (Build.VERSION.SDK_INT >= 29) saveMediaStore(c, text) else saveLegacy(c, text)
        } catch (e: Exception) { false }
        if (ok) Cfg.prefs(c).edit().putLong("last_backup", System.currentTimeMillis()).apply()
        return ok
    }

    private fun saveMediaStore(c: Context, text: String): Boolean {
        val cr = c.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var uri: Uri? = null
        // Reuse our earlier backup file instead of creating "crosshair-backup (1).txt".
        cr.query(coll, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf("$FOLDER/", FILE_NAME), null)?.use { cur ->
            if (cur.moveToFirst()) uri = Uri.withAppendedPath(coll, cur.getLong(0).toString())
        }
        if (uri == null) {
            uri = cr.insert(coll, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
            })
        }
        val u = uri ?: return false
        val out = cr.openOutputStream(u, "wt") ?: return false
        out.use { it.write(text.toByteArray()) }
        lastSavedName = try {
            cr.query(u, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null } ?: FILE_NAME
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(c: Context, text: String): Boolean {
        if (needsPermission(c)) return false
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Crosshair")
        if (!dir.exists() && !dir.mkdirs()) return false
        File(dir, FILE_NAME).writeText(text)
        lastSavedName = FILE_NAME
        return true
    }

    /** Auto-backup after saving a preset (on by default). Silent if it can't write. */
    fun auto(c: Context) { if (Cfg.prefs(c).getBoolean("auto_backup", true)) save(c) }

    /** Real backups are about 1-2 KB; anything bigger than this is not a backup file. */
    const val MAX_BYTES = 64 * 1024

    fun read(c: Context, uri: Uri): String? = try {
        c.contentResolver.openInputStream(uri)?.use { readLimited(it) }
    } catch (e: Exception) { null }

    /** Reads at most [MAX_BYTES]; returns null for anything larger (e.g. a video picked by mistake). */
    fun readLimited(input: java.io.InputStream, max: Int = MAX_BYTES): String? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            if (buf.size() + n > max) return null
            buf.write(chunk, 0, n)
        }
        return String(buf.toByteArray())
    }
}
