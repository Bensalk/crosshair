package com.example.crosshair

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast

class OverlayService : Service() {
    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "STOP"
        const val ACTION_HIDE = "HIDE"
        const val ACTION_NEXT = "NEXT"
    }
    private var view: CrosshairView? = null
    private lateinit var wm: WindowManager
    private var hidden = false
    private var lp: WindowManager.LayoutParams? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Cfg.prefs(this)
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean("want_on", false).apply(); stopSelf(); return START_NOT_STICKY }
        // intent == null: Android restarted us after killing the service (battery saver, low memory).
        // Only come back if the user still wanted the crosshair on.
        if (intent == null && (!prefs.getBoolean("want_on", false) || !Settings.canDrawOverlays(this))) {
            stopSelf(); return START_NOT_STICKY }
        prefs.edit().putBoolean("want_on", true).apply()
        when (intent?.action) {
            ACTION_HIDE -> { hidden = !hidden; view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE }
            ACTION_NEXT -> nextPreset()
        }
        startForeground(1, notif())
        if (!running) { running = true; refreshTile() }
        if (view == null) {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= 30) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    fitInsetsTypes = 0
                } else if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                // Android 12+ blocks taps through another app's overlay unless the window is at most
                // this opaque (usually 80%). Staying at the limit keeps game touches working.
                if (Build.VERSION.SDK_INT >= 31) {
                    alpha = getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch.coerceAtMost(1f)
                }
            }
            this.lp = lp
            view = CrosshairView(this).also { wm.addView(it, lp) }
        }
        relayout()
        return START_STICKY
    }

    /** Full display size in px for the current rotation (includes cutouts and system bars). */
    @Suppress("DEPRECATION")
    private fun screenPx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { return Pair(it.width(), it.height()) }
        val dm = android.util.DisplayMetrics(); wm.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    /**
     * Normally the window is only as big as the crosshair, placed at screen centre + offset: Android
     * then blends a tiny layer over the game instead of a full-screen one (better FPS and battery).
     * With center guide lines on it covers the full screen, like before, so the lines can be drawn.
     */
    private fun relayout() {
        val v = view ?: return; val p = lp ?: return
        val prefs = Cfg.prefs(this)
        if (prefs.getBoolean("guide", false)) {
            v.load(prefs, true)
            p.width = WindowManager.LayoutParams.MATCH_PARENT; p.height = WindowManager.LayoutParams.MATCH_PARENT
            p.x = 0; p.y = 0
        } else {
            v.load(prefs, false)          // offsets are applied by moving the window
            val d = resources.displayMetrics.density
            val (w, h) = screenPx()
            val land = w > h
            val half = v.extentPx()
            val cx = Math.round(w / 2f) + Math.round(Cfg.clamp("ox", prefs.getInt(if (land) "oxL" else "ox", 0)) * d)
            val cy = Math.round(h / 2f) + Math.round(Cfg.clamp("oy", prefs.getInt(if (land) "oyL" else "oy", 0)) * d)
            p.width = half * 2; p.height = half * 2       // even size: crosshair centre lands on a whole pixel
            p.x = cx - half; p.y = cy - half
        }
        try { wm.updateViewLayout(v, p) } catch (e: Exception) { }
    }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); relayout() }

    private fun nextPreset() {
        val prefs = Cfg.prefs(this)
        val slots = Cfg.filledSlots(prefs)
        if (slots.isEmpty()) { Toast.makeText(this, "No presets saved yet", Toast.LENGTH_SHORT).show(); return }
        val cur = prefs.getInt("cur_preset", 0)
        val next = slots.firstOrNull { it > cur } ?: slots.first()
        if (Cfg.applyPreset(prefs, next)) {
            if (hidden) { hidden = false; view?.visibility = View.VISIBLE }
            Toast.makeText(this, "Crosshair: ${Cfg.presetName(prefs, next)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun action(label: String, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(this, code, Intent(this, OverlayService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, label, pi).build()
    }

    private fun notif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ov", "Overlay", NotificationManager.IMPORTANCE_LOW))
        val prefs = Cfg.prefs(this)
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "ov")
            .setContentTitle(if (hidden) "Crosshair hidden" else "Crosshair running")
            .setContentText("Preset: ${Cfg.currentLabel(prefs)}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        if (Cfg.filledSlots(prefs).isNotEmpty()) b.addAction(action("Next preset", ACTION_NEXT, 1))
        b.addAction(action(if (hidden) "Show" else "Hide", ACTION_HIDE, 2))
        b.addAction(action("Stop", ACTION_STOP, 0))
        return b.build()
    }

    private fun refreshTile() {
        try { TileService.requestListeningState(this, ComponentName(this, ToggleTile::class.java)) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        running = false; hidden = false
        view?.let { wm.removeView(it) }; view = null
        refreshTile()
        super.onDestroy()
    }
}
