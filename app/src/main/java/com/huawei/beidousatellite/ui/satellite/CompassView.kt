package com.huawei.beidousatellite.ui.satellite

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuthDeg: Float = 0f // device orientation
    private var satelliteAzimuthDeg: Float = 0f
    private var satelliteElevationDeg: Float = 45f
    private var snr: Float = 0f
    private var satelliteId: Int = 0

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FF6200EE")
    }
    private val paintSatellite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val paintNorth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    fun updateCompass(deviceAzimuth: Float, satAzimuth: Float, satElevation: Float, snrDb: Float, satId: Int) {
        this.azimuthDeg = deviceAzimuth
        this.satelliteAzimuthDeg = satAzimuth
        this.satelliteElevationDeg = satElevation
        this.snr = snrDb
        this.satelliteId = satId
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.85f

        // Draw outer circle
        canvas.drawCircle(cx, cy, radius, paintCircle)
        // Draw inner circles for elevation
        canvas.drawCircle(cx, cy, radius * 0.33f, paintCircle.apply { alpha = 100 })
        canvas.drawCircle(cx, cy, radius * 0.66f, paintCircle.apply { alpha = 100 })
        paintCircle.alpha = 255

        // Draw N/S/E/W
        canvas.drawText("N", cx, cy - radius + 40, paintNorth)
        paintText.color = Color.WHITE
        canvas.drawText("S", cx, cy + radius - 20, paintText)
        canvas.drawText("E", cx + radius - 20, cy + 10, paintText)
        canvas.drawText("W", cx - radius + 20, cy + 10, paintText)

        // Satellite position: elevation 0 = horizon = outer circle, 90 = zenith = center
        // So distance from center = radius * (1 - elevation/90)
        val elevFactor = (1f - (satelliteElevationDeg / 90f).coerceIn(0f, 1f))
        val satDist = radius * elevFactor

        // Azimuth relative to device north: satelliteAzimuth - deviceAzimuth
        val relativeAz = Math.toRadians((satelliteAzimuthDeg - azimuthDeg).toDouble())
        val satX = cx + satDist * sin(relativeAz).toFloat()
        val satY = cy - satDist * cos(relativeAz).toFloat()

        // Draw line from center to satellite
        canvas.drawLine(cx, cy, satX, satY, paintLine)

        // Draw satellite dot
        val satRadius = 20f + (snr / 40f * 15f)
        paintSatellite.color = when {
            snr >= 35 -> Color.GREEN
            snr >= 28 -> Color.YELLOW
            snr >= 20 -> Color.parseColor("#FFA500")
            else -> Color.RED
        }
        canvas.drawCircle(satX, satY, satRadius, paintSatellite)

        // Draw device orientation arrow at center
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        // Small triangle pointing north (device orientation)
        canvas.save()
        canvas.rotate(-azimuthDeg, cx, cy)
        val path = Path()
        path.moveTo(cx, cy - 30)
        path.lineTo(cx - 15, cy + 10)
        path.lineTo(cx + 15, cy + 10)
        path.close()
        canvas.drawPath(path, arrowPaint.apply { style = Paint.Style.FILL; color = Color.CYAN })
        canvas.restore()

        // Info text
        paintText.textSize = 28f
        canvas.drawText("PRN $satelliteId | SNR ${"%.1f".format(snr)} dB", cx, cy + radius + 50, paintText)
        canvas.drawText("Az %.1f° El %.1f°".format(satelliteAzimuthDeg, satelliteElevationDeg), cx, cy + radius + 85, paintText)
    }
}
