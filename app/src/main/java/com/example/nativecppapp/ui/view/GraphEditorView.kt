package com.example.nativecppapp.ui.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.example.nativecppapp.NativeEngineBridge
import com.example.nativecppapp.ui.theme.MotionTheme
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max

class GraphEditorView(context: Context) : View(context) {

    // Bezier control handles (normalized in [0, 1], can overshoot in [-0.5, 1.5])
    var outHandleX: Double = 0.42
    var outHandleY: Double = 0.0
    var inHandleX: Double = 0.58
    var inHandleY: Double = 1.0

    // Zoom & Pan state
    var zoomLevel: Float = 1.0f // Range: 0.4f (Zoom Out) to 3.5f (Zoom In)
        set(value) {
            field = value.coerceIn(0.4f, 3.5f)
            onZoomChanged?.invoke(field)
            invalidate()
        }

    var panX: Double = 0.0
    var panY: Double = 0.0

    var onZoomChanged: ((Float) -> Unit)? = null
    var onCurveChanged: ((Double, Double, Double, Double) -> Unit)? = null

    var propertyName: String = "posX (Posisi X)"
        set(value) {
            field = value
            invalidate()
        }

    private val density get() = context.resources.displayMetrics.density

    // Paint configurations
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#25FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        isAntiAlias = true
    }

    private val gridAccentPaint = Paint().apply {
        color = Color.parseColor("#45FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        isAntiAlias = true
    }

    private val unitBoxPaint = Paint().apply {
        color = Color.parseColor("#5038BDF8") // Cyan dashed box for [0, 1] unit range
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        isAntiAlias = true
    }

    private val axisPaint = Paint().apply {
        color = MotionTheme.BORDER_SUBTLE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }

    private val curvePaint = Paint().apply {
        color = Color.parseColor("#38BDF8") // Vibrant cyan curve
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val tangentLinePaint1 = Paint().apply {
        color = Color.parseColor("#803B82F6") // Blue tangent arm
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val tangentLinePaint2 = Paint().apply {
        color = Color.parseColor("#80A855F7") // Purple tangent arm
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val puck1Paint = Paint().apply {
        color = Color.parseColor("#3B82F6") // Blue P1 puck
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val puck2Paint = Paint().apply {
        color = Color.parseColor("#A855F7") // Purple P2 puck
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val puckStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        isAntiAlias = true
    }

    private val glowPaint1 = Paint().apply {
        color = Color.parseColor("#503B82F6")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val glowPaint2 = Paint().apply {
        color = Color.parseColor("#50A855F7")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = MotionTheme.TEXT_SECONDARY
        textSize = 10f * density
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    // Geometry rects
    private val graphBounds = RectF()
    private val zoomBarBounds = RectF()
    private val btnZoomInBounds = RectF()
    private val btnZoomOutBounds = RectF()
    private val btnResetBounds = RectF()
    private val sliderTrackBounds = RectF()

    // Interaction state
    private var draggedPuck: Int = 0 // 0 = none, 1 = outHandle (P1), 2 = inHandle (P2)
    private var isDraggingZoom: Boolean = false
    private var isPanningGraph: Boolean = false
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var initialPinchDist: Float = 0f
    private var basePinchZoom: Float = 1f

    private val puckHitRadius get() = 44f * density // 44dp generous touch area
    private val puckRadius get() = 11f * density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padY = 24f * density

        // 1. Vertical Zoom Bar on the Left (in the green circled area)
        val zoomBarW = 38f * density
        val zoomBarLeft = 10f * density
        val zoomBarTop = padY
        val zoomBarBottom = h - padY
        zoomBarBounds.set(zoomBarLeft, zoomBarTop, zoomBarLeft + zoomBarW, zoomBarBottom)

        // Sub-elements of the Zoom Bar
        val btnH = 34f * density
        btnZoomInBounds.set(zoomBarBounds.left, zoomBarBounds.top, zoomBarBounds.right, zoomBarBounds.top + btnH)

        val resetH = 22f * density
        btnResetBounds.set(
            zoomBarBounds.left + 3f * density,
            zoomBarBounds.bottom - resetH - 2f * density,
            zoomBarBounds.right - 3f * density,
            zoomBarBounds.bottom - 2f * density
        )

        btnZoomOutBounds.set(
            zoomBarBounds.left,
            btnResetBounds.top - btnH - 2f * density,
            zoomBarBounds.right,
            btnResetBounds.top - 2f * density
        )

        val trackTop = btnZoomInBounds.bottom + 20f * density // Space for zoom readout
        val trackBottom = btnZoomOutBounds.top - 8f * density
        val trackCenterX = zoomBarBounds.centerX()
        sliderTrackBounds.set(trackCenterX - 2f * density, trackTop, trackCenterX + 2f * density, trackBottom)

        // 2. Centered 1:1 Square Graph Area to the right of the Zoom Bar
        val graphAreaLeft = zoomBarBounds.right + 12f * density
        val availW = (w - graphAreaLeft - 16f * density).coerceAtLeast(80f)
        val availH = (h - padY * 2f).coerceAtLeast(80f)
        val side = min(availW, availH)
        val left = graphAreaLeft + (availW - side) * 0.5f
        val top = (h - side) * 0.5f
        graphBounds.set(left, top, left + side, top + side)
    }

    /**
     * Calculates the visible normalized coordinate range [xMin, xMax, yMin, yMax]
     * based on zoomLevel and pan offsets.
     */
    fun getViewportBounds(): DoubleArray {
        val halfW = 0.5 / zoomLevel
        val halfH = 0.5 / zoomLevel
        val centerX = 0.5 + panX
        val centerY = 0.5 + panY
        val xMin = centerX - halfW
        val xMax = centerX + halfW
        val yMin = centerY - halfH
        val yMax = centerY + halfH
        return doubleArrayOf(xMin, xMax, yMin, yMax)
    }

    fun toScreenX(nx: Double): Float {
        val vp = getViewportBounds()
        val t = (nx - vp[0]) / (vp[1] - vp[0])
        return (graphBounds.left + t * graphBounds.width()).toFloat()
    }

    fun toScreenY(ny: Double): Float {
        val vp = getViewportBounds()
        val t = (ny - vp[2]) / (vp[3] - vp[2])
        return (graphBounds.bottom - t * graphBounds.height()).toFloat()
    }

    fun toNormX(sx: Float): Double {
        val vp = getViewportBounds()
        val t = ((sx - graphBounds.left) / graphBounds.width()).toDouble()
        return vp[0] + t * (vp[1] - vp[0])
    }

    fun toNormY(sy: Float): Double {
        val vp = getViewportBounds()
        val t = ((graphBounds.bottom - sy) / graphBounds.height()).toDouble()
        return vp[2] + t * (vp[3] - vp[2])
    }

    private fun getSliderThumbY(): Float {
        val trackH = sliderTrackBounds.height()
        if (trackH <= 0f) return sliderTrackBounds.centerY()
        val frac = if (zoomLevel <= 1.0f) {
            ((zoomLevel - 0.4f) / (1.0f - 0.4f)) * 0.5f
        } else {
            0.5f + ((zoomLevel - 1.0f) / (3.5f - 1.0f)) * 0.5f
        }.coerceIn(0f, 1f)
        return sliderTrackBounds.bottom - frac * trackH
    }

    private fun setZoomFromSliderY(touchY: Float) {
        val trackH = sliderTrackBounds.height()
        if (trackH <= 0f) return
        val frac = ((sliderTrackBounds.bottom - touchY) / trackH).coerceIn(0f, 1f)
        val newZoom = if (frac <= 0.5f) {
            0.4f + (frac / 0.5f) * 0.6f
        } else {
            1.0f + ((frac - 0.5f) / 0.5f) * 2.5f
        }
        zoomLevel = newZoom
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
            "BOUNCE" -> {
                outHandleX = 0.34; outHandleY = 1.56
                inHandleX = 0.64; inHandleY = 1.0
                // Auto zoom-out slightly if needed to keep overshoot visible
                if (zoomLevel > 0.75f) {
                    zoomLevel = 0.75f
                }
            }
            "FAST_SNAP" -> {
                outHandleX = 0.16; outHandleY = 1.0
                inHandleX = 0.30; inHandleY = 1.0
            }
        }
        onCurveChanged?.invoke(outHandleX, outHandleY, inHandleX, inHandleY)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ==========================================
        // 1. DRAW VERTICAL ZOOM BAR (Left Section)
        // ==========================================
        val pillBgPaint = Paint().apply {
            color = Color.parseColor("#18181B")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val pillBorderPaint = Paint().apply {
            color = Color.parseColor("#2E2E33")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            isAntiAlias = true
        }
        canvas.drawRoundRect(zoomBarBounds, 14f * density, 14f * density, pillBgPaint)
        canvas.drawRoundRect(zoomBarBounds, 14f * density, 14f * density, pillBorderPaint)

        // 1a. Zoom In Button (+)
        val btnTextPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 18f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("+", btnZoomInBounds.centerX(), btnZoomInBounds.centerY() + 6f * density, btnTextPaint)

        // 1b. Zoom Level Readout Badge
        val readoutPaint = Paint().apply {
            color = if (kotlin.math.abs(zoomLevel - 1f) < 0.05f) Color.WHITE else Color.parseColor("#38BDF8")
            textSize = 9.5f * density
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            String.format("%.1fx", zoomLevel),
            zoomBarBounds.centerX(),
            btnZoomInBounds.bottom + 13f * density,
            readoutPaint
        )

        // 1c. Slider Track
        val trackBgPaint = Paint().apply {
            color = Color.parseColor("#27272A")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.drawLine(
            sliderTrackBounds.centerX(), sliderTrackBounds.top,
            sliderTrackBounds.centerX(), sliderTrackBounds.bottom,
            trackBgPaint
        )

        // Active Track (from center 1.0x to current thumb)
        val centerTrackY = sliderTrackBounds.centerY()
        val thumbY = getSliderThumbY()
        val activeTrackPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.drawLine(
            sliderTrackBounds.centerX(), centerTrackY,
            sliderTrackBounds.centerX(), thumbY,
            activeTrackPaint
        )

        // Draggable Thumb Knob
        if (isDraggingZoom) {
            val thumbGlowPaint = Paint().apply {
                color = Color.parseColor("#5038BDF8")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(sliderTrackBounds.centerX(), thumbY, 14f * density, thumbGlowPaint)
        }
        val thumbPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val thumbDotPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(sliderTrackBounds.centerX(), thumbY, 7.5f * density, thumbPaint)
        canvas.drawCircle(sliderTrackBounds.centerX(), thumbY, 3.5f * density, thumbDotPaint)

        // 1d. Zoom Out Button (−)
        canvas.drawText("−", btnZoomOutBounds.centerX(), btnZoomOutBounds.centerY() + 6f * density, btnTextPaint)

        // 1e. Reset Button (1:1)
        val isDefaultZoom = kotlin.math.abs(zoomLevel - 1f) < 0.05f && kotlin.math.abs(panX) < 0.01 && kotlin.math.abs(panY) < 0.01
        val resetBgPaint = Paint().apply {
            color = if (isDefaultZoom) Color.parseColor("#2563EB") else Color.parseColor("#27272A")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(btnResetBounds, 4f * density, 4f * density, resetBgPaint)

        val resetTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("1:1", btnResetBounds.centerX(), btnResetBounds.centerY() + 3.5f * density, resetTextPaint)

        // ==========================================
        // 2. DRAW 1:1 GRAPH EDITOR (Right Section)
        // ==========================================
        val bgRect = RectF(graphBounds.left - 4f, graphBounds.top - 4f, graphBounds.right + 4f, graphBounds.bottom + 4f)
        val graphBackdropPaint = Paint().apply {
            color = Color.parseColor("#121214")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(bgRect, 8f * density, 8f * density, graphBackdropPaint)

        // Bounding Axis Frame
        canvas.drawRoundRect(bgRect, 8f * density, 8f * density, axisPaint)

        // Clip to graph box so curve and grid never overflow outside
        canvas.save()
        canvas.clipRect(graphBounds)

        val vp = getViewportBounds()
        val xMin = vp[0]
        val xMax = vp[1]
        val yMin = vp[2]
        val yMax = vp[3]

        // 2a. Dynamic Grid Lines based on Zoom
        val step = if (zoomLevel > 1.8f) 0.125 else 0.25
        var gx = (kotlin.math.floor(xMin / step) * step)
        while (gx <= xMax + 0.001) {
            val sx = toScreenX(gx)
            val isAxis = kotlin.math.abs(gx) < 0.01 || kotlin.math.abs(gx - 1.0) < 0.01
            canvas.drawLine(sx, graphBounds.top, sx, graphBounds.bottom, if (isAxis) gridAccentPaint else gridPaint)
            gx += step
        }

        var gy = (kotlin.math.floor(yMin / step) * step)
        while (gy <= yMax + 0.001) {
            val sy = toScreenY(gy)
            val isAxis = kotlin.math.abs(gy) < 0.01 || kotlin.math.abs(gy - 1.0) < 0.01
            canvas.drawLine(graphBounds.left, sy, graphBounds.right, sy, if (isAxis) gridAccentPaint else gridPaint)
            gy += step
        }

        // 2b. Draw Standard [0, 1] Unit Reference Box (Visible when zoomed out)
        if (zoomLevel < 0.98f || kotlin.math.abs(panX) > 0.05 || kotlin.math.abs(panY) > 0.05) {
            val refL = toScreenX(0.0)
            val refR = toScreenX(1.0)
            val refT = toScreenY(1.0)
            val refB = toScreenY(0.0)
            canvas.drawRect(refL, refT, refR, refB, unitBoxPaint)
        }

        // Screen points
        val p0X = toScreenX(0.0)
        val p0Y = toScreenY(0.0)
        val p3X = toScreenX(1.0)
        val p3Y = toScreenY(1.0)

        val p1X = toScreenX(outHandleX)
        val p1Y = toScreenY(outHandleY)
        val p2X = toScreenX(inHandleX)
        val p2Y = toScreenY(inHandleY)

        // 2c. Tangent Handle Arms
        canvas.drawLine(p0X, p0Y, p1X, p1Y, tangentLinePaint1)
        canvas.drawLine(p3X, p3Y, p2X, p2Y, tangentLinePaint2)

        // 2d. Bezier Curve using C++ Engine solver
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

        // 2e. Anchor points at (0, 0) and (1, 1)
        val anchorPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(p0X, p0Y, 4f * density, anchorPaint)
        canvas.drawCircle(p3X, p3Y, 4f * density, anchorPaint)

        // 2f. Tangent Control Pucks with Active Glowing halos
        // P1 (Out-tangent puck - Blue)
        if (draggedPuck == 1) {
            canvas.drawCircle(p1X, p1Y, puckRadius * 2.5f, glowPaint1)
        }
        canvas.drawCircle(p1X, p1Y, puckRadius, puck1Paint)
        canvas.drawCircle(p1X, p1Y, puckRadius, puckStrokePaint)

        // P2 (In-tangent puck - Purple)
        if (draggedPuck == 2) {
            canvas.drawCircle(p2X, p2Y, puckRadius * 2.5f, glowPaint2)
        }
        canvas.drawCircle(p2X, p2Y, puckRadius, puck2Paint)
        canvas.drawCircle(p2X, p2Y, puckRadius, puckStrokePaint)

        canvas.restore()

        // 2g. Title and Coordinate Readout
        textPaint.color = MotionTheme.TEXT_PRIMARY
        textPaint.textSize = 10f * density
        textPaint.textAlign = Paint.Align.LEFT
        val zoomTag = if (kotlin.math.abs(zoomLevel - 1f) > 0.05f) " [${String.format("%.1fx", zoomLevel)}]" else ""
        canvas.drawText("1:1 KURVA$zoomTag: $propertyName", graphBounds.left, graphBounds.top - 8f * density, textPaint)

        textPaint.color = MotionTheme.TEXT_MUTED
        val readout = String.format("P1: (%.2f, %.2f)   P2: (%.2f, %.2f)", outHandleX, outHandleY, inHandleX, inHandleY)
        canvas.drawText(readout, graphBounds.left, graphBounds.bottom + 18f * density, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Multi-touch gestures (Pinch-to-zoom & Two-finger Pan)
        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    initialPinchDist = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    basePinchZoom = zoomLevel
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (initialPinchDist > 20f) {
                        val curDist = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                        val factor = curDist / initialPinchDist
                        zoomLevel = (basePinchZoom * factor).coerceIn(0.4f, 3.5f)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                        return true
                    }
                }
            }
        }

        val inZoomBarArea = event.x <= (zoomBarBounds.right + 12f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                // 1. Touch on Zoom Bar
                if (inZoomBarArea) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (btnZoomInBounds.contains(event.x, event.y)) {
                        zoomLevel = (zoomLevel + 0.25f).coerceIn(0.4f, 3.5f)
                        return true
                    }
                    if (btnZoomOutBounds.contains(event.x, event.y)) {
                        zoomLevel = (zoomLevel - 0.25f).coerceIn(0.4f, 3.5f)
                        return true
                    }
                    if (btnResetBounds.contains(event.x, event.y)) {
                        zoomLevel = 1.0f
                        panX = 0.0
                        panY = 0.0
                        invalidate()
                        return true
                    }
                    // Drag slider track
                    isDraggingZoom = true
                    setZoomFromSliderY(event.y)
                    return true
                }

                // 2. Touch on Graph Area
                val p1X = toScreenX(outHandleX)
                val p1Y = toScreenY(outHandleY)
                val p2X = toScreenX(inHandleX)
                val p2Y = toScreenY(inHandleY)

                val distP1 = hypot(event.x - p1X, event.y - p1Y)
                val distP2 = hypot(event.x - p2X, event.y - p2Y)
                val hitRad = puckHitRadius

                draggedPuck = when {
                    distP1 < hitRad && distP1 <= distP2 -> 1
                    distP2 < hitRad -> 2
                    distP1 < hitRad * 1.5f && distP1 <= distP2 -> 1
                    distP2 < hitRad * 1.5f -> 2
                    else -> 0
                }

                if (draggedPuck != 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }

                // If not touching pucks but inside graph box and zoomed: enable panning
                if (graphBounds.contains(event.x, event.y) && (zoomLevel != 1.0f || panX != 0.0 || panY != 0.0)) {
                    isPanningGraph = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Dragging Zoom Slider
                if (isDraggingZoom) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    setZoomFromSliderY(event.y)
                    return true
                }

                // Dragging Handle Pucks
                if (draggedPuck != 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (draggedPuck == 1) {
                        outHandleX = toNormX(event.x).coerceIn(-0.5, 1.5)
                        outHandleY = toNormY(event.y).coerceIn(-0.5, 2.0)
                    } else if (draggedPuck == 2) {
                        inHandleX = toNormX(event.x).coerceIn(-0.5, 1.5)
                        inHandleY = toNormY(event.y).coerceIn(-0.5, 2.0)
                    }
                    onCurveChanged?.invoke(outHandleX, outHandleY, inHandleX, inHandleY)
                    invalidate()
                    return true
                }

                // Panning the Graph
                if (isPanningGraph) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val dx = (event.x - lastTouchX) / graphBounds.width()
                    val dy = (event.y - lastTouchY) / graphBounds.height()
                    val vp = getViewportBounds()
                    val vpW = vp[1] - vp[0]
                    val vpH = vp[3] - vp[2]
                    panX = (panX - dx * vpW).coerceIn(-1.5, 1.5)
                    panY = (panY + dy * vpH).coerceIn(-1.5, 1.5)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingZoom || draggedPuck != 0 || isPanningGraph) {
                    isDraggingZoom = false
                    draggedPuck = 0
                    isPanningGraph = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }
}
