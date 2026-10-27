package com.example.crosshair

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class GridBackground(c: Context) : View(c) {
    var light = false
    private val p = Paint()
    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        cv.drawColor(if (light) 0xFFE6EBF2.toInt() else 0xFF050810.toInt())
        p.strokeWidth = d
        p.color = if (light) 0x22000000 else 0x2200F0FF
        val step = 24f * d
        var x = (width / 2f) % step
        while (x < width) { cv.drawLine(x, 0f, x, height.toFloat(), p); x += step }
        var y = (height / 2f) % step
        while (y < height) { cv.drawLine(0f, y, width.toFloat(), y, p); y += step }
        p.color = if (light) 0x44000000 else 0x4400F0FF
        cv.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), p)
        cv.drawLine(0f, height / 2f, width.toFloat(), height / 2f, p)
    }
}
