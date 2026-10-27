package com.example.crosshair

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Settings, presets and backup/restore: the things that must never lose a player's setup. */
@RunWith(RobolectricTestRunner::class)
class CfgTest {
    private lateinit var ctx: Context
    private lateinit var s: SharedPreferences

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        s = Cfg.prefs(ctx)
        s.edit().clear().commit()
    }

    private fun fresh(name: String): SharedPreferences =
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun defaultsOnFirstRun() {
        Cfg.ensureDefaults(s)
        assertEquals(0xFF00FF66.toInt(), s.getInt("color", 0))
        assertEquals(20, s.getInt("size", 0))
        assertEquals(2, s.getInt("thick", 0))
        assertEquals(100, s.getInt("alpha", 0))
        assertEquals(40, s.getInt("gap", 0))
        assertEquals(3, s.getInt("othick", 0))
    }

    @Test fun defaultsNeverOverwriteUserSettings() {
        s.edit().putInt("size", 55).putInt("color", 0xFFFF0000.toInt()).commit()
        Cfg.ensureDefaults(s)
        assertEquals(55, s.getInt("size", 0))
        assertEquals(0xFFFF0000.toInt(), s.getInt("color", 0))
    }

    @Test fun landscapeStartsFromPortraitOffset() {
        s.edit().putInt("ox", 7).putInt("oy", -3).commit()   // only a portrait offset saved
        Cfg.ensureDefaults(s)
        assertEquals(7, s.getInt("oxL", 0))
        assertEquals(-3, s.getInt("oyL", 0))
    }

    @Test fun presetSaveAndLoadRoundTrip() {
        Cfg.ensureDefaults(s)
        s.edit().putInt("style", 2).putInt("size", 33).putInt("oxL", 12).putBoolean("outline", true).commit()
        Cfg.savePreset(s, 2, "  Sniper ")
        assertEquals("Sniper", Cfg.presetName(s, 2))
        assertEquals(listOf(2), Cfg.filledSlots(s))
        assertEquals(2, s.getInt("cur_preset", 0))

        s.edit().putInt("style", 0).putInt("size", 10).putInt("oxL", 0).putBoolean("outline", false).putInt("cur_preset", 0).commit()
        val rev = s.getInt("rev", 0)
        assertTrue(Cfg.applyPreset(s, 2))
        assertEquals(2, s.getInt("style", 0))
        assertEquals(33, s.getInt("size", 0))
        assertEquals(12, s.getInt("oxL", 0))
        assertTrue(s.getBoolean("outline", false))
        assertEquals("Sniper", Cfg.currentLabel(s))
        assertEquals(rev + 1, s.getInt("rev", 0))   // tells the open app screen to refresh
    }

    @Test fun blankPresetNameGetsSlotName() {
        Cfg.ensureDefaults(s)
        Cfg.savePreset(s, 3, "   ")
        assertEquals("Preset 3", Cfg.presetName(s, 3))
    }

    @Test fun emptySlotCannotBeApplied() {
        Cfg.ensureDefaults(s)
        assertFalse(Cfg.applyPreset(s, 1))
        assertNull(Cfg.presetName(s, 1))
        assertEquals("Custom", Cfg.currentLabel(s))
    }

    @Test fun presetWithoutLandscapeUsesPortraitOffset() {
        Cfg.ensureDefaults(s)
        val old = JSONObject().put("color", 0xFFFFFFFF.toInt()).put("style", 1).put("size", 20)
            .put("thick", 2).put("alpha", 100).put("ox", 5).put("oy", 9).put("gap", 40).put("name", "Basic")
        s.edit().putString("preset1", old.toString()).putInt("othick", 4).commit()
        assertTrue(Cfg.applyPreset(s, 1))
        assertEquals(5, s.getInt("oxL", 0))
        assertEquals(9, s.getInt("oyL", 0))
        assertEquals(4, s.getInt("othick", 0))   // key missing in the preset: current value kept
    }

    @Test fun brokenPresetIsTreatedAsEmpty() {
        s.edit().putString("preset1", "{not json").commit()
        assertFalse(Cfg.applyPreset(s, 1))
        assertEquals(emptyList<Int>(), Cfg.filledSlots(s))
    }

    @Test fun resetKeepsPresets() {
        Cfg.ensureDefaults(s)
        Cfg.savePreset(s, 1, "Hipfire")
        s.edit().putInt("size", 70).putBoolean("guide", true).commit()
        Cfg.resetLook(s)
        assertEquals(20, s.getInt("size", 0))
        assertFalse(s.getBoolean("guide", false))
        assertEquals("Hipfire", Cfg.presetName(s, 1))
        assertEquals("Custom", Cfg.currentLabel(s))
    }

    @Test fun backupAndRestoreOnAnotherPhone() {
        Cfg.ensureDefaults(s)
        s.edit().putInt("color", 0xFF00F0FF.toInt()).putInt("style", 5).putInt("ox", -4).putInt("oyL", 11)
            .putBoolean("cdot", true).putBoolean("guide", true).commit()
        Cfg.savePreset(s, 1, "Hipfire")
        Cfg.savePreset(s, 3, "Sniper")
        val code = Cfg.backupCode(s)
        assertTrue(Cfg.looksLikeCode(code))
        assertTrue(Cfg.looksLikeCode("  $code\n"))

        val other = fresh("other_phone")
        other.edit().putString("preset2", "{\"name\":\"stale\",\"color\":1}").commit()
        assertTrue(Cfg.restore(other, "  $code \n"))
        for (k in Cfg.intKeys) assertEquals(k, s.getInt(k, 0), other.getInt(k, -1))
        for (k in Cfg.boolKeys + "guide") assertEquals(k, s.getBoolean(k, false), other.getBoolean(k, !s.getBoolean(k, false)))
        assertEquals("Hipfire", Cfg.presetName(other, 1))
        assertNull(Cfg.presetName(other, 2))       // slot that was empty in the backup is cleared
        assertEquals("Sniper", Cfg.presetName(other, 3))
        assertEquals(3, other.getInt("cur_preset", 0))
    }

    @Test fun badCodesChangeNothing() {
        Cfg.ensureDefaults(s)
        s.edit().putInt("size", 44).commit()
        val before = s.all.toMap()
        for (bad in listOf("", "hello", "CROSSHAIR1:", "CROSSHAIR1:!!!not-base64!!!",
                "CROSSHAIR1:" + android.util.Base64.encodeToString("{\"size\":5}".toByteArray(), android.util.Base64.NO_WRAP))) {
            assertFalse(bad, Cfg.restore(s, bad))
        }
        assertEquals(before, s.all.toMap())
        assertFalse(Cfg.looksLikeCode(null))
    }

    @Test fun viewLoadsLandscapeOffsetWithFallback() {
        s.edit().putInt("ox", 6).putInt("oy", -2).commit()     // no landscape keys saved yet
        val v = CrosshairView(ctx)
        v.load(s, withOffset = true)
        assertEquals(6f, v.offsetXL)
        assertEquals(-2f, v.offsetYL)
        v.load(s, withOffset = false)
        assertEquals(0f, v.offsetX)
        assertEquals(0f, v.offsetXL)
    }

    @Test fun overlayWindowIsBigEnoughForEveryStyle() {
        // The overlay window is only extentPx() * 2 wide, so it must hold the whole drawing
        // (line ends with square outline caps, ring stroke, centre dot) at every slider setting.
        val v = CrosshairView(ctx)
        val d = ctx.resources.displayMetrics.density
        fun even(x: Float) = maxOf(2, Math.round(x * d / 2f) * 2)
        for (style in 0..6) for (size in 2..80 step 6) for (thick in 1..12) for (oh in listOf(0, 1, 3, 6)) {
            v.style = style; v.sizeDp = size.toFloat(); v.thickDp = thick.toFloat(); v.centerDot = true
            v.outline = oh > 0; v.outlineHalfDp = maxOf(oh, 1)
            val r = Math.round(size * d); val t = even(thick.toFloat()); val extra = if (oh > 0) even(oh * 0.5f) else 0
            val need = maxOf(r + (t + extra + 1) / 2, Math.round(t * 1.2f) + extra / 2) + 1
            assertTrue("style $style size $size thick $thick outline $oh: ${v.extentPx()} < $need", v.extentPx() >= need)
        }
    }
}
