package com.example.nativecppapp.ui.view

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.example.nativecppapp.ui.theme.MotionTheme
import kotlin.math.max
import kotlin.math.min

class TimelineDeckView(context: Context) : View(context) {

    data class TrackInfo(
        val id: String,
        val name: String,
        val type: String, // SHAPE, MEDIA, AUDIO, TEXT
        var isVisible: Boolean = true,
        var startFrame: Long = 0,
        var durationFrames: Long = 300,
        val keyframeFrames: MutableList<Long> = mutableListOf(),
        var blendMode: Int = 0,
        var inTransition: Int = 0,
        var inTransitionDuration: Long = 0,
        var outTransition: Int = 0,
        var outTransitionDuration: Long = 0,
        var mediaWidth: Int = 0,
        var mediaHeight: Int = 0,
        var motionBlurEnabled: Boolean = false,
        var motionBlurStrength: Float = 1.0f
    )

    var currentFrame: Long = 30
        set(value) {
            field = value
            invalidate()
        }

    var totalDurationFrames: Long = 300
        set(value) {
            field = value
            invalidate()
        }

    var selectedTrackIndex: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    val tracks = mutableListOf<TrackInfo>()

    var onFrameScrubbed: ((Long) -> Unit)? = null
    var onTrackSelected: ((Int, TrackInfo) -> Unit)? = null
    var onClipSpanChanged: ((TrackInfo) -> Unit)? = null
    var onTrackReordered: ((TrackInfo, Int) -> Unit)? = null
    var onDragCompleted: (() -> Unit)? = null
    var onKeyframeClicked: ((Long) -> Unit)? = null
    var onKeyframeMoved: ((TrackInfo, Long, Long) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val headerWidth = 96f * density
    private val rulerHeight = 28f * density
    private val trackHeight = 42f * density
    private val trackSpacing = 6f * density

    private val rulerPaint = Paint().apply {
        color = MotionTheme.SURFACE_LOW
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = MotionTheme.BORDER_HAIRLINE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = MotionTheme.TEXT_SECONDARY
        textSize = 12f * density
        isAntiAlias = true
    }

    private val monoTextPaint = Paint().apply {
        color = MotionTheme.TEXT_MUTED
        textSize = 10f * density
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val trackBgPaint = Paint().apply {
        color = MotionTheme.SURFACE_CONTAINER
        style = Paint.Style.FILL
    }

    private val trackSelectedPaint = Paint().apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val clipBgPaint = Paint().apply {
        color = MotionTheme.SURFACE_HIGH
        style = Paint.Style.FILL
    }

    private val playheadPaint = Paint().apply {
        color = MotionTheme.PLAYHEAD_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        isAntiAlias = true
    }

    private val playheadHeadPaint = Paint().apply {
        color = MotionTheme.PLAYHEAD_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val keyframePaint = Paint().apply {
        color = MotionTheme.KEYFRAME_INACTIVE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val keyframeActivePaint = Paint().apply {
        color = MotionTheme.KEYFRAME_ACTIVE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val waveformPaint = Paint().apply {
        color = MotionTheme.TEXT_MUTED
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private enum class TouchMode {
        NONE, SCRUB, TRIM_LEFT, TRIM_RIGHT, MOVE_CLIP, REORDER_TRACK, PENDING_KEYFRAME_HOLD, DRAG_KEYFRAME
    }

    private var touchMode = TouchMode.NONE
    private var dragStartFrame = 0L
    private var initialTrackStart = 0L
    private var initialTrackDuration = 300L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var draggedTrackIndex = -1
    private var draggedKeyframeFrame = -1L
    private var pendingKeyframeFrame = -1L
    private var isKeyframeDragUnlocked = false
    private val keyframeHoldHandler = Handler(Looper.getMainLooper())
    private val keyframeHoldRunnable = Runnable {
        if (pendingKeyframeFrame >= 0L) {
            isKeyframeDragUnlocked = true
            draggedKeyframeFrame = pendingKeyframeFrame
            touchMode = TouchMode.DRAG_KEYFRAME
            try {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {}
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalW = width.toFloat()
        val totalH = height.toFloat()
        val lanesW = totalW - headerWidth

        // 1. Draw Background
        canvas.drawColor(MotionTheme.SURFACE_LOW)

        // 2. Draw Time Ruler at the top
        canvas.drawRect(0f, 0f, totalW, rulerHeight, rulerPaint)
        canvas.drawLine(0f, rulerHeight, totalW, rulerHeight, borderPaint)

        // Draw track separator header column line
        canvas.drawLine(headerWidth, 0f, headerWidth, totalH, borderPaint)

        // Header Title
        textPaint.color = MotionTheme.TEXT_MUTED
        textPaint.textSize = 9f * density
        canvas.drawText("LAYERS (${tracks.size})", 10f * density, rulerHeight * 0.65f, textPaint)

        // Draw ruler tick marks
        val tickCount = 6
        for (i in 0..tickCount) {
            val ratio = i.toFloat() / tickCount
            val x = headerWidth + (ratio * lanesW)
            canvas.drawLine(x, rulerHeight - 12f, x, rulerHeight, borderPaint)

            val sec = (ratio * (totalDurationFrames / 60f))
            val timeLabel = String.format("%02d:%02d", (sec / 60).toInt(), (sec % 60).toInt())
            canvas.drawText(timeLabel, x + 6f, rulerHeight * 0.6f, monoTextPaint)
        }

        // 3. Draw Track Rows
        var currentY = rulerHeight + trackSpacing

        for (i in tracks.indices) {
            val track = tracks[i]
            val isSelected = (i == selectedTrackIndex)

            // Header card
            val headerRect = RectF(12f, currentY, headerWidth - 8f, currentY + trackHeight)
            canvas.drawRoundRect(headerRect, 8f, 8f, trackBgPaint)
            val isReordering = (touchMode == TouchMode.REORDER_TRACK && i == selectedTrackIndex)
            val reorderBorder = Paint().apply {
                color = Color.parseColor("#818CF8")
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
            }
            if (isReordering) {
                canvas.drawRoundRect(headerRect, 8f, 8f, reorderBorder)
            } else if (isSelected) {
                canvas.drawRoundRect(headerRect, 8f, 8f, trackSelectedPaint)
            }

            // Left indicator strip
            val indicatorPaint = Paint().apply {
                color = when (track.type) {
                    "SHAPE" -> MotionTheme.TEXT_PRIMARY
                    "MEDIA" -> Color.parseColor("#818CF8") // Indigo
                    "AUDIO" -> Color.parseColor("#F59E0B") // Amber
                    "TEXT" -> Color.parseColor("#34D399")  // Emerald
                    else -> MotionTheme.TEXT_MUTED
                }
            }
            canvas.drawRoundRect(RectF(headerRect.left + 4f, headerRect.top + 8f, headerRect.left + 10f, headerRect.bottom - 8f), 4f, 4f, indicatorPaint)

            // Track Name
            textPaint.color = if (isSelected) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_SECONDARY
            textPaint.textSize = 11f * density
            val maxTextWidth = if (track.motionBlurEnabled) {
                (headerRect.width() - 36f * density).coerceAtLeast(16f * density)
            } else {
                (headerRect.width() - 18f * density).coerceAtLeast(16f * density)
            }
            val displayName = android.text.TextUtils.ellipsize(track.name, android.text.TextPaint(textPaint), maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(displayName, headerRect.left + 14f * density, headerRect.centerY() + 4f * density, textPaint)

            // Motion Blur [MB] Badge
            if (track.motionBlurEnabled) {
                val mbBadgePaint = Paint().apply {
                    color = Color.parseColor("#06B6D4") // Cyan
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val mbTextPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 7.5f * density
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                val badgeW = 18f * density
                val badgeH = 11f * density
                val badgeRect = RectF(
                    headerRect.right - badgeW - 3f * density,
                    headerRect.top + 4f * density,
                    headerRect.right - 3f * density,
                    headerRect.top + 4f * density + badgeH
                )
                canvas.drawRoundRect(badgeRect, 3f * density, 3f * density, mbBadgePaint)
                canvas.drawText("MB", badgeRect.centerX(), badgeRect.centerY() + 2.5f * density, mbTextPaint)
            }

            // Clip lane area
            val laneLeft = headerWidth + 8f
            val laneRight = totalW - 12f
            val laneW = laneRight - laneLeft

            val clipStartX = laneLeft + (track.startFrame.toFloat() / totalDurationFrames.toFloat()) * laneW
            val clipW = (track.durationFrames.toFloat() / totalDurationFrames.toFloat()) * laneW
            val clipRect = RectF(clipStartX, currentY, clipStartX + clipW, currentY + trackHeight)

            canvas.drawRoundRect(clipRect, 8f, 8f, clipBgPaint)
            if (isReordering) {
                canvas.drawRoundRect(clipRect, 8f, 8f, reorderBorder)
            }

            // In-Transition indicator wedge
            if (track.inTransition > 0 && track.inTransitionDuration > 0) {
                val inTransW = (track.inTransitionDuration.toFloat() / totalDurationFrames.toFloat()) * laneW
                val inTransWClamp = inTransW.coerceAtMost(clipRect.width() * 0.5f)
                val inTransPaint = Paint().apply {
                    color = Color.parseColor("#818CF8")
                    style = Paint.Style.FILL
                    alpha = 110
                    isAntiAlias = true
                }
                val inTransPath = Path().apply {
                    moveTo(clipRect.left, clipRect.top)
                    lineTo(clipRect.left + inTransWClamp, clipRect.top)
                    lineTo(clipRect.left, clipRect.bottom)
                    close()
                }
                canvas.drawPath(inTransPath, inTransPaint)
            }

            // Out-Transition indicator wedge
            if (track.outTransition > 0 && track.outTransitionDuration > 0) {
                val outTransW = (track.outTransitionDuration.toFloat() / totalDurationFrames.toFloat()) * laneW
                val outTransWClamp = outTransW.coerceAtMost(clipRect.width() * 0.5f)
                val outTransPaint = Paint().apply {
                    color = Color.parseColor("#818CF8")
                    style = Paint.Style.FILL
                    alpha = 110
                    isAntiAlias = true
                }
                val outTransPath = Path().apply {
                    moveTo(clipRect.right - outTransWClamp, clipRect.bottom)
                    lineTo(clipRect.right, clipRect.top)
                    lineTo(clipRect.right, clipRect.bottom)
                    close()
                }
                canvas.drawPath(outTransPath, outTransPaint)
            }

            // Blend Mode badge if non-normal
            if (track.blendMode > 0) {
                val blendNames = arrayOf("NORM", "MUL", "SCRN", "OVL", "ADD", "DIFF")
                val bName = blendNames.getOrElse(track.blendMode) { "FX" }
                val badgePaint = Paint().apply {
                    color = Color.parseColor("#312E81") // Deep Indigo
                    style = Paint.Style.FILL
                }
                val badgeText = Paint().apply {
                    color = Color.parseColor("#C7D2FE")
                    textSize = 8.5f * density
                    typeface = Typeface.MONOSPACE
                    isAntiAlias = true
                }
                val bW = 32f * density
                val bH = 16f * density
                val bRect = RectF(clipRect.right - bW - 4f, clipRect.top + 4f, clipRect.right - 4f, clipRect.top + 4f + bH)
                canvas.drawRoundRect(bRect, 4f, 4f, badgePaint)
                canvas.drawText(bName, bRect.left + 4f, bRect.centerY() + 3f * density, badgeText)
            }

            if (track.type == "AUDIO") {
                val waveMidY = clipRect.centerY()
                val barW = 3f * density
                val barGap = 2f * density
                val maxBarH = (trackHeight * 0.7f) / 2f
                val barPaint = Paint().apply {
                    color = Color.parseColor("#F59E0B") // Studio Amber
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                var bx = clipRect.left + (8f * density)
                var idx = 0
                val sampleAmps = floatArrayOf(
                    0.25f, 0.45f, 0.85f, 0.6f, 0.3f, 0.75f, 0.95f, 0.5f,
                    0.4f, 0.7f, 0.9f, 0.8f, 0.35f, 0.6f, 0.85f, 0.4f,
                    0.2f, 0.5f, 0.75f, 0.95f, 0.65f, 0.4f, 0.8f, 0.3f
                )

                while (bx < clipRect.right - (8f * density)) {
                    val amp = sampleAmps[idx % sampleAmps.size]
                    val h = maxBarH * amp
                    canvas.drawRoundRect(
                        RectF(bx, waveMidY - h, bx + barW, waveMidY + h),
                        barW * 0.5f, barW * 0.5f, barPaint
                    )
                    bx += barW + barGap
                    idx++
                }
            }

            // Draw Keyframe Diamonds on track clip
            for (kfFrame in track.keyframeFrames) {
                val kfX = laneLeft + (kfFrame.toFloat() / totalDurationFrames.toFloat()) * laneW
                val kfY = clipRect.centerY()
                val isBeingDragged = (isKeyframeDragUnlocked && kfFrame == draggedKeyframeFrame && isSelected)
                val isKfActive = (kfFrame == currentFrame)

                if (isBeingDragged) {
                    // Bright white glowing keyframe when held for 0.15s
                    val sz = 13f * density
                    val brightDiamondPath = Path().apply {
                        moveTo(kfX, kfY - sz)
                        lineTo(kfX + sz, kfY)
                        lineTo(kfX, kfY + sz)
                        lineTo(kfX - sz, kfY)
                        close()
                    }
                    val glowPaint = Paint().apply {
                        color = Color.parseColor("#99FFFFFF")
                        style = Paint.Style.STROKE
                        strokeWidth = 6f * density
                        isAntiAlias = true
                    }
                    canvas.drawPath(brightDiamondPath, glowPaint)

                    val brightFill = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawPath(brightDiamondPath, brightFill)

                    val borderPaint = Paint().apply {
                        color = Color.parseColor("#18181B")
                        style = Paint.Style.STROKE
                        strokeWidth = 2f * density
                        isAntiAlias = true
                    }
                    canvas.drawPath(brightDiamondPath, borderPaint)
                } else if (isKfActive) {
                    val sz = 9f * density
                    val diamondPath = Path().apply {
                        moveTo(kfX, kfY - sz)
                        lineTo(kfX + sz, kfY)
                        lineTo(kfX, kfY + sz)
                        lineTo(kfX - sz, kfY)
                        close()
                    }
                    canvas.drawPath(diamondPath, keyframeActivePaint)
                    val ringPaint = Paint().apply {
                        color = MotionTheme.SURFACE
                        style = Paint.Style.STROKE
                        strokeWidth = 2f * density
                        isAntiAlias = true
                    }
                    canvas.drawPath(diamondPath, ringPaint)
                } else {
                    val sz = 7.5f * density
                    val diamondPath = Path().apply {
                        moveTo(kfX, kfY - sz)
                        lineTo(kfX + sz, kfY)
                        lineTo(kfX, kfY + sz)
                        lineTo(kfX - sz, kfY)
                        close()
                    }
                    canvas.drawPath(diamondPath, keyframePaint)
                }
            }

            // Duration and span badge inside clip
            val durSec = track.durationFrames / 60.0
            val durLabel = String.format("%.1fs", durSec)
            val durPaint = Paint().apply {
                color = if (isSelected) MotionTheme.TEXT_PRIMARY else MotionTheme.TEXT_MUTED
                textSize = 9f * density
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            }
            if (clipW > 36f * density) {
                canvas.drawText(durLabel, clipRect.left + (if (isSelected) 18f * density else 8f * density), clipRect.bottom - 6f * density, durPaint)
            }

            // Selection Trim Handles
            if (isSelected) {
                val selBorder = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawRoundRect(clipRect, 8f, 8f, selBorder)

                // Left Handle
                val handleW = 14f * density
                val leftHandleRect = RectF(clipRect.left, clipRect.top, clipRect.left + handleW, clipRect.bottom)
                val handleFill = Paint().apply {
                    color = Color.parseColor("#F4F4F5")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(leftHandleRect, 6f, 6f, handleFill)
                val notchPaint = Paint().apply {
                    color = Color.parseColor("#18181B")
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(leftHandleRect.centerX(), leftHandleRect.centerY() - 7f * density, leftHandleRect.centerX(), leftHandleRect.centerY() + 7f * density, notchPaint)

                // Right Handle
                val rightHandleRect = RectF(clipRect.right - handleW, clipRect.top, clipRect.right, clipRect.bottom)
                canvas.drawRoundRect(rightHandleRect, 6f, 6f, handleFill)
                canvas.drawLine(rightHandleRect.centerX(), rightHandleRect.centerY() - 7f * density, rightHandleRect.centerX(), rightHandleRect.centerY() + 7f * density, notchPaint)
            }

            currentY += trackHeight + trackSpacing
        }

        // 4. Draw Playhead Needle across all tracks
        val playheadProgress = (currentFrame.toFloat() / totalDurationFrames.toFloat()).coerceIn(0f, 1f)
        val playheadX = (headerWidth + 8f) + (playheadProgress * (totalW - headerWidth - 20f))

        // Vertical line with contrast backdrop
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#80000000")
            strokeWidth = 4.5f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(playheadX, 0f, playheadX, totalH, shadowPaint)
        canvas.drawLine(playheadX, 0f, playheadX, totalH, playheadPaint)

        // Bottom grip pill
        val bottomGripRect = RectF(
            playheadX - 4f * density,
            totalH - 12f * density,
            playheadX + 4f * density,
            totalH - 2f * density
        )
        canvas.drawRoundRect(bottomGripRect, 3f * density, 3f * density, playheadHeadPaint)

        // Needle head badge at ruler (prominent bookmark shape)
        val needleW = 15f * density
        val headPath = Path().apply {
            moveTo(playheadX, rulerHeight)
            lineTo(playheadX + needleW, rulerHeight - (10f * density))
            lineTo(playheadX + needleW, 2f * density)
            lineTo(playheadX - needleW, 2f * density)
            lineTo(playheadX - needleW, rulerHeight - (10f * density))
            close()
        }
        canvas.drawPath(headPath, playheadHeadPaint)

        val pinPaint = Paint().apply {
            color = Color.parseColor("#18181B")
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(playheadX, 4f * density, playheadX, rulerHeight - (8f * density), pinPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val totalW = width.toFloat()
        val laneStart = headerWidth + 8f
        val laneEnd = totalW - 12f
        val laneW = laneEnd - laneStart

        val playheadProgress = (currentFrame.toFloat() / totalDurationFrames.toFloat()).coerceIn(0f, 1f)
        val playheadX = laneStart + (playheadProgress * laneW)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y

                // 1. Tapped on left header column
                if (event.x < headerWidth) {
                    if (event.y > rulerHeight) {
                        val trackIndex = ((event.y - rulerHeight) / (trackHeight + trackSpacing)).toInt()
                        if (trackIndex in tracks.indices) {
                            selectedTrackIndex = trackIndex
                            draggedTrackIndex = trackIndex
                            val track = tracks[trackIndex]
                            onTrackSelected?.invoke(trackIndex, track)
                            touchMode = TouchMode.NONE
                            invalidate()
                            return true
                        }
                    }
                    return true
                }

                // 2. Touched on top ruler -> only scrub when actively dragged horizontally, not on click
                if (event.y <= rulerHeight) {
                    touchMode = TouchMode.SCRUB
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // 3. Check track rows
                val trackIndex = ((event.y - rulerHeight) / (trackHeight + trackSpacing)).toInt()
                if (trackIndex in tracks.indices) {
                    selectedTrackIndex = trackIndex
                    draggedTrackIndex = trackIndex
                    val track = tracks[trackIndex]
                    onTrackSelected?.invoke(trackIndex, track)

                    // 3a. Check if touching a keyframe diamond on this track
                    val kfHitRadius = 24f * density
                    var hitKfFrame: Long? = null
                    for (kfFrame in track.keyframeFrames) {
                        val kfX = laneStart + (kfFrame.toFloat() / totalDurationFrames.toFloat()) * laneW
                        if (kotlin.math.abs(event.x - kfX) <= kfHitRadius) {
                            hitKfFrame = kfFrame
                            break
                        }
                    }

                    if (hitKfFrame != null) {
                        pendingKeyframeFrame = hitKfFrame
                        isKeyframeDragUnlocked = false
                        touchMode = TouchMode.PENDING_KEYFRAME_HOLD
                        currentFrame = hitKfFrame
                        onFrameScrubbed?.invoke(hitKfFrame)
                        onKeyframeClicked?.invoke(hitKfFrame)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        keyframeHoldHandler.removeCallbacks(keyframeHoldRunnable)
                        keyframeHoldHandler.postDelayed(keyframeHoldRunnable, 150L) // 0.15s hold to unlock
                        invalidate()
                        return true
                    }

                    // 3b. Check trim handles
                    val clipStartX = laneStart + (track.startFrame.toFloat() / totalDurationFrames.toFloat()) * laneW
                    val clipEndX = clipStartX + (track.durationFrames.toFloat() / totalDurationFrames.toFloat()) * laneW
                    val handleTouchWidth = 24f * density

                    dragStartFrame = frameFromX(event.x, laneStart, laneW)
                    initialTrackStart = track.startFrame
                    initialTrackDuration = track.durationFrames

                    if (kotlin.math.abs(event.x - clipStartX) <= handleTouchWidth) {
                        touchMode = TouchMode.TRIM_LEFT
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                        return true
                    } else if (kotlin.math.abs(event.x - clipEndX) <= handleTouchWidth) {
                        touchMode = TouchMode.TRIM_RIGHT
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                        return true
                    } else {
                        // Tapped inside track / clip or anywhere on track:
                        // Select the track! The white line is NOT clicked and does NOT move!
                        touchMode = TouchMode.NONE
                        invalidate()
                        return true
                    }
                } else {
                    touchMode = TouchMode.NONE
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - touchDownY
                val dx = event.x - touchDownX

                if (touchMode == TouchMode.SCRUB) {
                    if (kotlin.math.abs(dx) > 4f * density) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        updatePlayheadFromTouch(event.x, laneStart, laneW)
                    }
                    return true
                }

                if (touchMode == TouchMode.PENDING_KEYFRAME_HOLD) {
                    val dist = kotlin.math.hypot(dx, dy)
                    if (dist > 10f * density) {
                        keyframeHoldHandler.removeCallbacks(keyframeHoldRunnable)
                        pendingKeyframeFrame = -1L
                        isKeyframeDragUnlocked = false
                        touchMode = TouchMode.NONE
                    }
                    return true
                }

                if (touchMode == TouchMode.DRAG_KEYFRAME && isKeyframeDragUnlocked) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val targetFrame = frameFromX(event.x, laneStart, laneW).coerceIn(0L, totalDurationFrames)
                    if (targetFrame != draggedKeyframeFrame && draggedKeyframeFrame >= 0L) {
                        val track = tracks.getOrNull(selectedTrackIndex)
                        if (track != null) {
                            onKeyframeMoved?.invoke(track, draggedKeyframeFrame, targetFrame)
                            draggedKeyframeFrame = targetFrame
                            pendingKeyframeFrame = targetFrame
                            currentFrame = targetFrame
                            onFrameScrubbed?.invoke(targetFrame)
                            invalidate()
                        }
                    }
                    return true
                }

                // Detect vertical dragging on a track to reorder up or down
                if ((touchMode == TouchMode.NONE || touchMode == TouchMode.MOVE_CLIP) &&
                    kotlin.math.abs(dy) > 10f * density &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx) &&
                    draggedTrackIndex in tracks.indices
                ) {
                    touchMode = TouchMode.REORDER_TRACK
                }

                if (touchMode == TouchMode.REORDER_TRACK) {
                    val targetIndex = ((event.y - rulerHeight) / (trackHeight + trackSpacing)).toInt().coerceIn(0, tracks.lastIndex)
                    if (targetIndex != draggedTrackIndex && draggedTrackIndex in tracks.indices) {
                        val moved = tracks.removeAt(draggedTrackIndex)
                        tracks.add(targetIndex, moved)
                        draggedTrackIndex = targetIndex
                        selectedTrackIndex = targetIndex
                        onTrackReordered?.invoke(moved, targetIndex)
                        invalidate()
                    }
                    return true
                }

                val selectedTrack = tracks.getOrNull(selectedTrackIndex)
                when (touchMode) {
                    TouchMode.TRIM_LEFT -> {
                        if (selectedTrack != null) {
                            val curTouchFrame = frameFromX(event.x, laneStart, laneW)
                            val minDuration = 5L
                            val maxStart = initialTrackStart + initialTrackDuration - minDuration
                            val newStart = curTouchFrame.coerceIn(0L, maxStart)
                            val newDur = (initialTrackStart + initialTrackDuration) - newStart
                            selectedTrack.startFrame = newStart
                            selectedTrack.durationFrames = newDur
                            onClipSpanChanged?.invoke(selectedTrack)
                            invalidate()
                        }
                        return true
                    }
                    TouchMode.TRIM_RIGHT -> {
                        if (selectedTrack != null) {
                            if (event.x >= laneEnd - 24f * density && totalDurationFrames < 3600L) {
                                totalDurationFrames += 30L
                            }
                            val curTouchFrame = frameFromX(event.x, laneStart, laneW)
                            val minDuration = 5L
                            val newEnd = curTouchFrame.coerceIn(selectedTrack.startFrame + minDuration, totalDurationFrames)
                            selectedTrack.durationFrames = newEnd - selectedTrack.startFrame
                            onClipSpanChanged?.invoke(selectedTrack)
                            invalidate()
                        }
                        return true
                    }
                    TouchMode.MOVE_CLIP -> {
                        if (selectedTrack != null) {
                            val curTouchFrame = frameFromX(event.x, laneStart, laneW)
                            val delta = curTouchFrame - dragStartFrame
                            val maxStart = totalDurationFrames - selectedTrack.durationFrames
                            val newStart = (initialTrackStart + delta).coerceIn(0L, maxStart)
                            selectedTrack.startFrame = newStart
                            onClipSpanChanged?.invoke(selectedTrack)
                            updatePlayheadFromTouch(event.x, laneStart, laneW)
                            invalidate()
                        }
                        return true
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                keyframeHoldHandler.removeCallbacks(keyframeHoldRunnable)
                pendingKeyframeFrame = -1L
                isKeyframeDragUnlocked = false
                touchMode = TouchMode.NONE
                draggedTrackIndex = -1
                draggedKeyframeFrame = -1L
                onDragCompleted?.invoke()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun frameFromX(touchX: Float, laneStart: Float, laneW: Float): Long {
        val norm = ((touchX - laneStart) / laneW).coerceIn(0f, 1f)
        return (norm * totalDurationFrames).toLong()
    }

    private fun updatePlayheadFromTouch(touchX: Float, laneStart: Float, laneW: Float) {
        val targetFrame = frameFromX(touchX, laneStart, laneW)
        currentFrame = targetFrame
        onFrameScrubbed?.invoke(targetFrame)
        invalidate()
    }
}
