package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class TransformInspectorDialog(
    context: Context,
    private val layerName: String,
    private val currentFrame: Long,
    initialPosX: Double,
    initialPosY: Double,
    initialScaleX: Double,
    initialScaleY: Double,
    initialRotation: Double,
    initialOpacity: Double,
    private val onTransformChanged: (
        posX: Double,
        posY: Double,
        scaleX: Double,
        scaleY: Double,
        rotation: Double,
        opacity: Double,
        recordKeyframe: Boolean
    ) -> Unit
) : Dialog(context) {

    private var currentPosX = initialPosX
    private var currentPosY = initialPosY
    private var currentScaleX = initialScaleX
    private var currentScaleY = initialScaleY
    private var currentRotation = initialRotation
    private var currentOpacity = initialOpacity
    private var autoKeyframe = true

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }

        // Header
        val headerBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val tagView = TextView(context).apply {
            text = "INSPEKTOR TRANSFORMASI • FRAME $currentFrame"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val titleView = TextView(context).apply {
            text = "Transformasi: $layerName"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MotionTheme.TEXT_PRIMARY)
        }
        headerBox.addView(titleView)
        root.addView(headerBox)

        // Auto Keyframe Switch
        val kfRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val kfLabel = TextView(context).apply {
            text = "Rekam Keyframe Otomatis"
            textSize = 12f
            setTextColor(MotionTheme.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        kfRow.addView(kfLabel)

        val kfSwitch = Switch(context).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                autoKeyframe = isChecked
            }
        }
        kfRow.addView(kfSwitch)
        root.addView(kfRow)

        // Scrollable Properties List
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Posisi X & Y Section
        val posTitle = createSectionTitle("POSISI (X, Y)")
        content.addView(posTitle)

        val posReadout = TextView(context).apply {
            text = String.format("X: %.1f px  |  Y: %.1f px", currentPosX, currentPosY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(6))
        }
        content.addView(posReadout)

        val posStepRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }

        fun applyPosDelta(dx: Double, dy: Double) {
            currentPosX += dx
            currentPosY += dy
            posReadout.text = String.format("X: %.1f px  |  Y: %.1f px", currentPosX, currentPosY)
            dispatchUpdate()
        }

        val btnLeft = createStepButton("← X-20") { applyPosDelta(-20.0, 0.0) }
        val btnRight = createStepButton("X+20 →") { applyPosDelta(20.0, 0.0) }
        val btnUp = createStepButton("↑ Y-20") { applyPosDelta(0.0, -20.0) }
        val btnDown = createStepButton("Y+20 ↓") { applyPosDelta(0.0, 20.0) }
        val btnCenter = createStepButton("Pusat (0,0)") {
            currentPosX = 0.0
            currentPosY = 0.0
            posReadout.text = "X: 0.0 px  |  Y: 0.0 px"
            dispatchUpdate()
        }

        posStepRow.addView(btnLeft)
        posStepRow.addView(btnRight)
        posStepRow.addView(btnUp)
        posStepRow.addView(btnDown)
        posStepRow.addView(btnCenter)
        content.addView(posStepRow)

        // 2. Skala (Scale) Section
        val scaleTitle = createSectionTitle("SKALA (UKURAN)")
        content.addView(scaleTitle)

        val scaleReadout = TextView(context).apply {
            text = String.format("%.0f%% (%.2fx)", currentScaleX * 100.0, currentScaleX)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(scaleReadout)

        val scaleSeek = SeekBar(context).apply {
            max = 300 // 10% to 300%
            progress = (currentScaleX * 100).toInt().coerceIn(10, 300)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val s = (prog.coerceAtLeast(10)) / 100.0
                        currentScaleX = s
                        currentScaleY = s
                        scaleReadout.text = String.format("%.0f%% (%.2fx)", s * 100.0, s)
                        dispatchUpdate()
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        content.addView(scaleSeek)

        val scaleQuickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        val scales = listOf(Pair("50%", 0.5), Pair("100%", 1.0), Pair("150%", 1.5), Pair("200%", 2.0))
        for (sq in scales) {
            val btn = createStepButton(sq.first) {
                currentScaleX = sq.second
                currentScaleY = sq.second
                scaleSeek.progress = (sq.second * 100).toInt()
                scaleReadout.text = String.format("%.0f%% (%.2fx)", sq.second * 100.0, sq.second)
                dispatchUpdate()
            }
            scaleQuickRow.addView(btn)
        }
        content.addView(scaleQuickRow)

        // 3. Rotasi (Rotation) Section
        val rotTitle = createSectionTitle("ROTASI (SUDUT)")
        content.addView(rotTitle)

        val rotReadout = TextView(context).apply {
            text = String.format("%.1f°", currentRotation)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(rotReadout)

        val rotSeek = SeekBar(context).apply {
            max = 360 // -180 to +180
            progress = (currentRotation + 180).toInt().coerceIn(0, 360)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val r = (prog - 180).toDouble()
                        currentRotation = r
                        rotReadout.text = String.format("%.1f°", r)
                        dispatchUpdate()
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        content.addView(rotSeek)

        val rotQuickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        val rots = listOf(Pair("-90°", -90.0), Pair("0°", 0.0), Pair("+90°", 90.0), Pair("180°", 180.0))
        for (rq in rots) {
            val btn = createStepButton(rq.first) {
                currentRotation = rq.second
                rotSeek.progress = (rq.second + 180).toInt()
                rotReadout.text = String.format("%.1f°", rq.second)
                dispatchUpdate()
            }
            rotQuickRow.addView(btn)
        }
        content.addView(rotQuickRow)

        // 4. Opasitas (Opacity / Alpha) Section
        val opTitle = createSectionTitle("OPASITAS (TRANSPARANSI)")
        content.addView(opTitle)

        val opReadout = TextView(context).apply {
            text = String.format("%.0f%%", currentOpacity * 100.0)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setPadding(0, 0, 0, dp(4))
        }
        content.addView(opReadout)

        val opSeek = SeekBar(context).apply {
            max = 100
            progress = (currentOpacity * 100).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val o = prog / 100.0
                        currentOpacity = o
                        opReadout.text = String.format("%.0f%%", o * 100.0)
                        dispatchUpdate()
                    }
                }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        content.addView(opSeek)

        scroll.addView(content)
        root.addView(scroll)

        // Done Button
        val btnDone = Button(context).apply {
            text = "Selesai"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { topMargin = dp(14) }
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener { dismiss() }
        }
        root.addView(btnDone)

        setContentView(root)
    }

    private fun dispatchUpdate() {
        onTransformChanged(
            currentPosX,
            currentPosY,
            currentScaleX,
            currentScaleY,
            currentRotation,
            currentOpacity,
            autoKeyframe
        )
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(MotionTheme.TEXT_MUTED)
            setPadding(0, dp(6), 0, dp(4))
        }
    }

    private fun createStepButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 10f
            typeface = Typeface.MONOSPACE
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(6), 0, dp(6), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setOnClickListener { onClick() }
        }
    }
}
