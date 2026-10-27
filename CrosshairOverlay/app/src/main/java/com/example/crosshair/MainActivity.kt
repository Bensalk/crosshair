package com.example.crosshair

import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.hardware.input.InputManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val cyan = 0xFF00F0FF.toInt()
    private val magenta = 0xFFFF2BD6.toInt()
    private val muted = 0xFF8FA3C8.toInt()
    private val palette = intArrayOf(0xFF00FF66.toInt(), 0xFFFF2D2D.toInt(), 0xFF00F0FF.toInt(), 0xFFFFE600.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF2BD6.toInt(), 0xFFFF8800.toInt(), 0xFF3D7BFF.toInt())
    private val styleNames = listOf("Dot", "Plus", "Gap plus", "T-shape", "Circle", "Ring + dot", "X")
    private class Slider(val bar: SeekBar, val min: Int, val keyOf: () -> String)
    private val sliders = HashMap<String, Slider>()      // by slider id
    private val toggles = HashMap<String, Switch>()      // by pref key
    private var syncing = false                          // true while code (not the user) moves controls
    private val handler = Handler(Looper.getMainLooper())
    private val pushOverlay = Runnable {
        if (OverlayService.running && Settings.canDrawOverlays(this))
            startService(Intent(this, OverlayService::class.java))
    }
    private var posLand = false                          // which offset the Position card edits
    private val posChips = ArrayList<TextView>()
    private val presetLabels = ArrayList<TextView>()
    private var seenRev = 0
    private var isWide = false
    private lateinit var banners: LinearLayout
    private lateinit var previewWrap: FrameLayout
    private lateinit var previewBox: FrameLayout
    // Android 12+ shows other apps' overlays at most this solid while still letting taps through (usually 0.8).
    private val overlayCap by lazy {
        if (Build.VERSION.SDK_INT >= 31) getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch.coerceIn(0.1f, 1f) else 1f }
    private lateinit var cards: LinearLayout
    private lateinit var preview: CrosshairView
    private lateinit var startBtn: TextView
    private lateinit var devText: TextView
    private val swatches = ArrayList<View>()
    private lateinit var customSwatch: View
    private lateinit var hueBar: SeekBar
    private lateinit var valBar: SeekBar
    private var pickerSync = false       // true while code moves the colour sliders
    private var fromPicker = false       // colour change came from the sliders: don't move them back
    private val outlineChips = ArrayList<TextView>()
    private val chips = ArrayList<TextView>()

    private val changelog = """
v1.0
• First public release: a light crosshair overlay for FPS games on Android
• 7 styles (Dot, Plus, Gap plus, T-shape, Circle, Ring + dot, X) with size, thickness, gap and opacity
• Any colour: swatches, hue and brightness sliders or a hex code, plus an outline in black or white
• Taps always pass through the crosshair to your game (Android 12+)
• Separate position for portrait and landscape, with 1 dp + / - steps, Center exactly and Fit size to screen
• Three named presets; switch preset, hide / show or stop from the notification during a match
• Quick Settings tile to start and stop
• Comes back on its own if battery saver closes it
• Backup to a file in Download/Crosshair, auto-backup on preset save, and restore
• Device card, changelog and About; two-column layout on tablets
""".trimIndent()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lpTop(m: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(m) }
    private fun panel(radius: Int = 16, fill: Int = 0x33141F3D, stroke: Int = 0x4400F0FF) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke) }
    private fun grad(a: Int, b: Int, radius: Int) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply {
        cornerRadius = dp(radius).toFloat() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = 0xFF060A12.toInt()
        window.navigationBarColor = 0xFF060A12.toInt()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)

        Cfg.ensureDefaults(prefs)
        seenRev = prefs.getInt("rev", 0)
        posLand = screenPx().let { it.first > it.second }

        val wide = resources.configuration.screenWidthDp >= 720
        isWide = wide
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(24)) }
        cards = if (wide) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(20), dp(16), dp(24)) } else main

        // Header
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply { text = "CROSSHAIR"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f; setTextColor(cyan) }
        title.paint.shader = LinearGradient(0f, 0f, dp(220).toFloat(), 0f, cyan, magenta, Shader.TileMode.CLAMP)
        titleBox.addView(title)
        titleBox.addView(TextView(this).apply { text = "YOUR CROSSHAIR, YOUR RULES."; textSize = 10f; letterSpacing = 0.14f; setTextColor(muted) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val menuBtn = TextView(this).apply { text = "\u22EE"; textSize = 28f; setTextColor(cyan); setPadding(dp(20), 0, dp(8), 0) }
        menuBtn.setOnClickListener { v -> showMenu(v) }
        header.addView(menuBtn)
        main.addView(header)

        // Preview with grid
        val box = FrameLayout(this)
        val grid = GridBackground(this)
        var light = false
        box.background = panel(16, 0xFF050810.toInt(), 0x6600F0FF)
        box.outlineProvider = ViewOutlineProvider.BACKGROUND
        box.clipToOutline = true
        box.addView(grid, FrameLayout.LayoutParams(-1, -1))
        preview = CrosshairView(this); preview.alpha = overlayCap
        box.addView(preview, FrameLayout.LayoutParams(-1, -1))
        box.setOnClickListener { light = !light; grid.light = light; grid.invalidate() }
        previewBox = box
        previewWrap = FrameLayout(this)
        previewWrap.addView(box, FrameLayout.LayoutParams(-1, dp(150), Gravity.CENTER_HORIZONTAL))
        var lastW = 0
        previewWrap.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width != lastW) { lastW = v.width; v.post { refreshPreview() } } }
        main.addView(previewWrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        main.addView(TextView(this).apply { text = "TAP PREVIEW TO TEST A LIGHT BACKGROUND"; textSize = 10f
            letterSpacing = 0.12f; setTextColor(muted); setPadding(0, dp(6), 0, 0) })

        startBtn = TextView(this).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.14f
            gravity = Gravity.CENTER; setOnClickListener { toggleOverlay() } }
        main.addView(startBtn, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })
        banners = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        main.addView(banners, LinearLayout.LayoutParams(-1, -2))

        // Color
        val c1 = card("Color")
        val row = LinearLayout(this)
        palette.forEach { col ->
            val v = View(this); v.tag = col
            v.setOnClickListener { save("color", col); refreshSwatches() }
            row.addView(v, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            swatches.add(v)
        }
        customSwatch = View(this).apply { visibility = View.GONE
            setOnClickListener { (tag as? Int)?.let { save("color", it); refreshSwatches() } } }
        row.addView(customSwatch, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        swatches.add(customSwatch)
        c1.addView(row, lpTop(12))
        val hexRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val hex = EditText(this).apply { hint = "Custom hex, e.g. 00F0FF"; setTextColor(Color.WHITE)
            setHintTextColor(0xFF6B7A90.toInt()); isSingleLine = true; textSize = 14f
            background = panel(10); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val setBtn = TextView(this).apply { text = "SET"; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; background = grad(cyan, 0xFF2D6BFF.toInt(), 10)
            setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener {
                try {
                    val col = Color.parseColor("#" + hex.text.toString().trim().removePrefix("#"))
                    saveCustom(col or 0xFF000000.toInt())
                } catch (ex: Exception) { Toast.makeText(this@MainActivity, "Invalid hex color", Toast.LENGTH_SHORT).show() } } }
        hexRow.addView(hex, LinearLayout.LayoutParams(0, -2, 1f))
        hexRow.addView(setBtn, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        c1.addView(hexRow, lpTop(10))
        fun pickerBar(label: String, max: Int): SeekBar {
            c1.addView(TextView(this).apply { text = label; textSize = 11f; letterSpacing = 0.12f; setTextColor(muted) }, lpTop(12))
            val sb = SeekBar(this); sb.max = max; sb.splitTrack = false
            sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
            if (Build.VERSION.SDK_INT >= 29) { sb.minHeight = dp(10); sb.maxHeight = dp(10) }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (pickerSync) return
                    val col = Color.HSVToColor(floatArrayOf(hueBar.progress.toFloat(), 1f, valBar.progress / 100f))
                    fromPicker = true; saveCustom(col); fromPicker = false; tintValBar() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            c1.addView(sb, lpTop(4)); return sb
        }
        hueBar = pickerBar("HUE", 360)
        hueBar.progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
                0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt())).apply { cornerRadius = dp(5).toFloat() }
        valBar = pickerBar("BRIGHTNESS", 100)
        refreshSwatches()

        // Style
        val c2 = card("Style")
        val hs = HorizontalScrollView(this); val r2 = LinearLayout(this)
        styleNames.forEachIndexed { i, n ->
            val tv = TextView(this).apply { text = n.uppercase(); textSize = 12f; letterSpacing = 0.08f; gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { save("style", i); refreshChips() } }
            r2.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            chips.add(tv)
        }
        hs.addView(r2); hs.isHorizontalScrollBarEnabled = false
        c2.addView(hs, lpTop(12)); refreshChips()
        toggle(c2, "Center dot (adds a dot to plus / circle / T / X)", "cdot", false)
        toggle(c2, "Outline (better visibility)", "outline", false)
        val orow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        orow.addView(TextView(this).apply { text = "OUTLINE"; textSize = 11f; letterSpacing = 0.12f; setTextColor(muted) },
            LinearLayout.LayoutParams(0, -2, 1f))
        listOf("BLACK" to Color.BLACK, "WHITE" to Color.WHITE).forEach { (label, col) ->
            val tv = TextView(this).apply { text = label; textSize = 12f; letterSpacing = 0.08f; gravity = Gravity.CENTER; tag = col
                setPadding(dp(16), dp(8), dp(16), dp(8)); setOnClickListener { save("ocolor", col); refreshOutline() } }
            orow.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
            outlineChips.add(tv)
        }
        c2.addView(orow, lpTop(10))
        refreshOutline()
        slider(c2, "othick", "Outline thickness", 1, 6, 3, "", fmt = { v -> if (v % 2 == 0) "${v / 2} dp" else "${v / 2}.5 dp" })
        toggle(c2, "Center guide lines (to test the exact center)", "guide", false)

        // Size & look
        val c3 = card("Size and look")
        slider(c3, "size", "Size", 2, 80, 20, " dp")
        slider(c3, "thick", "Thickness", 1, 12, 2, " dp")
        slider(c3, "gap", "Gap (gap plus, T, X)", 0, 90, 40, "%")
        slider(c3, "alpha", "Opacity", 10, 100, 100, "%", extra = { v ->
            if (overlayCap < 1f) "  (on screen ${Math.round(v * overlayCap)}%)" else "" })
        if (overlayCap < 1f) c3.addView(TextView(this).apply { textSize = 11f; setTextColor(muted); setPadding(0, dp(6), 0, 0)
            text = "Android 12+ only lets taps pass through overlays up to ${Math.round(overlayCap * 100)}% solid, so that's the max. " +
                "Turn on Outline for a sharper look." })

        // Position
        val c4 = card("Position (fine-tune center)")
        val pr = LinearLayout(this)
        listOf("PORTRAIT" to false, "LANDSCAPE" to true).forEach { (label, land) ->
            val tv = TextView(this).apply { text = label; textSize = 12f; letterSpacing = 0.08f; gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { posLand = land; refreshPos() } }
            pr.addView(tv, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            posChips.add(tv)
        }
        c4.addView(pr, lpTop(12))
        c4.addView(TextView(this).apply { textSize = 11f; setTextColor(muted); setPadding(0, dp(8), 0, 0)
            text = "Each has its own position and switches automatically when you rotate. Use landscape for games. " +
                "Start the overlay and turn on center guide lines to adjust it live." })
        slider(c4, "ox", "Horizontal", -150, 150, 0, " dp", steps = true) { if (posLand) "oxL" else "ox" }
        slider(c4, "oy", "Vertical", -150, 150, 0, " dp", steps = true) { if (posLand) "oyL" else "oy" }
        refreshPos()

        // Presets
        val c5 = card("Presets")
        c5.addView(TextView(this).apply { textSize = 11f; setTextColor(muted); setPadding(0, dp(8), 0, 0)
            text = "Tip: save e.g. Hipfire and Sniper, then switch with Next preset in the notification while playing." })
        for (i in 1..Cfg.SLOTS) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val name = TextView(this).apply { textSize = 13f; setPadding(dp(4), 0, dp(8), 0); isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END }
            presetLabels.add(name)
            r.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            fun btn(label: String, col: Int, stroke: Int, f: () -> Unit) = r.addView(TextView(this).apply {
                text = label; textSize = 12f; letterSpacing = 0.1f; setTextColor(col); gravity = Gravity.CENTER
                background = panel(12, 0x22141F3D, stroke); setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener { f() } }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
            btn("LOAD", magenta, 0x66FF2BD6) { loadPreset(i) }
            btn("SAVE", cyan, 0x6600F0FF) { savePreset(i) }
            c5.addView(r, lpTop(8))
        }
        refreshPresets()

        // Device
        val c6 = card("Device")
        devText = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f
            setTextColor(0xFFCFE3FF.toInt()); text = deviceText() }
        c6.addView(devText, lpTop(10))
        val dr = LinearLayout(this)
        fun action(label: String, f: () -> Unit) = dr.addView(TextView(this).apply {
            text = label; textSize = 11f; letterSpacing = 0.08f; setTextColor(cyan); gravity = Gravity.CENTER
            background = panel(12, 0x22141F3D, 0x6600F0FF); setPadding(dp(6), dp(12), dp(6), dp(12))
            setOnClickListener { f() } }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        action("CENTER EXACTLY") { setSlider("ox", 0); setSlider("oy", 0)
            Toast.makeText(this, (if (posLand) "Landscape" else "Portrait") + " position centered", Toast.LENGTH_SHORT).show() }
        action("FIT SIZE TO SCREEN") {
            val (w, h) = screenPx()
            val shortDp = minOf(w, h) / resources.displayMetrics.density
            setSlider("size", Math.round(shortDp * 0.03f).coerceIn(6, 80))
        }
        c6.addView(dr, lpTop(12))

        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF060A12.toInt(), 0xFF0E1530.toInt()))
        if (wide) {
            val outer = LinearLayout(this); outer.background = bg
            val left = ScrollView(this).apply { addView(main) }
            val right = ScrollView(this).apply { addView(cards) }
            outer.addView(left, LinearLayout.LayoutParams(0, -1, 4f))
            outer.addView(right, LinearLayout.LayoutParams(0, -1, 5f))
            setContentView(outer)
        } else {
            val scroll = ScrollView(this); scroll.background = bg
            scroll.addView(main, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
            scroll.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val w = minOf(r - l, dp(600)); val lp = main.layoutParams
                if (lp.width != w) { lp.width = w; main.layoutParams = lp }
            }
            setContentView(scroll)
        }
    }

    override fun onResume() {
        super.onResume(); refreshStart(); refreshBanners()
        // A preset was switched from the notification while we were away: show it.
        val rev = prefs.getInt("rev", 0)
        if (rev != seenRev) { seenRev = rev; syncAll() }
    }

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(16))
            background = panel(16, 0x99101A33.toInt(), 0x4400F0FF) }
        c.addView(TextView(this).apply { text = "\u25B8 " + title.uppercase(); setTextColor(cyan); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.2f })
        cards.addView(c, lpTop(14))
        return c
    }

    /** A slider bound to a pref key. keyOf lets the Position sliders switch between portrait and landscape keys. */
    private fun slider(parent: LinearLayout, id: String, name: String, min: Int, max: Int, def: Int, unit: String,
                       steps: Boolean = false, extra: (Int) -> String = { "" }, fmt: ((Int) -> String)? = null,
                       keyOf: () -> String = { id }) {
        val cur = prefs.getInt(keyOf(), def)
        fun label(v: Int) = "$name: " + (fmt?.invoke(v) ?: "$v$unit") + extra(v)
        val tv = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; text = label(cur) }
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = cur - min
        sb.progressTintList = ColorStateList.valueOf(cyan)
        sb.thumbTintList = ColorStateList.valueOf(magenta)
        sb.progressBackgroundTintList = ColorStateList.valueOf(0x4400F0FF)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                val v = p + min; tv.text = label(v)
                if (!syncing) save(keyOf(), v) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sliders[id] = Slider(sb, min, keyOf)
        parent.addView(tv, lpTop(12))
        if (!steps) { parent.addView(sb); return }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun step(label: String, d: Int) = TextView(this).apply {
            text = label; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan); gravity = Gravity.CENTER
            background = panel(10, 0x22141F3D, 0x6600F0FF)
            // Tap = one step. Hold = keeps stepping. Light vibration on each step.
            // Vibrate only when the value really moved (not while held at the end of the range).
            val tick = { val before = sb.progress
                sb.progress = (sb.progress + d).coerceIn(0, sb.max)
                if (sb.progress != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            val repeat = object : Runnable { override fun run() { tick(); postDelayed(this, 60) } }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { tick(); v.isPressed = true; v.postDelayed(repeat, 400) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.removeCallbacks(repeat); v.isPressed = false }
                }
                true
            } }
        row.addView(step("\u2212", -1), LinearLayout.LayoutParams(dp(44), dp(36)))
        row.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(step("+", 1), LinearLayout.LayoutParams(dp(44), dp(36)))
        parent.addView(row, lpTop(4))
    }

    /** Move a slider from code; its listener saves the value. */
    private fun setSlider(id: String, v: Int) { sliders[id]?.let { it.bar.progress = v - it.min } }

    /** Re-read every control from prefs without saving anything (after loading a preset, etc.). */
    private fun syncAll() {
        syncing = true
        sliders.values.forEach { it.bar.progress = prefs.getInt(it.keyOf(), 0) - it.min }
        toggles.forEach { (k, sw) -> sw.isChecked = prefs.getBoolean(k, false) }
        syncing = false
        refreshSwatches(); refreshChips(); refreshPos(); refreshPresets(); refreshOutline()
        preview.load(prefs, true)
        if (::devText.isInitialized) devText.text = deviceText()
    }

    /** Mini phone screen in the orientation being edited, with the offset scaled to it. */
    private fun refreshPreview() {
        if (!::previewWrap.isInitialized) return
        val avail = previewWrap.width
        if (avail <= 0) return
        val (sw, sh) = screenPx()
        val long = maxOf(sw, sh).toFloat(); val short = minOf(sw, sh).toFloat()
        val ratio = if (posLand) long / short else short / long     // width / height
        var h = dp(if (isWide) 260 else 200).toFloat(); var w = h * ratio
        if (w > avail) { w = avail.toFloat(); h = w / ratio }
        val lp = previewBox.layoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt()) { lp.width = w.toInt(); lp.height = h.toInt(); previewBox.layoutParams = lp }
        preview.forceLand = posLand
        preview.offsetScale = w / (if (posLand) long else short)
        preview.load(prefs, true)
    }

    private fun banner(msg: String, action: String, onDismiss: (() -> Unit)? = null, f: () -> Unit) {
        val b = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(8), dp(10))
            background = panel(14, 0x33FF2BD6, 0x88FF2BD6.toInt()) }
        b.addView(TextView(this).apply { text = msg; textSize = 12f; setTextColor(0xFFE6EEFF.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        b.addView(TextView(this).apply { text = action; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
            setTextColor(Color.BLACK); gravity = Gravity.CENTER; background = grad(cyan, 0xFF2D6BFF.toInt(), 10)
            setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { f() } }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        if (onDismiss != null) b.addView(TextView(this).apply { text = "\u2715"; textSize = 14f; setTextColor(muted)
            setPadding(dp(10), dp(6), dp(4), dp(6)); setOnClickListener { onDismiss() } })
        banners.addView(b, lpTop(12))
    }

    private fun refreshBanners() {
        if (!::banners.isInitialized) return
        banners.removeAllViews()
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled())
            banner("Notifications are off, so Next preset / Hide / Stop won't show while you play.", "ALLOW") { fixNotifications() }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) &&
            !prefs.getBoolean("batt_dismiss", false))
            banner("Battery saver can stop the crosshair in the middle of a match.", "ALLOW",
                onDismiss = { prefs.edit().putBoolean("batt_dismiss", true).apply(); refreshBanners() }) { fixBattery() }
    }

    private fun fixNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notif_asked", false) &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("notif_asked", true).apply()
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun fixBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        Toast.makeText(this, "On Xiaomi, Realme, Oppo or Vivo also turn on Autostart for Crosshair in App info.", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 2) { if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) showBackup(); return }
        refreshBanners(); pushOverlay.run()   // re-post the notification if the overlay is running
    }

    private fun refreshPos() {
        refreshPreview()
        posChips.forEachIndexed { i, tv ->
            val sel = (i == 1) == posLand
            tv.setTextColor(if (sel) Color.BLACK else Color.WHITE)
            tv.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.background = if (sel) grad(cyan, 0xFF2D6BFF.toInt(), 12) else panel(12)
        }
        syncing = true
        listOf("ox", "oy").forEach { id -> sliders[id]?.let { it.bar.progress = prefs.getInt(it.keyOf(), 0) - it.min } }
        syncing = false
    }

    private fun refreshPresets() {
        val cur = prefs.getInt("cur_preset", 0)
        presetLabels.forEachIndexed { idx, tv ->
            val i = idx + 1
            val n = Cfg.presetName(prefs, i)
            tv.text = if (n == null) "$i \u00B7 Empty" else "$i \u00B7 $n" + if (i == cur) "  \u2713" else ""
            tv.setTextColor(if (n == null) muted else if (i == cur) cyan else Color.WHITE)
            tv.typeface = if (i == cur) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun toggle(parent: LinearLayout, label: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = label; sw.setTextColor(Color.WHITE); sw.isChecked = prefs.getBoolean(key, def)
        sw.thumbTintList = ColorStateList.valueOf(cyan)
        sw.trackTintList = ColorStateList.valueOf(0x6600F0FF)
        sw.setOnCheckedChangeListener { _, v -> if (!syncing) { prefs.edit().putBoolean(key, v).apply(); changed(key) } }
        toggles[key] = sw
        parent.addView(sw, lpTop(12))
    }

    private fun save(key: String, v: Int) { prefs.edit().putInt(key, v).apply(); changed(key) }

    private fun changed(key: String) {
        // Editing a preset value turns the active preset into "Custom" (guide lines aren't part of presets).
        if (key != "guide" && prefs.getInt("cur_preset", 0) != 0) {
            prefs.edit().putInt("cur_preset", 0).apply(); refreshPresets()
        }
        preview.load(prefs, true)
        if (::devText.isInitialized) devText.text = deviceText()
        // Update the live overlay at most every ~120 ms instead of on every slider tick.
        handler.removeCallbacks(pushOverlay)
        handler.postDelayed(pushOverlay, 120)
    }

    /** A colour picked with the sliders or hex box: also kept as the extra "custom" swatch. */
    private fun saveCustom(col: Int) {
        prefs.edit().putInt("custom_color", col).apply()
        save("color", col); refreshSwatches()
    }

    private fun tintValBar() {
        val full = Color.HSVToColor(floatArrayOf(hueBar.progress.toFloat(), 1f, 1f))
        valBar.progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.BLACK, full))
            .apply { cornerRadius = dp(5).toFloat() }
    }

    private fun refreshOutline() {
        val cur = prefs.getInt("ocolor", Color.BLACK)
        outlineChips.forEach { tv ->
            val sel = (tv.tag as Int) == cur
            tv.setTextColor(if (sel) Color.BLACK else Color.WHITE)
            tv.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.background = if (sel) grad(cyan, 0xFF2D6BFF.toInt(), 12) else panel(12)
        }
    }

    private fun refreshSwatches() {
        val cur = prefs.getInt("color", 0)
        if (::customSwatch.isInitialized) {
            val custom = if (prefs.contains("custom_color")) prefs.getInt("custom_color", 0) else null
            customSwatch.tag = custom ?: 0
            customSwatch.visibility = if (custom == null || palette.contains(custom)) View.GONE else View.VISIBLE
        }
        if (::valBar.isInitialized && !fromPicker) {
            val hsv = FloatArray(3); Color.colorToHSV(cur, hsv)
            pickerSync = true
            hueBar.progress = Math.round(hsv[0]); valBar.progress = Math.round(hsv[2] * 100)
            pickerSync = false
            tintValBar()
        }
        swatches.forEach { v ->
            if (v.visibility != View.VISIBLE) return@forEach
            val col = v.tag as Int
            v.background = GradientDrawable().apply { setColor(col); cornerRadius = dp(10).toFloat()
                if (col == cur) setStroke(dp(3), Color.WHITE) else setStroke(dp(1), 0x33FFFFFF) }
        }
    }

    private fun refreshChips() {
        val cur = prefs.getInt("style", 0)
        chips.forEachIndexed { i, tv ->
            val sel = i == cur
            tv.setTextColor(if (sel) Color.BLACK else Color.WHITE)
            tv.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.background = if (sel) grad(cyan, 0xFF2D6BFF.toInt(), 12) else panel(12)
        }
    }

    private fun refreshStart() {
        val on = OverlayService.running
        startBtn.text = if (on) "\u25A0  STOP OVERLAY" else "\u25B6  START OVERLAY"
        startBtn.setTextColor(if (on) Color.WHITE else Color.BLACK)
        startBtn.background = if (on) grad(magenta, 0xFFFF3B3B.toInt(), 14) else grad(cyan, 0xFF2D6BFF.toInt(), 14)
    }

    private fun toggleOverlay() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction("STOP"))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow \"Display over other apps\", then tap Start again. If greyed out: App info > menu > Allow restricted settings",
                    Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        startBtn.postDelayed({ refreshStart() }, 400)
    }

    private fun showMenu(anchor: View) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; minimumWidth = dp(170)
            setPadding(dp(6), dp(6), dp(6), dp(6)); background = panel(14, 0xF2080D1C.toInt(), 0x6600F0FF) }
        val pw = PopupWindow(box, -2, -2, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        fun item(label: String, f: () -> Unit) = box.addView(TextView(this).apply {
            text = label; textSize = 13f; letterSpacing = 0.14f; setTextColor(cyan); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { pw.dismiss(); f() } })
        item("\u25B8  BACKUP") { showBackup() }
        item("\u25B8  RESTORE") { showRestore() }
        item("\u25B8  RESET") { showReset() }
        item("\u25B8  CHANGELOG") { showChangelog() }
        item("\u25B8  ABOUT") { showAbout() }
        pw.showAsDropDown(anchor, anchor.width - dp(170), 0)
    }

    @Suppress("DEPRECATION")
    private fun screenPx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.maximumWindowMetrics.bounds
            return Pair(b.width(), b.height())
        }
        val dm = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private fun deviceName(): String = Settings.Global.getString(contentResolver, "device_name") ?: Build.MODEL

    private fun deviceText(): String {
        val (w, h) = screenPx()
        val d = resources.displayMetrics.density
        val dpi = resources.displayMetrics.densityDpi
        val name = deviceName()
        val land = w > h
        val cx = w / 2 + Math.round(prefs.getInt(if (land) "oxL" else "ox", 0) * d)
        val cy = h / 2 + Math.round(prefs.getInt(if (land) "oyL" else "oy", 0) * d)
        return "Device: $name\nModel: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Screen: $w x $h px\nScreen: ${Math.round(w / d)} x ${Math.round(h / d)} dp @ $dpi dpi\n" +
            "Screen center: ${w / 2}, ${h / 2} px\nCrosshair at: $cx, $cy px"
    }

    private fun styledDialog(title: String, closeLabel: String = "CLOSE", build: (LinearLayout, Dialog) -> Unit) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16)); background = panel(20, 0xF2080D1C.toInt(), 0x6600F0FF) }
        val t = TextView(this).apply { text = title.uppercase(); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f; setTextColor(cyan) }
        t.paint.shader = LinearGradient(0f, 0f, dp(200).toFloat(), 0f, cyan, magenta, Shader.TileMode.CLAMP)
        box.addView(t)
        val line = View(this); line.background = grad(cyan, magenta, 1)
        box.addView(line, LinearLayout.LayoutParams(-1, dp(2)).apply { topMargin = dp(10); bottomMargin = dp(6) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(4), 0) }
        build(body, dlg)
        val maxH = (resources.configuration.screenHeightDp * resources.displayMetrics.density * 0.6f).toInt()
        val sv = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
            }
        }
        sv.addView(body)
        box.addView(sv, LinearLayout.LayoutParams(-1, -2))
        val close = TextView(this).apply { text = closeLabel; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f; gravity = Gravity.CENTER; background = grad(cyan, 0xFF2D6BFF.toInt(), 12)
            setOnClickListener { dlg.dismiss() } }
        box.addView(close, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(14) })
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setDimAmount(0.65f)
        val ww = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt()
        dlg.show()
        dlg.window?.setLayout(minOf(dp(440), (ww * 0.92f).toInt()), -2)
    }

    private fun showChangelog() = styledDialog("Changelog") { body, _ ->
        var first = true
        changelog.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            if (t.startsWith("\u2022")) {
                val sp = SpannableString("\u25B8 " + t.removePrefix("\u2022").trim())
                sp.setSpan(ForegroundColorSpan(magenta), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                body.addView(TextView(this).apply { text = sp; textSize = 13f; setTextColor(0xFFE6EEFF.toInt())
                    setPadding(dp(4), dp(3), 0, dp(3)) })
            } else {
                val label = if (first) t.uppercase() + "  \u2022  LATEST" else t.uppercase()
                first = false
                body.addView(TextView(this).apply { text = label; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.14f; setTextColor(Color.BLACK); background = grad(cyan, 0xFF2D6BFF.toInt(), 8)
                    setPadding(dp(12), dp(5), dp(12), dp(5)) },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14); bottomMargin = dp(4) })
            }
        }
    }

    private fun infoRow(parent: LinearLayout, label: String, value: String) {
        parent.addView(TextView(this).apply { text = label.uppercase(); textSize = 10f; letterSpacing = 0.18f; setTextColor(muted) }, lpTop(12))
        parent.addView(TextView(this).apply { text = value; textSize = 15f; setTextColor(Color.WHITE) })
    }

    private fun showAbout() = styledDialog("About") { body, _ ->
        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        val name = TextView(this).apply { text = "CROSSHAIR"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f; setTextColor(cyan) }
        name.paint.shader = LinearGradient(0f, 0f, dp(200).toFloat(), 0f, cyan, magenta, Shader.TileMode.CLAMP)
        body.addView(name)
        body.addView(TextView(this).apply { text = "VERSION $v"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f; setTextColor(Color.BLACK); background = grad(cyan, 0xFF2D6BFF.toInt(), 8)
            setPadding(dp(12), dp(5), dp(12), dp(5)) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        infoRow(body, "Owner", deviceName())
        infoRow(body, "Creator", "Bensal")
        infoRow(body, "Notes", "Free \u2022 No ads \u2022 No internet access")
        body.addView(TextView(this).apply { text = "\u25B8 DEVICE"; setTextColor(cyan); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.2f }, lpTop(18))
        body.addView(TextView(this).apply { text = deviceText(); typeface = Typeface.MONOSPACE; textSize = 12f
            setTextColor(0xFFCFE3FF.toInt()); background = panel(12); setPadding(dp(12), dp(10), dp(12), dp(10)) }, lpTop(8))
    }

    private fun savePreset(i: Int) {
        val old = Cfg.presetName(prefs, i)
        styledDialog("Save preset $i", "CANCEL") { body, dlg ->
            if (old != null) body.addView(TextView(this).apply { text = "This replaces \u201C$old\u201D."; textSize = 13f
                setTextColor(magenta) }, lpTop(10))
            val input = EditText(this).apply { hint = "Name, e.g. Hipfire"; setText(old ?: ""); setTextColor(Color.WHITE)
                setHintTextColor(0xFF6B7A90.toInt()); isSingleLine = true; textSize = 15f
                background = panel(10); setPadding(dp(12), dp(10), dp(12), dp(10)); setSelectAllOnFocus(true) }
            body.addView(input, lpTop(12))
            body.addView(TextView(this).apply { text = if (old != null) "REPLACE" else "SAVE"; setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.18f; gravity = Gravity.CENTER
                background = grad(cyan, 0xFF2D6BFF.toInt(), 12)
                setOnClickListener {
                    Cfg.savePreset(prefs, i, input.text.toString())
                    Backup.auto(this@MainActivity)
                    dlg.dismiss(); refreshPresets(); pushOverlay.run()
                    Toast.makeText(this@MainActivity, "Saved \u201C${Cfg.presetName(prefs, i)}\u201D", Toast.LENGTH_SHORT).show()
                } }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(14) })
        }
    }

    private fun loadPreset(i: Int) {
        if (!Cfg.applyPreset(prefs, i)) { Toast.makeText(this, "Slot $i is empty", Toast.LENGTH_SHORT).show(); return }
        seenRev = prefs.getInt("rev", 0)
        syncAll(); pushOverlay.run()
        Toast.makeText(this, "Loaded \u201C${Cfg.presetName(prefs, i)}\u201D", Toast.LENGTH_SHORT).show()
    }

    private fun dialogButton(body: LinearLayout, label: String, a: Int, b: Int, f: () -> Unit) =
        body.addView(TextView(this).apply { text = label; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f; gravity = Gravity.CENTER; background = grad(a, b, 12); setOnClickListener { f() } },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })

    private fun showBackup() {
        if (Backup.needsPermission(this)) {   // Android 8-9 only
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 2); return }
        val ok = Backup.save(this)
        styledDialog("Backup") { body, _ ->
            body.addView(TextView(this).apply { textSize = 14f; setTextColor(if (ok) 0xFFE6EEFF.toInt() else magenta)
                text = if (ok) "Saved to your phone:\n${Backup.FOLDER}/${Backup.lastSavedName}\n\nUse Restore and pick this file to bring " +
                    "back all settings and presets, even after reinstalling. Copy the file to another phone to use it there."
                else "Couldn't save the backup file. Check that the phone has free storage and try again." }, lpTop(10))
            val sw = Switch(this)
            sw.text = "Auto-backup when I save a preset"; sw.setTextColor(Color.WHITE)
            sw.isChecked = prefs.getBoolean("auto_backup", true)
            sw.thumbTintList = ColorStateList.valueOf(cyan); sw.trackTintList = ColorStateList.valueOf(0x6600F0FF)
            sw.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("auto_backup", v).apply() }
            body.addView(sw, lpTop(14))
        }
    }

    private fun showRestore() = styledDialog("Restore", "CANCEL") { body, dlg ->
        body.addView(TextView(this).apply { textSize = 14f; setTextColor(0xFFE6EEFF.toInt())
            text = "Pick your backup file from ${Backup.FOLDER} (the newest crosshair-backup file). " +
                "This replaces your current settings and presets." }, lpTop(10))
        dialogButton(body, "CHOOSE BACKUP FILE", cyan, 0xFF2D6BFF.toInt()) {
            dlg.dismiss()
            try {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
            } catch (e: Exception) { Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show() }
        }
    }

    @Deprecated("Plain Activity result callback")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 7 || res != RESULT_OK) return
        val text = data?.data?.let { Backup.read(this, it) }
        if (text != null && Cfg.restore(prefs, text)) {
            seenRev = prefs.getInt("rev", 0); syncAll(); pushOverlay.run()
            Toast.makeText(this, "Settings restored", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "That file isn't a Crosshair backup", Toast.LENGTH_LONG).show()
    }

    private fun showReset() = styledDialog("Reset", "CANCEL") { body, dlg ->
        body.addView(TextView(this).apply { textSize = 14f; setTextColor(0xFFE6EEFF.toInt())
            text = "Reset color, style, size, position and toggles to default? Your saved presets stay." }, lpTop(10))
        dialogButton(body, "RESET", magenta, 0xFFFF3B3B.toInt()) {
            Cfg.resetLook(prefs); dlg.dismiss(); syncAll(); pushOverlay.run()
            Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show()
        }
    }
}
