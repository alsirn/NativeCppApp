package com.example.nativecppapp.ui.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.example.nativecppapp.NativeEngineBridge
import com.example.nativecppapp.ui.theme.MotionTheme
import kotlin.math.hypot

class GraphEditorView(context: Context) : View(context) {

    // Bezier control handles (normalized in [0, 1])
    var outHandleX: Double = 0.42
    var outHandleY: Double = 0.0
    var inHandleX: Double = 0.58
    var inHandleY: Double = 1.0

    var propertyName: String = "posX (Posisi X)"
        set(value) {
            field = value
            invalidate()
        }

    var onCurveChanged: ((Double, Double, Double, Double) -> Unit)? = null

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2527272A")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val axisPaint = Paint().apply {
        color = MotionTheme.BORDER_SUBTLE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val curvePaint = Paint().apply {
        color = MotionTheme.CURVE_X
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val tangentLinePaint = Paint().apply {
        color = MotionTheme.CURVE_HANDLE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }

    private val puckPaint = Paint().apply {
        color = MotionTheme.TEXT_PRIMARY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val puckStrokePaint = Paint().apply {
        color = MotionTheme.CANVAS_BG
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = MotionTheme.TEXT_SECONDARY
        textSize = 24f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val graphBounds = RectF()
    private var draggedPuck: Int = 0 // 0 = none, 1 = outHandle (P1), 2 = inHandle (P2)
    private val puckHitRadius = 55f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padX = 60f
        val padY = 70f
        graphBounds.set(padX, padY, w - padX, h - padY)
    }

    fun applyPreset(preset: String) {
        when (preset) {
            "LINEAR" -> {
                outHandleX = 0.0; outHandleY = 0.0
                inHandleX = 1.0; inHandleY = 1.0
            }
            "EASE_IN" -> {
                outHandleX = 0.42; outHandleY = 0.0
                inHandleX = 1.0; inHandleY = 1.0
            }
            "EASE_OUT" -> {
                outHandleX = 0.0; outHandleY = 0.0
                inHandleX = 0.58; inHandleY = 1.0
            }
            "EASE_IN_OUT" -> {
                outHandleX = 0.42; outHandleY = 0.0
                inHandleX = 0.58; inHandleY = 1.0
            }
            "HOLD" -> {
                outHandleX = 0.0; outHandleY = 0.0
                inHandleX = 0.0; inHandleY = 0.0
            }
        }
        onCurveChanged?.invoke(outHandleX, outHandleY, inHandleX, inHandleY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Graph Backdrop
        canvas.drawColor(MotionTheme.SURFACE_CONTAINER)

        // 2. Draw Grid Lines (Time vs Value)
        val cols = 4
        val rows = 4
        for (c in 0..cols) {
            val gx = graphBounds.left + (c.toFloat() / cols) * graphBounds.width()
            canvas.drawLine(gx, graphBounds.top, gx, graphBounds.bottom, gridPaint)
        }
        for (r in 0..rows) {
            val gy = graphBounds.top + (r.toFloat() / rows) * graphBounds.height()
            canvas.drawLine(graphBounds.left, gy, graphBounds.right, gy, gridPaint)
        }

        // Bounding Axis Frame
        canvas.drawRect(graphBounds, axisPaint)

        // Convert Normalized Coordinates [0, 1] to Canvas Screen Coordinates
        // Origin (0, 0) is at bottom-left, (1, 1) is at top-right
        fun toScreenX(nx: Double): Float = (graphBounds.left + nx * graphBounds.width()).toFloat()
        fun toScreenY(ny: Double): Float = (graphBounds.bottom - ny * graphBounds.height()).toFloat()

        val p0X = toScreenX(0.0)
        val p0Y = toScreenY(0.0)
        val p3X = toScreenX(1.0)
        val p3Y = toScreenY(1.0)

        val p1X = toScreenX(outHandleX)
        val p1Y = toScreenY(outHandleY)
        val p2X = toScreenX(inHandleX)
        val p2Y = toScreenY(inHandleY)

        // 3. Draw Tangent Handle Arms
        canvas.drawLine(p0X, p0Y, p1X, p1Y, tangentLinePaint)
        canvas.drawLine(p3X, p3Y, p2X, p2Y, tangentLinePaint)

        // 4. Sample and Draw Cubic Bezier Curve using C++ Engine solver
        val curvePath = Path()
        curvePath.moveTo(p0X, p0Y)
        val samples = 64
        for (i in 1..samples) {
            val u = i.toDouble() / samples
            val easedVal = NativeEngineBridge.nativeSolveCubicBezier(outHandleX, outHandleY, inHandleX, inHandleY, u)
            val sx = toScreenX(u)
            val sy = toScreenY(easedVal)
            curvePath.lineTo(sx, sy)
        }
        canvas.drawPath(curvePath, curvePaint)

        // 5. Draw Tangent Control Pucks
        // P1 (Out-tangent puck)
        canvas.drawCircle(p1X, p1Y, 18f, puckPaint)
        canvas.drawCircle(p1X, p1Y, 18f, puckStrokePaint)

        // P2 (In-tangent puck)
        canvas.drawCircle(p2X, p2Y, 18f, puckPaint)
        canvas.drawCircle(p2X, p2Y, 18f, puckStrokePaint)

        // 6. Draw Coordinate Readout & Title
        textPaint.color = MotionTheme.TEXT_PRIMARY
        canvas.drawText("GRAFIK: $propertyName", graphBounds.left, 42f, textPaint)

        textPaint.color = MotionTheme.TEXT_MUTED
        val readout = String.format("P1: (%.2f, %.2f)  |  P2: (%.2f, %.2f)", outHandleX, outHandleY, inHandleX, inHandleY)
        canvas.drawText(readout, graphBounds.left, height - 24f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        fun toScreenX(nx: Double): Float = (graphBounds.left + nx * graphBounds.width()).toFloat()
        fun toScreenY(ny: Double): Float = (graphBounds.bottom - ny * graphBounds.height()).toFloat()
        fun toNormX(sx: Float): Double = ((sx - graphBounds.left) / graphBounds.width()).toDouble().coerceIn(0.0, 1.0)
        fun toNormY(sy: Float): Double = ((graphBounds.bottom - sy) / graphBounds.height()).toDouble().coerceIn(-0.5, 1.5)

        val p1X = toScreenX(outHandleX)
        val p1Y = toScreenY(outHandleY)
        val p2X = toScreenX(inHandleX)
        val p2Y = toScreenY(inHandleY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val distP1 = hypot(event.x - p1X, event.y - p1Y)
                val distP2 = hypot(event.x - p2X, event.y - p2Y)

                draggedPuck = when {
                    distP1 < puckHitRadius && distP1 <= distP2 -> 1
                    distP2 < puckHitRadius -> 2
                    else -> 0
                }
                return draggedPuck != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPuck == 1) {
                    outHandleX = toNormX(event.x)
                    outHandleY = toNormY(event.y)
                    onCurveChanged?.invoke(outHandleX, outHandleY, inHandleX, inHandleY)
                    invalidate()
                    return true
                } else if (draggedPuck == 2) {
                    inHandleX = toNormX(event.x)
                    inHandleY = toNormY(event.y)
                    onCurveChanged?.invoke(outHandleX, outHandleY, inHandleX, inHandleY)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedPuck = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
