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

    private var azimuthDeg: Float = 0f
    private var satelliteAzimuthDeg: Float = 0f
    private var satelliteElevationDeg: Float = 45f
    private var snr: Float = 0f
    private var satelliteId: Int = 0
    private var searchMode: Int = 0

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF6200EE")
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.GRAY
        alpha = 120
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val paintSatellite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val paintNorth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val paintUser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val paintHorizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FF0000")
        style = Paint.Style.FILL
    }

    fun updateCompass(deviceAzimuth: Float, satAzimuth: Float, satElevation: Float, snrDb: Float, satId: Int, searchMode: Int = 2) {
        this.azimuthDeg = deviceAzimuth
        this.satelliteAzimuthDeg = satAzimuth
        this.satelliteElevationDeg = satElevation
        this.snr = snrDb
        this.satelliteId = satId
        this.searchMode = searchMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.80f

        // Background
        canvas.drawColor(Color.parseColor("#FF121212"))

        // Horizon gradient - outer red zone for low elevation
        canvas.drawCircle(cx, cy, radius, paintHorizon)

        // Elevation circles: 0° = horizon (outer), 30°, 60°, 90° = zenith (center)
        for (i in 1..3) {
            val r = radius * (i / 3f)
            canvas.drawCircle(cx, cy, r, paintGrid)
            // Label elevation
            paintText.textSize = 20f
            paintText.color = Color.GRAY
            val elevLabel = "${90 - i*30}°"
            canvas.drawText(elevLabel, cx + r + 10, cy, paintText)
        }

        // Outer circle - horizon
        paintCircle.strokeWidth = 4f
        paintCircle.color = Color.parseColor("#FF6200EE")
        canvas.drawCircle(cx, cy, radius, paintCircle)

        // Degree marks every 30°
        paintText.textSize = 18f
        paintText.color = Color.LTGRAY
        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = cx + radius * sin(rad).toFloat()
            val y1 = cy - radius * cos(rad).toFloat()
            val x2 = cx + (radius + 20) * sin(rad).toFloat()
            val y2 = cy - (radius + 20) * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paintGrid)
            if (deg % 90 == 0) {
                val label = when(deg) {
                    0 -> "N 0°"
                    90 -> "E 90°"
                    180 -> "S 180°"
                    270 -> "W 270°"
                    else -> "$deg°"
                }
                canvas.drawText(label, x2, y2, paintText)
            }
        }

        // N/S/E/W labels with distinct colors
        paintNorth.color = Color.GREEN
        canvas.drawText("N", cx, cy - radius + 45, paintNorth)
        paintText.color = Color.WHITE
        paintText.textSize = 28f
        canvas.drawText("S", cx, cy + radius - 15, paintText)
        canvas.drawText("E", cx + radius - 15, cy + 10, paintText)
        canvas.drawText("W", cx - radius + 15, cy + 10, paintText)

        // Satellite position
        val elevFactor = (1f - (satelliteElevationDeg / 90f).coerceIn(0f, 1f))
        val satDist = radius * elevFactor
        val relativeAz = Math.toRadians((satelliteAzimuthDeg - azimuthDeg).toDouble())
        val satX = cx + satDist * sin(relativeAz).toFloat()
        val satY = cy - satDist * cos(relativeAz).toFloat()

        // Draw trail line from center to satellite - animated dashed
        paintLine.color = Color.YELLOW
        paintLine.pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        canvas.drawLine(cx, cy, satX, satY, paintLine)
        paintLine.pathEffect = null

        // Draw satellite with glow based on SNR
        val satRadius = 18f + (snr / 40f * 22f)
        val qualityColor = when {
            snr >= 35 -> Color.GREEN
            snr >= 28 -> Color.YELLOW
            snr >= 20 -> Color.parseColor("#FFA500")
            else -> Color.RED
        }
        // Glow
        paintSatellite.color = qualityColor
        paintSatellite.alpha = 80
        canvas.drawCircle(satX, satY, satRadius + 10, paintSatellite)
        paintSatellite.alpha = 255
        paintSatellite.color = qualityColor
        canvas.drawCircle(satX, satY, satRadius, paintSatellite)
        // Border
        paintCircle.color = Color.WHITE
        paintCircle.strokeWidth = 2f
        canvas.drawCircle(satX, satY, satRadius, paintCircle)

        // Satellite PRN label
        paintText.color = Color.WHITE
        paintText.textSize = 20f
        paintText.isFakeBoldText = true
        canvas.drawText("PRN $satelliteId", satX, satY - satRadius - 10, paintText)
        paintText.isFakeBoldText = false

        // Draw user at center - device orientation
        canvas.save()
        canvas.rotate(-azimuthDeg, cx, cy)
        // User dot
        canvas.drawCircle(cx, cy, 12f, paintUser)
        // Arrow pointing north
        val arrowPath = Path()
        arrowPath.moveTo(cx, cy - 35)
        arrowPath.lineTo(cx - 12, cy + 8)
        arrowPath.lineTo(cx + 12, cy + 8)
        arrowPath.close()
        paintUser.color = Color.CYAN
        canvas.drawPath(arrowPath, paintUser)
        // North indicator line
        canvas.drawLine(cx, cy, cx, cy - radius + 20, Paint().apply {
            color = Color.GREEN
            strokeWidth = 3f
            alpha = 150
        })
        canvas.restore()

        // Bottom info panel
        val infoY = cy + radius + 35
        paintText.textSize = 22f
        paintText.color = Color.WHITE
        paintText.textAlign = Paint.Align.CENTER
        val searchModeText = if (searchMode == 2) "Direct Send" else "Search Required"
        canvas.drawText("📡 PRN $satelliteId | 📶 %.1f dB | 🎯 %s".format(snr, searchModeText), cx, infoY, paintText)
        paintText.textSize = 20f
        canvas.drawText("Az %.1f° El %.1f° | Device %.1f°".format(satelliteAzimuthDeg, satelliteElevationDeg, azimuthDeg), cx, infoY + 30, paintText)
        
        val quality = when {
            snr >= 35 -> "EXCELLENT ✅"
            snr >= 28 -> "GOOD 🟡"
            snr >= 20 -> "FAIR 🟠"
            else -> "POOR 🔴 - Move to open sky"
        }
        paintText.color = qualityColor
        canvas.drawText("Quality: $quality", cx, infoY + 60, paintText)

        // Instruction if low elevation
        if (satelliteElevationDeg < 20) {
            paintText.color = Color.RED
            paintText.textSize = 18f
            canvas.drawText("⚠️ Low elevation - point to open sky!", cx, infoY + 85, paintText)
        } else if (satelliteElevationDeg > 60) {
            paintText.color = Color.GREEN
            paintText.textSize = 18f
            canvas.drawText("✅ Good elevation - hold steady!", cx, infoY + 85, paintText)
        }
    }
}
