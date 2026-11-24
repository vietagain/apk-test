package com.vietvuive.ylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

class SolarFlowView(context: Context) : View(context) {

    var pvWatts: Double = 0.0
    var loadWatts: Double = 0.0
    var batteryWatts: Double = 0.0 // > 0: Đang sạc vào pin, < 0: Đang xả từ pin
    var batterySoc: String = "--"

    private var animPhase = 0f
    private var lastFrameTime = System.currentTimeMillis()

    private val circleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111827")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textAlign = Paint.Align.CENTER
    }

    private val colorSolar = Color.parseColor("#F59E0B")   // Cam vàng
    private val colorBattery = Color.parseColor("#2DD4BF") // Xanh ngọc
    private val colorCharge = Color.parseColor("#EC4899")  // Hồng sạc

    private val pathSolarToLoad = Path()
    private val pathSolarToBat = Path()
    private val pathBatToLoad = Path()
    private val pathMeasure = PathMeasure()
    private val dotPos = FloatArray(2)
    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameTime).coerceIn(10L, 100L)) / 1000f
        lastFrameTime = now
        animPhase = (animPhase + dt * 0.55f) % 1f

        val radius = (minOf(w, h) * 0.165f).coerceIn(28f, 68f)
        strokePaint.strokeWidth = (radius * 0.11f).coerceIn(3f, 7f)
        linePaint.strokeWidth = (radius * 0.065f).coerceIn(2f, 4.5f)
        textMainPaint.textSize = (radius * 0.34f).coerceIn(11f, 22f)
        textSubPaint.textSize = (radius * 0.27f).coerceIn(9f, 17f)

        // Tọa độ 3 vòng tròn chuẩn bố cục Home Assistant Energy
        val cxSolar = w * 0.36f
        val cySolar = h * 0.23f

        val cxBat = w * 0.36f
        val cyBat = h * 0.77f

        val cxLoad = w * 0.76f
        val cyLoad = h * 0.50f

        // Tính toán phân bổ dòng công suất thực tế
        val pvToLoad = minOf(pvWatts.coerceAtLeast(0.0), loadWatts.coerceAtLeast(0.0))
        val batCharge = if (batteryWatts > 15.0) batteryWatts else 0.0
        val batDischarge = if (batteryWatts < -15.0) abs(batteryWatts) else 0.0

        // 1. Dây thẳng: Mặt trời -> Pin (Sạc)
        pathSolarToBat.reset()
        pathSolarToBat.moveTo(cxSolar, cySolar + radius)
        pathSolarToBat.lineTo(cxBat, cyBat - radius)
        linePaint.color = if (batCharge > 0) colorCharge else Color.parseColor("#334155")
        canvas.drawPath(pathSolarToBat, linePaint)
        if (batCharge > 0) {
            drawMovingDot(canvas, pathSolarToBat, colorCharge, radius * 0.13f, animPhase)
        }

        // 2. Dây cong góc bo: Mặt trời -> Nhà
        val offset = radius * 0.22f
        val startX1 = cxSolar + offset
        val startY1 = cySolar + radius
        val endX1 = cxLoad - radius
        val endY1 = cyLoad - offset
        val cornerR = radius * 0.65f

        pathSolarToLoad.reset()
        pathSolarToLoad.moveTo(startX1, startY1)
        pathSolarToLoad.lineTo(startX1, endY1 - cornerR)
        pathSolarToLoad.quadTo(startX1, endY1, startX1 + cornerR, endY1)
        pathSolarToLoad.lineTo(endX1, endY1)
        linePaint.color = if (pvToLoad > 15.0) colorSolar else Color.parseColor("#334155")
        canvas.drawPath(pathSolarToLoad, linePaint)
        if (pvToLoad > 15.0) {
            drawMovingDot(canvas, pathSolarToLoad, colorSolar, radius * 0.13f, animPhase)
        }

        // 3. Dây cong góc bo: Pin -> Nhà (Xả)
        val startX2 = cxBat + offset
        val startY2 = cyBat - radius
        val endX2 = cxLoad - radius
        val endY2 = cyLoad + offset

        pathBatToLoad.reset()
        pathBatToLoad.moveTo(startX2, startY2)
        pathBatToLoad.lineTo(startX2, endY2 + cornerR)
        pathBatToLoad.quadTo(startX2, endY2, startX2 + cornerR, endY2)
        pathBatToLoad.lineTo(endX2, endY2)
        linePaint.color = if (batDischarge > 0) colorBattery else Color.parseColor("#334155")
        canvas.drawPath(pathBatToLoad, linePaint)
        if (batDischarge > 0) {
            drawMovingDot(canvas, pathBatToLoad, colorBattery, radius * 0.13f, animPhase)
        }

        // Vẽ vòng tròn MẶT TRỜI
        drawNodeCircle(canvas, cxSolar, cySolar, radius, colorSolar, "☀ Mặt trời", formatPower(pvWatts), null, 0)

        // Vẽ vòng tròn PIN
        val batDetail = if (batCharge > 0) "↑ ${formatPower(batCharge)}"
                        else if (batDischarge > 0) "↓ ${formatPower(batDischarge)}"
                        else "Chờ"
        val batDetailColor = if (batCharge > 0) colorCharge else colorBattery
        drawNodeCircle(canvas, cxBat, cyBat, radius, colorBattery, "🔋 Pin $batterySoc%", batDetail, null, batDetailColor)

        // Vẽ vòng tròn NHÀ (Viền 2 màu cam/xanh theo tỷ lệ PV và Pin cấp)
        drawLoadCircle(canvas, cxLoad, cyLoad, radius, pvToLoad, batDischarge, "🏠 Nhà", formatPower(loadWatts))

        // Tiếp tục chạy animation mượt mà nếu có dòng điện
        if (pvToLoad > 15.0 || batCharge > 0 || batDischarge > 0) {
            postInvalidateDelayed(35L)
        }
    }

    private fun drawMovingDot(canvas: Canvas, path: Path, color: Int, dotRadius: Float, phase: Float) {
        pathMeasure.setPath(path, false)
        val len = pathMeasure.length
        if (len <= 0f) return
        pathMeasure.getPosTan(len * phase, dotPos, null)
        dotPaint.color = color
        canvas.drawCircle(dotPos[0], dotPos[1], dotRadius, dotPaint)
    }

    private fun drawNodeCircle(
        canvas: Canvas, cx: Float, cy: Float, r: Float,
        borderColor: Int, label: String, valueText: String,
        subText: String?, valueColor: Int
    ) {
        canvas.drawCircle(cx, cy, r, circleBgPaint)
        strokePaint.color = borderColor
        canvas.drawCircle(cx, cy, r, strokePaint)

        // Nhãn bên ngoài vòng tròn
        val labelY = if (cy < height / 2f) cy - r - 10f else cy + r + textSubPaint.textSize + 8f
        canvas.drawText(label, cx, labelY, textSubPaint)

        // Giá trị bên trong vòng tròn
        textMainPaint.color = if (valueColor != 0) valueColor else Color.WHITE
        canvas.drawText(valueText, cx, cy + textMainPaint.textSize * 0.35f, textMainPaint)
    }

    private fun drawLoadCircle(
        canvas: Canvas, cx: Float, cy: Float, r: Float,
        solarShare: Double, batShare: Double, label: String, valueText: String
    ) {
        canvas.drawCircle(cx, cy, r, circleBgPaint)
        arcRect.set(cx - r, cy - r, cx + r, cy + r)

        val total = solarShare + batShare
        if (total <= 1.0) {
            strokePaint.color = colorBattery
            canvas.drawCircle(cx, cy, r, strokePaint)
        } else {
            val solarSweep = ((solarShare / total) * 360.0).toFloat()
            strokePaint.color = colorSolar
            canvas.drawArc(arcRect, -90f, solarSweep, false, strokePaint)
            strokePaint.color = colorBattery
            canvas.drawArc(arcRect, -90f + solarSweep, 360f - solarSweep, false, strokePaint)
        }

        canvas.drawText(label, cx, cy + r + textSubPaint.textSize + 8f, textSubPaint)
        textMainPaint.color = Color.WHITE
        canvas.drawText(valueText, cx, cy + textMainPaint.textSize * 0.35f, textMainPaint)
    }

    private fun formatPower(watts: Double): String {
        val absW = abs(watts)
        return if (absW >= 1000.0) {
            String.format("%.2f kW", absW / 1000.0)
        } else {
            "${absW.toInt()} W"
        }
    }
}
