package com.example.nativecppapp.ui.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.example.nativecppapp.ui.theme.MotionTheme
import kotlin.math.roundToInt

import android.view.SurfaceHolder
import android.view.SurfaceView

class CanvasViewportView(context: Context) : View(context) {

    var onSurfaceCreated: ((android.view.Surface) -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null
    var onSurfaceChanged: ((Int, Int) -> Unit)? = null
    var onViewportCalculated: ((Float, Float, Float, Float) -> Unit)? = null

    init {
        // Standard View, hardware accelerated
    }

    var previewBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var projectWidth: Int = 1080
    var projectHeight: Int = 1920
    var currentTimecode: String = "00:00:00"
        set(value) {
            field = value
            invalidate()
        }

    var selectedLayerName: String = "Lingkaran 1"
        set(value) {
            field = value
            invalidate()
        }

    // Selected layer transform coordinates on screen
    var selectedLayerX: Float = 0f
    var selectedLayerY: Float = 0f
    var selectedLayerScaleX: Float = 1.0f
    var selectedLayerScaleY: Float = 1.0f
    var selectedLayerRotation: Float = 0.0f
    var zoomFactor: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 3.0f)
            invalidate()
        }

    var onLayerPositionChanged: ((Float, Float) -> Unit)? = null
    var onLayerScaleChanged: ((Float, Float) -> Unit)? = null
    var onLayerRotationChanged: ((Float) -> Unit)? = null

    private val bgPaint = Paint().apply {
        color = MotionTheme.CANVAS_BG
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = MotionTheme.BORDER_HAIRLINE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val guidePaint = Paint().apply {
        color = Color.parseColor("#25FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val badgeBgPaint = Paint().apply {
        color = Color.parseColor("#B318181B")
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint().apply {
        color = MotionTheme.TEXT_PRIMARY
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    private val handlePaint = Paint().apply {
        color = MotionTheme.TEXT_PRIMARY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val handleStrokePaint = Paint().apply {
        color = MotionTheme.CANVAS_BG
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val selectionBoxPaint = Paint().apply {
        color = MotionTheme.TEXT_PRIMARY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    var activeSurface: android.view.Surface? = null
    private val canvasRect = RectF()
    fun getCanvasRect(): RectF = canvasRect

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            calculateCanvasRect(w.toFloat(), h.toFloat())
        }
    }

    private fun calculateCanvasRect(viewW: Float, viewH: Float) {
        val density = resources.displayMetrics.density
        val padding = 16f * density
        val availW = (viewW - (padding * 2f)).coerceAtLeast(10f)
        val availH = (viewH - (padding * 2f)).coerceAtLeast(10f)

        val projectAspect = projectWidth.toFloat() / projectHeight.toFloat().coerceAtLeast(1f)
        val availAspect = availW / availH

        val targetW: Float
        val targetH: Float

        if (availAspect > projectAspect) {
            targetH = availH
            targetW = targetH * projectAspect
        } else {
            targetW = availW
            targetH = targetW / projectAspect
        }

        val left = (viewW - targetW) / 2f
        val top = (viewH - targetH) / 2f
        canvasRect.set(left, top, left + targetW, top + targetH)
        onViewportCalculated?.invoke(left, top, targetW, targetH)
    }

    fun updateProjectAspect(width: Int, height: Int) {
        projectWidth = width
        projectHeight = height
        val curW = if (this.width > 0) this.width.toFloat() else 0f
        val curH = if (this.height > 0) this.height.toFloat() else 0f
        if (curW > 0f && curH > 0f) {
            calculateCanvasRect(curW, curH)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // 1. Draw outer workspace backdrop
        val surfacePaint = Paint().apply {
            color = MotionTheme.SURFACE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), canvasRect.top, surfacePaint) // Top
        canvas.drawRect(0f, canvasRect.bottom, width.toFloat(), height.toFloat(), surfacePaint) // Bottom
        canvas.drawRect(0f, canvasRect.top, canvasRect.left, canvasRect.bottom, surfacePaint) // Left
        canvas.drawRect(canvasRect.right, canvasRect.top, width.toFloat(), canvasRect.bottom, surfacePaint) // Right

        // 2. Draw canvas background inside canvasRect
        canvas.drawRect(canvasRect, bgPaint)

        // 3. Draw Rendered Frame Bitmap from C++ Engine
        previewBitmap?.let { bmp ->
            val src = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, src, canvasRect, null)
        }

        // 4. Draw canvas frame container border
        canvas.drawRect(canvasRect, borderPaint)

        // 4. Safe area guideline overlay
        val safeMarginX = canvasRect.width() * 0.08f
        val safeMarginY = canvasRect.height() * 0.08f
        canvas.drawRect(
            canvasRect.left + safeMarginX,
            canvasRect.top + safeMarginY,
            canvasRect.right - safeMarginX,
            canvasRect.bottom - safeMarginY,
            guidePaint
        )

        // 5. Draw interactive Selection Bounding Box & Transformation Handles
        val boxCenterX = canvasRect.centerX() + (selectedLayerX * canvasRect.width() / projectWidth)
        val boxCenterY = canvasRect.centerY() + (selectedLayerY * canvasRect.height() / projectHeight)
        val boxRadius = (45f * density) * selectedLayerScaleX.coerceIn(0.6f, 2.5f)

        canvas.save()
        canvas.rotate(selectedLayerRotation, boxCenterX, boxCenterY)

        val boxRect = RectF(
            boxCenterX - boxRadius,
            boxCenterY - boxRadius,
            boxCenterX + boxRadius,
            boxCenterY + boxRadius
        )
        canvas.drawRect(boxRect, selectionBoxPaint)

        // Corner handles
        val corners = arrayOf(
            Pair(boxRect.left, boxRect.top),
            Pair(boxRect.right, boxRect.top),
            Pair(boxRect.left, boxRect.bottom),
            Pair(boxRect.right, boxRect.bottom)
        )
        for (corner in corners) {
            canvas.drawCircle(corner.first, corner.second, 9f * density, handlePaint)
            canvas.drawCircle(corner.first, corner.second, 9f * density, handleStrokePaint)
        }

        // Rotation stem on top
        canvas.drawLine(boxCenterX, boxRect.top, boxCenterX, boxRect.top - (24f * density), selectionBoxPaint)
        canvas.drawCircle(boxCenterX, boxRect.top - (24f * density), 8f * density, handlePaint)
        canvas.drawCircle(boxCenterX, boxRect.top - (24f * density), 8f * density, handleStrokePaint)

        canvas.restore()

        // 6. Badges: Zoom & Timecode
        badgeTextPaint.textSize = 11f * density
        val badgeH = 26f * density
        val badgePadding = 8f * density
        val badgeW = 60f * density
        val tcW = 90f * density
        val tagW = 110f * density
        val textOffsetY = badgeTextPaint.textSize * 0.35f

        // Zoom badge (top-left)
        val zoomRect = RectF(
            canvasRect.left + badgePadding,
            canvasRect.top + badgePadding,
            canvasRect.left + badgePadding + badgeW,
            canvasRect.top + badgePadding + badgeH
        )
        canvas.drawRoundRect(zoomRect, 6f * density, 6f * density, badgeBgPaint)
        badgeTextPaint.color = MotionTheme.TEXT_MUTED
        val zoomPct = String.format("%d%%", (zoomFactor * 100).toInt())
        canvas.drawText(zoomPct, zoomRect.left + 8f * density, zoomRect.centerY() + textOffsetY, badgeTextPaint)

        // Timecode badge (top-right)
        val tcRect = RectF(
            canvasRect.right - badgePadding - tcW,
            canvasRect.top + badgePadding,
            canvasRect.right - badgePadding,
            canvasRect.top + badgePadding + badgeH
        )
        canvas.drawRoundRect(tcRect, 6f * density, 6f * density, badgeBgPaint)
        badgeTextPaint.color = MotionTheme.TEXT_PRIMARY
        canvas.drawText(currentTimecode, tcRect.left + 8f * density, tcRect.centerY() + textOffsetY, badgeTextPaint)

        // Layer Name tag (bottom-left)
        val layerTagRect = RectF(
            canvasRect.left + badgePadding,
            canvasRect.bottom - badgePadding - badgeH,
            canvasRect.left + badgePadding + tagW,
            canvasRect.bottom - badgePadding
        )
        canvas.drawRoundRect(layerTagRect, 6f * density, 6f * density, badgeBgPaint)
        badgeTextPaint.color = MotionTheme.TEXT_SECONDARY
        canvas.drawText(selectedLayerName, layerTagRect.left + 8f * density, layerTagRect.centerY() + textOffsetY, badgeTextPaint)
    }

    private enum class DragMode {
        NONE, TRANSLATE, SCALE_CORNER, ROTATE_STEM, PINCH_ZOOM
    }
    private var dragMode = DragMode.NONE
    private var initialPinchDistance = 0f
    private var initialPinchZoom = 1.0f
    private var initialScaleDistance = 0f
    private var initialLayerScale = 1.0f
    private var initialAngle = 0.0
    private var initialLayerRotation = 0.0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val boxCenterX = canvasRect.centerX() + (selectedLayerX * canvasRect.width() / projectWidth)
        val boxCenterY = canvasRect.centerY() + (selectedLayerY * canvasRect.height() / projectHeight)
        val boxRadius = (45f * density) * selectedLayerScaleX.coerceIn(0.6f, 2.5f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                val rad = -Math.toRadians(selectedLayerRotation.toDouble())
                val cosR = kotlin.math.cos(rad).toFloat()
                val sinR = kotlin.math.sin(rad).toFloat()
                val dx = event.x - boxCenterX
                val dy = event.y - boxCenterY
                val localX = dx * cosR - dy * sinR
                val localY = dx * sinR + dy * cosR

                val stemDist = kotlin.math.hypot(localX, localY - (-boxRadius - (24f * density)))
                val cornerRadiusHit = 26f * density

                val cornerDists = listOf(
                    kotlin.math.hypot(localX - (-boxRadius), localY - (-boxRadius)),
                    kotlin.math.hypot(localX - boxRadius, localY - (-boxRadius)),
                    kotlin.math.hypot(localX - (-boxRadius), localY - boxRadius),
                    kotlin.math.hypot(localX - boxRadius, localY - boxRadius)
                )

                if (stemDist <= cornerRadiusHit) {
                    dragMode = DragMode.ROTATE_STEM
                    initialAngle = kotlin.math.atan2((event.y - boxCenterY).toDouble(), (event.x - boxCenterX).toDouble())
                    initialLayerRotation = selectedLayerRotation
                } else if ((cornerDists.minOrNull() ?: 999f) <= cornerRadiusHit) {
                    dragMode = DragMode.SCALE_CORNER
                    initialScaleDistance = kotlin.math.hypot(dx, dy).coerceAtLeast(10f)
                    initialLayerScale = selectedLayerScaleX
                } else {
                    dragMode = DragMode.TRANSLATE
                }
                isDragging = true
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    dragMode = DragMode.PINCH_ZOOM
                    val p0x = event.getX(0); val p0y = event.getY(0)
                    val p1x = event.getX(1); val p1y = event.getY(1)
                    initialPinchDistance = kotlin.math.hypot(p0x - p1x, p0y - p1y).coerceAtLeast(10f)
                    initialPinchZoom = zoomFactor
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode == DragMode.PINCH_ZOOM && event.pointerCount >= 2) {
                    val p0x = event.getX(0); val p0y = event.getY(0)
                    val p1x = event.getX(1); val p1y = event.getY(1)
                    val curDist = kotlin.math.hypot(p0x - p1x, p0y - p1y)
                    if (initialPinchDistance > 10f) {
                        zoomFactor = (initialPinchZoom * (curDist / initialPinchDistance)).coerceIn(0.5f, 3.0f)
                        invalidate()
                    }
                    return true
                }

                when (dragMode) {
                    DragMode.ROTATE_STEM -> {
                        val curAngle = kotlin.math.atan2((event.y - boxCenterY).toDouble(), (event.x - boxCenterX).toDouble())
                        val deltaDeg = Math.toDegrees(curAngle - initialAngle).toFloat()
                        selectedLayerRotation = ((initialLayerRotation + deltaDeg) % 360f + 360f) % 360f
                        onLayerRotationChanged?.invoke(selectedLayerRotation)
                        invalidate()
                    }
                    DragMode.SCALE_CORNER -> {
                        val curDist = kotlin.math.hypot(event.x - boxCenterX, event.y - boxCenterY)
                        if (initialScaleDistance > 10f) {
                            val factor = curDist / initialScaleDistance
                            val newScale = (initialLayerScale * factor).coerceIn(0.1f, 5.0f)
                            selectedLayerScaleX = newScale
                            selectedLayerScaleY = newScale
                            onLayerScaleChanged?.invoke(newScale, newScale)
                            invalidate()
                        }
                    }
                    DragMode.TRANSLATE -> {
                        val deltaX = event.x - lastTouchX
                        val deltaY = event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y

                        val scaleX = projectWidth.toFloat() / canvasRect.width()
                        val scaleY = projectHeight.toFloat() / canvasRect.height()

                        selectedLayerX += deltaX * scaleX
                        selectedLayerY += deltaY * scaleY

                        onLayerPositionChanged?.invoke(selectedLayerX, selectedLayerY)
                        invalidate()
                    }
                    else -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                dragMode = DragMode.NONE
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun triggerSurfaceCreated(surface: android.view.Surface) {
        activeSurface = surface
        onSurfaceCreated?.invoke(surface)
    }

    fun triggerSurfaceChanged(width: Int, height: Int) {
        onSurfaceChanged?.invoke(width, height)
    }

    fun triggerSurfaceDestroyed() {
        activeSurface = null
        onSurfaceDestroyed?.invoke()
    }
}
