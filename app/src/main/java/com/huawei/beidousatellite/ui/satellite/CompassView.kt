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

    private var deviceAzimuth: Float = 0f
    private var satelliteAzimuth: Float = 45f
    private var satelliteElevation: Float = 45f
    private var snr: Float = 25f
    private var satelliteId: Int = 7
    private var searchMode: Int = 2

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#FF6200EE") }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.GRAY; alpha = 100; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
    private val paintSat = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER }
    private val paintNorth = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN; textSize = 36f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val paintUser = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.FILL }
    private val paintHorizon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15FF0000"); style = Paint.Style.FILL }

    fun updateCompass(deviceAz: Float, satAz: Float, satEl: Float, snrDb: Float, satId: Int, mode: Int = 2) {
        deviceAzimuth = deviceAz
        satelliteAzimuth = satAz
        satelliteElevation = satEl
        snr = snrDb
        satelliteId = satId
        searchMode = mode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.78f

        canvas.drawColor(Color.parseColor("#FF121212"))
        canvas.drawCircle(cx, cy, radius, paintHorizon)

        for (i in 1..3) {
            val r = radius * (i / 3f)
            canvas.drawCircle(cx, cy, r, paintGrid)
            paintText.textSize = 18f
            paintText.color = Color.GRAY
            canvas.drawText("${90 - i*30}°", cx + r + 10, cy, paintText)
        }

        paintCircle.strokeWidth = 4f
        paintCircle.color = Color.parseColor("#FF6200EE")
        canvas.drawCircle(cx, cy, radius, paintCircle)

        paintText.textSize = 16f
        paintText.color = Color.LTGRAY
        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = cx + radius * sin(rad).toFloat()
            val y1 = cy - radius * cos(rad).toFloat()
            val x2 = cx + (radius + 18) * sin(rad).toFloat()
            val y2 = cy - (radius + 18) * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paintGrid)
        }

        paintNorth.color = Color.GREEN
        paintText.textSize = 28f
        canvas.drawText("N 0°", cx, cy - radius + 40, paintNorth)
        paintText.color = Color.WHITE
        canvas.drawText("S 180°", cx, cy + radius - 15, paintText)
        canvas.drawText("E 90°", cx + radius - 15, cy + 8, paintText)
        canvas.drawText("W 270°", cx - radius + 15, cy + 8, paintText)

        // Satellite position calculation:
        // World azimuth of satellite is satelliteAzimuth.
        // Device azimuth is where phone top points.
        // Relative azimuth = satelliteAzimuth - deviceAzimuth => where satellite appears relative to device forward.
        // Example: satellite at 90 (east), device at 0 (north) => relative 90 => satellite to the right (east).
        // When device rotates clockwise to 90 (east), relative = 0 => satellite in front (top) -> correct.
        val relativeAz = (satelliteAzimuth - deviceAzimuth + 360f) % 360f
        val elevFactor = (1f - (satelliteElevation / 90f).coerceIn(0f, 1f))
        val satDist = radius * elevFactor
        val rad = Math.toRadians(relativeAz.toDouble())
        val satX = cx + satDist * sin(rad).toFloat()
        val satY = cy - satDist * cos(rad).toFloat()

        // Line from center to satellite
        paintLine.color = Color.YELLOW
        paintLine.pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        canvas.drawLine(cx, cy, satX, satY, paintLine)
        paintLine.pathEffect = null

        // Satellite dot with SNR size
        val satRadius = 16f + (snr / 40f * 20f)
        val qualityColor = when {
            snr >= 35 -> Color.GREEN
            snr >= 28 -> Color.YELLOW
            snr >= 20 -> Color.parseColor("#FFA500")
            else -> Color.RED
        }
        paintSat.color = qualityColor
        paintSat.alpha = 90
        canvas.drawCircle(satX, satY, satRadius + 12, paintSat)
        paintSat.alpha = 255
        paintSat.color = qualityColor
        canvas.drawCircle(satX, satY, satRadius, paintSat)
        paintCircle.color = Color.WHITE
        paintCircle.strokeWidth = 2f
        canvas.drawCircle(satX, satY, satRadius, paintCircle)

        paintText.color = Color.WHITE
        paintText.textSize = 18f
        paintText.isFakeBoldText = true
        canvas.drawText("PRN $satelliteId", satX, satY - satRadius - 8, paintText)
        paintText.isFakeBoldText = false

        // User at center - always pointing up (device forward is up)
        // Do NOT rotate with device azimuth - device forward is always up in this view
        canvas.drawCircle(cx, cy, 10f, paintUser)
        // Small forward arrow (up)
        val arrowPath = Path()
        arrowPath.moveTo(cx, cy - 28)
        arrowPath.lineTo(cx - 10, cy + 6)
        arrowPath.lineTo(cx + 10, cy + 6)
        arrowPath.close()
        paintUser.color = Color.CYAN
        canvas.drawPath(arrowPath, paintUser)

        // Info panel
        val infoY = cy + radius + 32
        paintText.textSize = 20f
        paintText.color = Color.WHITE
        val modeText = if (searchMode == 2) "Direct Send" else "Search Needed"
        canvas.drawText("📡 PRN $satelliteId | 📶 %.1f dB | %s".format(snr, modeText), cx, infoY, paintText)
        paintText.textSize = 18f
        canvas.drawText("Sat Az %.1f° El %.1f° | Dev Az %.1f° | Rel %.1f°".format(satelliteAzimuth, satelliteElevation, deviceAzimuth, relativeAz), cx, infoY + 26, paintText)

        val quality = when {
            snr >= 35 -> "EXCELLENT ✅ - Hold steady!"
            snr >= 28 -> "GOOD 🟡 - Good signal"
            snr >= 20 -> "FAIR 🟠 - Move to open sky"
            else -> "POOR 🔴 - Move to open sky, avoid buildings"
        }
        paintText.color = qualityColor
        canvas.drawText("Quality: $quality", cx, infoY + 52, paintText)

        // Guidance
        if (satDist > radius * 0.7f || satelliteElevation < 20) {
            paintText.color = Color.RED
            paintText.textSize = 16f
            canvas.drawText("⚠️ Low elevation - tilt phone to open sky! Rel Az ${"%.0f".format(relativeAz)}°", cx, infoY + 76, paintText)
        } else if (satDist < radius * 0.3f) {
            paintText.color = Color.GREEN
            canvas.drawText("✅ Aligned! Hold steady to send.", cx, infoY + 76, paintText)
        } else {
            paintText.color = Color.YELLOW
            canvas.drawText("➡️ Turn ${if (relativeAz > 180) "left" else "right"} %.0f° to align".format(minOf(relativeAz, 360-relativeAz)), cx, infoY + 76, paintText)
        }
    }
}
