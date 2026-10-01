package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.example.nativecppapp.NativeEngineBridge
import com.example.nativecppapp.ui.theme.MotionTheme

class KeyframeEditorDialog(
    context: Context,
    private val projectPtr: Long,
    private val layerId: String,
    private val layerName: String,
    initialFrame: Long,
    private val totalDurationFrames: Long,
    private val projectFps: Double,
    private val onPreviewUpdate: (frame: Long, posX: Double, posY: Double, scaleX: Double, scaleY: Double, rotation: Double) -> Unit,
    private val onKeyframeListChanged: () -> Unit
) : Dialog(context) {

    private var activeFrame: Long = initialFrame
    private var posX: Double = 0.0
    private var posY: Double = 0.0
    private var posZ: Double = 0.0
    private var scaleX: Double = 1.0
    private var scaleY: Double = 1.0
    private var rotation: Double = 0.0
    private var opacity: Double = 1.0
    private var interpolation: Int = NativeEngineBridge.InterpolationMode.BEZIER.value

    private val keyframeList = mutableListOf<Long>()

    private lateinit var keyframeLabel: TextView
    private lateinit var timeReadout: TextView
    private lateinit var timeSeekBar: SeekBar
    private lateinit var posXReadout: TextView
    private lateinit var posXSeekBar: SeekBar
    private lateinit var posYReadout: TextView
    private lateinit var posYSeekBar: SeekBar
    private lateinit var posZReadout: TextView
    private lateinit var posZSeekBar: SeekBar
    private lateinit var scaleReadout: TextView
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var rotationReadout: TextView
    private lateinit var rotationSeekBar: SeekBar

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.60).toInt())
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        refreshKeyframeList()
        loadKeyframeValues(activeFrame)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        // Header: Title & Close
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val headerText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tag = TextView(context).apply {
            text = "EDITOR KEYFRAME • MOTIONLAB"
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerText.addView(tag)

        val title = TextView(context).apply {
            text = "Keyframe: $layerName"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerText.addView(title)
        header.addView(headerText)

        val btnClose = Button(context).apply {
            text = "✕"
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }
        header.addView(btnClose)
        root.addView(header)

        // Keyframe Navigation Card
        val navCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val btnPrev = Button(context).apply {
            text = "◀ Prev"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener { selectPrevKeyframe() }
        }
        navCard.addView(btnPrev)

        keyframeLabel = TextView(context).apply {
            textSize = 11.5f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#34D399")) // Emerald
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        navCard.addView(keyframeLabel)

        val btnNext = Button(context).apply {
            text = "Next ▶"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener { selectNextKeyframe() }
        }
        navCard.addView(btnNext)

        val btnAdd = Button(context).apply {
            text = "+ Tambah"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
            setTextColor(Color.parseColor("#818CF8"))
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener { addKeyframeAtActiveFrame() }
        }
        navCard.addView(btnAdd)

        val btnDel = Button(context).apply {
            text = "🗑"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            setTextColor(Color.parseColor("#EF4444"))
            setBackgroundColor(MotionTheme.SURFACE_HIGH)
            setOnClickListener { deleteActiveKeyframe() }
        }
        navCard.addView(btnDel)
        root.addView(navCard)

        // Scrollable Parameter Editors
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // --- 1. WAKTU / DETIK KEYFRAME (Memindahkan detik 1s -> 2s) ---
        val timeHeader = createSectionHeader("WAKTU KEYFRAME (DETIK / FRAME)")
        content.addView(timeHeader)

        timeReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(timeReadout)

        timeSeekBar = SeekBar(context).apply {
            max = totalDurationFrames.toInt().coerceAtLeast(1)
            progress = activeFrame.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        moveActiveKeyframeTo(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        content.addView(timeSeekBar)

        // Quick time nudgers
        val timeNudgeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val nudgeLabels = listOf("-1.0s", "-0.1s", "+0.1s", "+1.0s")
        val nudgeDeltas = listOf(-60L, -6L, 6L, 60L)
        for (idx in nudgeLabels.indices) {
            val btn = Button(context).apply {
                text = nudgeLabels[idx]
                textSize = 10.5f
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setTextColor(MotionTheme.TEXT_SECONDARY)
                setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (idx < nudgeLabels.size - 1) dp(4) else 0
                }
                setOnClickListener {
                    val target = (activeFrame + nudgeDeltas[idx]).coerceIn(0L, totalDurationFrames)
                    moveActiveKeyframeTo(target)
                }
            }
            timeNudgeRow.addView(btn)
        }
        content.addView(timeNudgeRow)

        // --- 2. ARAH & POSISI (X, Y, Z) ---
        val posHeader = createSectionHeader("ARAH & POSISI (X, Y, Z)")
        content.addView(posHeader)

        // Pos X
        posXReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        content.addView(posXReadout)

        posXSeekBar = createSlider(-600, 600, posX.toInt()) { value ->
            posX = value.toDouble()
            updateKeyframeProperty("posX", posX)
            updateLabels()
        }
        content.addView(posXSeekBar)

        // Pos Y
        posYReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        content.addView(posYReadout)

        posYSeekBar = createSlider(-600, 600, posY.toInt()) { value ->
            posY = value.toDouble()
            updateKeyframeProperty("posY", posY)
            updateLabels()
        }
        content.addView(posYSeekBar)

        // Pos Z
        posZReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        content.addView(posZReadout)

        posZSeekBar = createSlider(-300, 300, posZ.toInt()) { value ->
            posZ = value.toDouble()
            updateKeyframeProperty("posZ", posZ)
            updateLabels()
        }
        content.addView(posZSeekBar)

        // --- 3. SKALA & ROTASI ---
        val transformHeader = createSectionHeader("SKALA & ROTASI")
        content.addView(transformHeader)

        // Scale
        scaleReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        content.addView(scaleReadout)

        scaleSeekBar = createSlider(10, 400, (scaleX * 100).toInt()) { value ->
            val s = value / 100.0
            scaleX = s
            scaleY = s
            updateKeyframeProperty("scaleX", scaleX)
            updateKeyframeProperty("scaleY", scaleY)
            updateLabels()
        }
        content.addView(scaleSeekBar)

        // Rotation
        rotationReadout = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        content.addView(rotationReadout)

        rotationSeekBar = createSlider(-360, 360, rotation.toInt()) { value ->
            rotation = value.toDouble()
            updateKeyframeProperty("rotation", rotation)
            updateLabels()
        }
        content.addView(rotationSeekBar)

        // --- 4. INTERPOLASI / KURVA ---
        val interpHeader = createSectionHeader("KURVA INTERPOLASI (EASING)")
        content.addView(interpHeader)

        val interpRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        val interpOptions = listOf(
            "Bezier (Halus)" to NativeEngineBridge.InterpolationMode.BEZIER.value,
            "Linear" to NativeEngineBridge.InterpolationMode.LINEAR.value,
            "Hold" to NativeEngineBridge.InterpolationMode.HOLD.value,
            "Auto Bezier" to NativeEngineBridge.InterpolationMode.AUTO_BEZIER.value,
            "Kontinu" to NativeEngineBridge.InterpolationMode.CONTINUOUS_BEZIER.value
        )

        for (opt in interpOptions) {
            val btn = Button(context).apply {
                text = opt.first
                textSize = 9.5f
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(6), dp(4), dp(6), dp(4))
                val isSel = (opt.second == interpolation)
                setTextColor(if (isSel) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(if (isSel) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_CONTAINER)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(3)
                }
                setOnClickListener {
                    interpolation = opt.second
                    commitAllCurrentProperties()
                    dismiss()
                    KeyframeEditorDialog(context, projectPtr, layerId, layerName, activeFrame, totalDurationFrames, projectFps, onPreviewUpdate, onKeyframeListChanged).show()
                }
            }
            interpRow.addView(btn)
        }
        content.addView(interpRow)

        scrollView.addView(content)
        root.addView(scrollView)

        setContentView(root)
        updateLabels()
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A1A1AA"))
            setPadding(0, dp(10), 0, dp(4))
        }
    }

    private fun createSlider(min: Int, max: Int, initial: Int, onProgress: (Int) -> Unit): SeekBar {
        return SeekBar(context).apply {
            this.max = max - min
            this.progress = (initial - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onProgress(prog + min)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun refreshKeyframeList() {
        keyframeList.clear()
        val frames = NativeEngineBridge.nativeGetLayerKeyframeFrames(projectPtr, layerId)
        for (f in frames) {
            keyframeList.add(f)
        }
        keyframeList.sort()
    }

    private fun loadKeyframeValues(frame: Long) {
        val vals = NativeEngineBridge.nativeGetKeyframeValues(projectPtr, layerId, frame)
        if (vals != null && vals.size >= 8) {
            posX = vals[0]
            posY = vals[1]
            posZ = vals[2]
            scaleX = vals[3]
            scaleY = vals[4]
            rotation = vals[5]
            opacity = vals[6]
            interpolation = vals[7].toInt()
        }
    }

    private fun updateLabels() {
        val curSec = if (projectFps > 0) activeFrame.toDouble() / projectFps else 0.0
        val kfIndex = keyframeList.indexOf(activeFrame)
        val indexText = if (kfIndex >= 0) "KF #${kfIndex + 1} / ${keyframeList.size}" else "KF Baru (Detik ${String.format("%.2f", curSec)}s)"

        keyframeLabel.text = indexText
        timeReadout.text = String.format("Posisi Waktu: %.2fs (Frame %d)", curSec, activeFrame)
        timeSeekBar.progress = activeFrame.toInt()

        posXReadout.text = String.format("Posisi X: %.1f px", posX)
        posYReadout.text = String.format("Posisi Y: %.1f px", posY)
        posZReadout.text = String.format("Posisi Z: %.1f px", posZ)
        scaleReadout.text = String.format("Skala: %.2fx", scaleX)
        rotationReadout.text = String.format("Rotasi: %.1f°", rotation)

        onPreviewUpdate(activeFrame, posX, posY, scaleX, scaleY, rotation)
    }

    private fun updateKeyframeProperty(propName: String, value: Double) {
        val ox = when (interpolation) {
            NativeEngineBridge.InterpolationMode.AUTO_BEZIER.value -> 0.33
            NativeEngineBridge.InterpolationMode.CONTINUOUS_BEZIER.value -> 0.25
            else -> 0.42
        }
        val oy = 0.0
        val ix = when (interpolation) {
            NativeEngineBridge.InterpolationMode.AUTO_BEZIER.value -> 0.67
            NativeEngineBridge.InterpolationMode.CONTINUOUS_BEZIER.value -> 0.75
            else -> 0.58
        }
        val iy = 1.0

        NativeEngineBridge.nativeAddOrUpdateKeyframe(
            projectPtr, layerId, propName, activeFrame, value,
            interpolation, ox, oy, ix, iy
        )
        refreshKeyframeList()
        onKeyframeListChanged()
    }

    private fun commitAllCurrentProperties() {
        val ox = 0.42; val oy = 0.0; val ix = 0.58; val iy = 1.0
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "posX", activeFrame, posX, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "posY", activeFrame, posY, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "posZ", activeFrame, posZ, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "scaleX", activeFrame, scaleX, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "scaleY", activeFrame, scaleY, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "rotation", activeFrame, rotation, interpolation, ox, oy, ix, iy)
        NativeEngineBridge.nativeAddOrUpdateKeyframe(projectPtr, layerId, "opacity", activeFrame, opacity, interpolation, ox, oy, ix, iy)
        refreshKeyframeList()
        onKeyframeListChanged()
    }

    private fun moveActiveKeyframeTo(newFrame: Long) {
        if (newFrame == activeFrame) return
        NativeEngineBridge.nativeMoveKeyframe(projectPtr, layerId, activeFrame, newFrame)
        activeFrame = newFrame
        refreshKeyframeList()
        onKeyframeListChanged()
        updateLabels()
    }

    private fun selectPrevKeyframe() {
        val prev = keyframeList.filter { it < activeFrame }.maxOrNull()
        if (prev != null) {
            activeFrame = prev
            loadKeyframeValues(activeFrame)
            updateLabels()
        }
    }

    private fun selectNextKeyframe() {
        val next = keyframeList.filter { it > activeFrame }.minOrNull()
        if (next != null) {
            activeFrame = next
            loadKeyframeValues(activeFrame)
            updateLabels()
        }
    }

    private fun addKeyframeAtActiveFrame() {
        commitAllCurrentProperties()
        refreshKeyframeList()
        updateLabels()
        Toast.makeText(context, "Keyframe ditambahkan pada frame $activeFrame", Toast.LENGTH_SHORT).show()
    }

    private fun deleteActiveKeyframe() {
        NativeEngineBridge.nativeDeleteKeyframeAtFrame(projectPtr, layerId, activeFrame)
        refreshKeyframeList()
        val fallback = keyframeList.firstOrNull() ?: 0L
        activeFrame = fallback
        loadKeyframeValues(activeFrame)
        onKeyframeListChanged()
        updateLabels()
        Toast.makeText(context, "Keyframe dihapus", Toast.LENGTH_SHORT).show()
    }
}
