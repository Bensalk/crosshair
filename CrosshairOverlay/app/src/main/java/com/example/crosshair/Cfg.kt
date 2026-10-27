package com.example.crosshair

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

/** Settings and presets shared by the app screen and the overlay service. */
object Cfg {
    const val SLOTS = 3
    // ox/oy = portrait offset, oxL/oyL = landscape offset (dp)
    // ocolor = outline colour, othick = outline thickness in half-dp steps (3 = 1.5 dp)
    val intKeys = listOf("color", "style", "size", "thick", "alpha", "ox", "oy", "oxL", "oyL", "gap", "ocolor", "othick")
    val boolKeys = listOf("outline", "cdot")

    /** Allowed range of each number, the same as the sliders. Values from presets and backup files are
     *  kept inside these, so a damaged or tampered file can't make the overlay huge or crash it. */
    private val ranges = mapOf("style" to 0..6, "size" to 2..80, "thick" to 1..12, "alpha" to 10..100,
        "gap" to 0..90, "othick" to 1..6, "ox" to -150..150, "oy" to -150..150, "oxL" to -150..150, "oyL" to -150..150)
    const val MAX_NAME = 40

    /** [v] kept inside the allowed range for [key] (colours can be any value). */
    fun clamp(key: String, v: Int): Int = ranges[key]?.let { v.coerceIn(it) } ?: v

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun ensureDefaults(s: SharedPreferences) {
        val e = s.edit()
        mapOf("color" to 0xFF00FF66.toInt(), "style" to 0, "size" to 20, "thick" to 2,
            "alpha" to 100, "ox" to 0, "oy" to 0, "gap" to 40, "ocolor" to 0xFF000000.toInt(), "othick" to 3).forEach { (k, v) -> if (!s.contains(k)) e.putInt(k, v) }
        // Landscape starts from the portrait offset when it hasn't been set yet.
        if (!s.contains("oxL")) e.putInt("oxL", s.getInt("ox", 0))
        if (!s.contains("oyL")) e.putInt("oyL", s.getInt("oy", 0))
        e.apply()
    }

    private fun json(s: SharedPreferences, i: Int): JSONObject? =
        s.getString("preset$i", null)?.let { try { JSONObject(it) } catch (e: Exception) { null } }

    /** Display name of a slot, or null when empty. */
    fun presetName(s: SharedPreferences, i: Int): String? =
        json(s, i)?.let { it.optString("name", "").trim().take(MAX_NAME).ifBlank { "Preset $i" } }

    fun filledSlots(s: SharedPreferences) = (1..SLOTS).filter { json(s, it) != null }

    fun savePreset(s: SharedPreferences, i: Int, name: String) {
        val o = JSONObject()
        intKeys.forEach { o.put(it, s.getInt(it, 0)) }
        boolKeys.forEach { o.put(it, s.getBoolean(it, false)) }
        o.put("name", name.trim().take(MAX_NAME).ifBlank { "Preset $i" })
        s.edit().putString("preset$i", o.toString()).putInt("cur_preset", i).apply()
    }

    fun applyPreset(s: SharedPreferences, i: Int): Boolean {
        val o = json(s, i) ?: return false
        val ed = s.edit()
        intKeys.forEach { k ->
            // Presets without a landscape offset use the portrait one for landscape too.
            val v = when {
                o.has(k) -> o.optInt(k, 0)
                k == "oxL" -> o.optInt("ox", 0)
                k == "oyL" -> o.optInt("oy", 0)
                else -> s.getInt(k, 0)
            }
            ed.putInt(k, clamp(k, v))
        }
        boolKeys.forEach { ed.putBoolean(it, o.optBoolean(it, false)) }
        ed.putInt("cur_preset", i).putInt("rev", s.getInt("rev", 0) + 1).apply()
        return true
    }

    /** Name shown in the notification: the active preset, or "Custom" after manual edits. */
    fun currentLabel(s: SharedPreferences): String {
        val i = s.getInt("cur_preset", 0)
        return if (i == 0) "Custom" else presetName(s, i) ?: "Custom"
    }

    /** Crosshair look back to defaults. Presets are kept. */
    fun resetLook(s: SharedPreferences) {
        val e = s.edit()
        (intKeys + boolKeys + "guide").forEach { e.remove(it) }
        e.putInt("cur_preset", 0).apply()
        ensureDefaults(s)
    }

    private const val CODE_PREFIX = "CROSSHAIR1:"

    /** All settings and presets as one copyable line. */
    fun backupCode(s: SharedPreferences): String {
        val o = JSONObject()
        intKeys.forEach { o.put(it, s.getInt(it, 0)) }
        (boolKeys + "guide").forEach { o.put(it, s.getBoolean(it, false)) }
        o.put("cur_preset", s.getInt("cur_preset", 0))
        for (i in 1..SLOTS) s.getString("preset$i", null)?.let { o.put("preset$i", it) }
        return CODE_PREFIX + Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun looksLikeCode(t: String?) = t?.trim()?.startsWith(CODE_PREFIX) == true

    /** Returns false (and changes nothing) if the code is not valid. */
    fun restore(s: SharedPreferences, code: String): Boolean {
        val o = try {
            val t = code.trim()
            if (!t.startsWith(CODE_PREFIX)) return false
            JSONObject(String(Base64.decode(t.removePrefix(CODE_PREFIX), Base64.DEFAULT)))
        } catch (e: Exception) { return false }
        if (!o.has("color")) return false
        val e = s.edit()
        intKeys.forEach { k -> if (o.has(k)) e.putInt(k, clamp(k, o.optInt(k))) }
        (boolKeys + "guide").forEach { k -> if (o.has(k)) e.putBoolean(k, o.optBoolean(k)) }
        // Presets are only kept if they are valid JSON; their numbers are clamped when they are applied.
        for (i in 1..SLOTS) {
            val p = o.optString("preset$i", "")
            val ok = p.length <= 4096 && try { JSONObject(p); true } catch (x: Exception) { false }
            if (ok) e.putString("preset$i", p) else e.remove("preset$i")
        }
        e.putInt("cur_preset", o.optInt("cur_preset", 0).coerceIn(0, SLOTS)).putInt("rev", s.getInt("rev", 0) + 1).apply()
        ensureDefaults(s)
        return true
    }
}
