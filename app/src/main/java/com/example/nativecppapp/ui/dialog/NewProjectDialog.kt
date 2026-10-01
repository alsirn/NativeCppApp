package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class NewProjectDialog(
    context: Context,
    private val onProjectCreated: (title: String, width: Int, height: Int, fps: Double, bgArgb: Int) -> Unit
) : Dialog(context) {

    private var selectedRatio = "9:16"
    private var selectedWidth = 1080
    private var selectedHeight = 1920
    private var selectedFps = 60.0
    private var selectedBg = 0xFF131315.toInt()

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // Title Header
        val header = TextView(context).apply {
            text = "Buat Proyek Baru"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(header)

        // Project Title Input
        val titleInput = EditText(context).apply {
            hint = "Nama Proyek"
            setText("Proyek MotionLab")
            setTextColor(MotionTheme.TEXT_PRIMARY)
            setHintTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(MotionTheme.SURFACE_CONTAINER)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 14f
        }
        root.addView(titleInput)

        // Label: Aspek Rasio
        val labelRatio = TextView(context).apply {
            text = "Aspek Rasio"
            setTextColor(MotionTheme.TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, dp(14), 0, dp(6))
        }
        root.addView(labelRatio)

        val ratioRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }

        val ratios = listOf(
            Triple("9:16", 1080, 1920),
            Triple("16:9", 1920, 1080),
            Triple("1:1", 1080, 1080),
            Triple("4:5", 1080, 1350)
        )

        val ratioButtons = mutableListOf<Button>()
        for (r in ratios) {
            val btn = Button(context).apply {
                text = r.first
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(6)
                }
                setTextColor(if (r.first == selectedRatio) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(if (r.first == selectedRatio) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    selectedRatio = r.first
                    selectedWidth = r.second
                    selectedHeight = r.third
                    for (b in ratioButtons) {
                        val isCurr = (b.text == selectedRatio)
                        b.setTextColor(if (isCurr) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                        b.setBackgroundColor(if (isCurr) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                    }
                }
            }
            ratioButtons.add(btn)
            ratioRow.addView(btn)
        }
        root.addView(ratioRow)

        // Label: Frame Rate
        val labelFps = TextView(context).apply {
            text = "Frame Rate"
            setTextColor(MotionTheme.TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(labelFps)

        val fpsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(18))
        }

        val fpsList = listOf(24.0, 30.0, 60.0)
        val fpsButtons = mutableListOf<Button>()
        for (f in fpsList) {
            val btn = Button(context).apply {
                text = "${f.toInt()} FPS"
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(6)
                }
                val isSelected = (f == selectedFps)
                setTextColor(if (isSelected) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(if (isSelected) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    selectedFps = f
                    for (b in fpsButtons) {
                        val isCur = b.text.toString().startsWith(f.toInt().toString())
                        b.setTextColor(if (isCur) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                        b.setBackgroundColor(if (isCur) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                    }
                }
            }
            fpsButtons.add(btn)
            fpsRow.addView(btn)
        }
        root.addView(fpsRow)

        // Action Buttons
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val btnCancel = Button(context).apply {
            text = "Batal"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginEnd = dp(8)
            }
            setTextColor(MotionTheme.TEXT_MUTED)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismiss() }
        }
        actionRow.addView(btnCancel)

        val btnCreate = Button(context).apply {
            text = "Buat Proyek"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener {
                val title = titleInput.text.toString().ifEmpty { "Proyek Baru" }
                onProjectCreated(title, selectedWidth, selectedHeight, selectedFps, selectedBg)
                dismiss()
            }
        }
        actionRow.addView(btnCreate)
        root.addView(actionRow)

        setContentView(root)
    }
}
