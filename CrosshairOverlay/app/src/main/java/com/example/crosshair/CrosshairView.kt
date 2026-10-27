package com.example.crosshair

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.View

class CrosshairView(c: Context) : View(c) {
    var color = 0xFF00FF66.toInt()
    var style = 0          // 0 dot, 1 plus, 2 gap plus, 3 T, 4 circle, 5 ring+dot, 6 X
    var sizeDp = 20f
    var thickDp = 2f
    var alphaPct = 100
    var gapPct = 40
    var offsetX = 0f       // portrait offset (dp)
    var offsetY = 0f
    var offsetXL = 0f      // landscape offset (dp)
    var offsetYL = 0f
    var forceLand: Boolean? = null   // preview only: which offset to show, regardless of its own shape
    var offsetScale = 1f             // preview only: shrink offsets to the mini screen
    var outline = false
    var outlineColor = Color.BLACK
    var outlineHalfDp = 3              // outline thickness in 0.5 dp steps
    var centerDot = false
    var guide = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(v: Float) = v * resources.displayMetrics.density
    // Whole pixels, so every line is pixel-aligned and symmetric around the center.
    private fun px(v: Float) = Math.round(dp(v)).toFloat()
    private fun evenPx(v: Float) = (Math.round(dp(v) / 2f) * 2).coerceAtLeast(2).toFloat()

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun load(s: SharedPreferences, withOffset: Boolean) {
        color = s.getInt("color", 0xFF00FF66.toInt()); style = s.getInt("style", 0)
        sizeDp = s.getInt("size", 20).toFloat(); thickDp = s.getInt("thick", 2).toFloat()
        alphaPct = s.getInt("alpha", 100); gapPct = s.getInt("gap", 40)
        outline = s.getBoolean("outline", false); centerDot = s.getBoolean("cdot", false)
        outlineColor = s.getInt("ocolor", Color.BLACK); outlineHalfDp = s.getInt("othick", 3)
        guide = s.getBoolean("guide", false)
        offsetX = if (withOffset) s.getInt("ox", 0).toFloat() else 0f
        offsetY = if (withOffset) s.getInt("oy", 0).toFloat() else 0f
        offsetXL = if (withOffset) s.getInt("oxL", s.getInt("ox", 0)).toFloat() else 0f
        offsetYL = if (withOffset) s.getInt("oyL", s.getInt("oy", 0)).toFloat() else 0f
        invalidate()
    }

    /** Half-size in px of a square that fits the whole crosshair (for the small overlay window). */
    fun extentPx(): Int {
        val r = px(sizeDp).toInt(); val t = evenPx(thickDp).toInt()
        val o = if (outline) evenPx(outlineHalfDp * 0.5f).toInt() else 0
        return maxOf(r, Math.round(t * 1.2f)) + t + o + 4
    }

    override fun onDraw(canvas: Canvas) {
        val mx = Math.round(width / 2f).toFloat(); val my = Math.round(height / 2f).toFloat()
        if (guide) {
            p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
            p.color = 0xFFFF2BD6.toInt(); p.alpha = 170; p.strokeWidth = 2f
            canvas.drawLine(0f, my, width.toFloat(), my, p); canvas.drawLine(mx, 0f, mx, height.toFloat(), p)
        }
        // Landscape (e.g. in games) uses its own offset, picked automatically on rotation.
        val land = forceLand ?: (width > height)
        val cx = mx + Math.round(dp(if (land) offsetXL else offsetX) * offsetScale)
        val cy = my + Math.round(dp(if (land) offsetYL else offsetY) * offsetScale)
        val a = alphaPct * 255 / 100
        if (outline) { p.color = outlineColor; p.alpha = a; shape(canvas, cx, cy, evenPx(outlineHalfDp * 0.5f), true) }
        p.color = color; p.alpha = a; shape(canvas, cx, cy, 0f, false)
    }

    private fun shape(cv: Canvas, cx: Float, cy: Float, extra: Float, o: Boolean) {
        val r = px(sizeDp); val t = evenPx(thickDp); val g = Math.round(r * gapPct / 100f).toFloat()
        p.strokeWidth = t + extra
        p.strokeCap = if (o) Paint.Cap.SQUARE else Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        when (style) {
            0 -> cv.drawCircle(cx, cy, Math.round(r * 0.25f).toFloat().coerceAtLeast(t) + extra / 2, p)
            1 -> { cv.drawLine(cx - r, cy, cx + r, cy, p); cv.drawLine(cx, cy - r, cx, cy + r, p) }
            2 -> { cv.drawLine(cx - r, cy, cx - g, cy, p); cv.drawLine(cx + g, cy, cx + r, cy, p)
                   cv.drawLine(cx, cy - r, cx, cy - g, p); cv.drawLine(cx, cy + g, cx, cy + r, p) }
            3 -> { cv.drawLine(cx - r, cy, cx - g, cy, p); cv.drawLine(cx + g, cy, cx + r, cy, p)
                   cv.drawLine(cx, cy + g, cx, cy + r, p) }
            4, 5 -> { p.style = Paint.Style.STROKE; cv.drawCircle(cx, cy, r, p); p.style = Paint.Style.FILL }
            6 -> { val a = r * 0.7071f; val b = g * 0.7071f
                   cv.drawLine(cx - a, cy - a, cx - b, cy - b, p); cv.drawLine(cx + b, cy + b, cx + a, cy + a, p)
                   cv.drawLine(cx + a, cy - a, cx + b, cy - b, p); cv.drawLine(cx - b, cy + b, cx - a, cy + a, p) }
        }
        if (style == 5 || (centerDot && style != 0)) cv.drawCircle(cx, cy, Math.round(t * 1.2f).toFloat() + extra / 2, p)
    }
}
