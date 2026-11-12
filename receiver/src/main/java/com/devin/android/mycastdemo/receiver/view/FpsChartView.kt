package com.devin.android.mycastdemo.receiver.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class FpsChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val maxPoints = 60
    private val data = ArrayDeque<Float>(maxPoints)

    // ===== 预分配的绘制对象 =====
    private val path = Path()
    private val fillPath = Path()
    private val pointX = FloatArray(maxPoints)
    private val pointY = FloatArray(maxPoints)

    private val axisPaint = Paint().apply {
        color = "#80FFFFFF".toColorInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint().apply {
        color = "#33FFFFFF".toColorInt()
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    private val linePaint = Paint().apply {
        color = "#00B4FF".toColorInt()
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = "#3300B4FF".toColorInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = "#CCFFFFFF".toColorInt()
        textSize = 22f
        isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        color = "#FFFF66".toColorInt()
        textSize = 24f
        isFakeBoldText = true
        isAntiAlias = true
    }
    private val targetPaint = Paint().apply {
        color = "#FF8800".toColorInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val alertPaint = Paint().apply {
        color = "#22FF0000".toColorInt()
        style = Paint.Style.FILL
    }

    private val yMax = 60f
    private val ySteps = 3
    private var targetFps = 30f

    private val padLeft = 60f
    private val padRight = 16f
    private val padTop = 30f
    private val padBottom = 20f

    private var smoothed: Float? = null

    fun setTargetFps(fps: Float) {
        targetFps = fps; invalidate()
    }

    fun push(fps: Double) {
        val raw = fps.toFloat()
        val s = if (smoothed == null) raw else smoothed!! * 0.7f + raw * 0.3f
        smoothed = s
        data.addLast(s)
        if (data.size > maxPoints) data.removeFirst()
        invalidate()
    }

    fun clear() {
        data.clear(); smoothed = null; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom
        if (chartW <= 0 || chartH <= 0) return

        // 1. 掉帧告警底色（复用 alertPaint）
        if (data.last() < targetFps * 0.6f) {
            canvas.drawRect(padLeft, padTop, width - padRight, height - padBottom, alertPaint)
        }

        // 2. Y 轴网格与刻度
        for (i in 0..ySteps) {
            val ratio = i.toFloat() / ySteps
            val y = padTop + chartH * (1 - ratio)
            canvas.drawLine(padLeft, y, width - padRight, y, gridPaint)
            canvas.drawText("%.0f".format(yMax * ratio), 16f, y + 8f, textPaint)
        }

        // 3. 目标帧率参考线
        val targetY = padTop + chartH * (1 - targetFps / yMax)
        canvas.drawLine(padLeft, targetY, width - padRight, targetY, targetPaint)
        canvas.drawText("%.0f".format(targetFps), 16f, targetY - 6f, textPaint)

        // 4. 坐标轴
        canvas.drawLine(padLeft, padTop, padLeft, height - padBottom, axisPaint)
        canvas.drawLine(padLeft, height - padBottom, width - padRight, height - padBottom, axisPaint)

        // 5. 计算坐标到复用数组（不再创建 Pair / ArrayList）
        val step = chartW / (maxPoints - 1)
        val n = data.size
        var idx = 0
        for (v in data) {
            pointX[idx] = padLeft + idx * step
            pointY[idx] = padTop + chartH * (1 - (v / yMax).coerceIn(0f, 1f))
            idx++
        }

        // 6. 复用 Path，先 reset
        if (n >= 2) {
            path.reset()
            fillPath.reset()

            path.moveTo(pointX[0], pointY[0])
            fillPath.moveTo(pointX[0], height - padBottom)
            fillPath.lineTo(pointX[0], pointY[0])

            for (i in 1 until n) {
                val p0x = pointX[i - 1];
                val p0y = pointY[i - 1]
                val p1x = pointX[i];
                val p1y = pointY[i]
                val prevX = if (i >= 2) pointX[i - 2] else p0x
                val prevY = if (i >= 2) pointY[i - 2] else p0y
                val nextX = if (i + 1 < n) pointX[i + 1] else p1x
                val nextY = if (i + 1 < n) pointY[i + 1] else p1y

                path.cubicTo(
                    p0x + (p1x - prevX) / 6f, p0y + (p1y - prevY) / 6f,
                    p1x - (nextX - p0x) / 6f, p1y - (nextY - p0y) / 6f,
                    p1x, p1y
                )
                fillPath.cubicTo(
                    p0x + (p1x - prevX) / 6f, p0y + (p1y - prevY) / 6f,
                    p1x - (nextX - p0x) / 6f, p1y - (nextY - p0y) / 6f,
                    p1x, p1y
                )
            }

            fillPath.lineTo(pointX[n - 1], height - padBottom)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, linePaint)
        }

        // 7. 当前值标注
        val last = data.last()
        val lastX = pointX[n - 1]
        val lastY = pointY[n - 1]
        valuePaint.color = when {
            last < targetFps * 0.6f -> Color.RED
            last < targetFps * 0.85f -> "#FFCC00".toColorInt()
            else -> "#00FF88".toColorInt()
        }
        canvas.drawCircle(lastX, lastY, 4f, valuePaint)
        canvas.drawText(
            "%.1f fps".format(last),
            (lastX - 70f).coerceAtLeast(padLeft),
            (lastY - 10f).coerceAtLeast(padTop + 20f),
            valuePaint
        )
    }
}