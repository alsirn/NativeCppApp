package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.example.nativecppapp.ui.theme.MotionTheme

class ShaderEffectsDialog(
    context: Context,
    private var brightness: Float,
    private var contrast: Float,
    private var saturation: Float,
    private var vignette: Float,
    private var filterMode: Int,
    private val onEffectsChanged: (b: Float, c: Float, s: Float, v: Float, mode: Int) -> Unit
) : Dialog(context) {

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
            text = "EFEK SHADER GPU & COLOR GRADING"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#818CF8"))
        }
        headerBox.addView(tagView)

        val header = TextView(context).apply {
            text = "Filter Visual & Warna"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerBox.addView(header)
        root.addView(headerBox)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. One-Tap Cinematic Grading Presets
        val presetLabel = TextView(context).apply {
            text = "PRESET GAYA SINEMATIK"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        content.addView(presetLabel)

        data class GradePreset(
            val name: String,
            val b: Float,
            val c: Float,
            val s: Float,
            val v: Float,
            val mode: Int
        )

        val presets = listOf(
            GradePreset("Natural", 0.0f, 1.0f, 1.0f, 0.0f, 0),
            GradePreset("Cyberpunk", 0.04f, 1.35f, 1.6f, 0.35f, 5),
            GradePreset("Teal/Orange", -0.02f, 1.25f, 1.35f, 0.25f, 0),
            GradePreset("Film Noir", 0.0f, 1.6f, 0.0f, 0.55f, 2),
            GradePreset("Retro 90s", 0.02f, 1.15f, 1.1f, 0.4f, 3),
            GradePreset("RGB Glitch", 0.0f, 1.1f, 1.4f, 0.15f, 4)
        )

        val gradeGrid1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
            layoutParams = lp
        }
        val gradeGrid2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            layoutParams = lp
        }

        val sliderUpdaters = mutableListOf<() -> Unit>()

        presets.forEachIndexed { i, p ->
            val btn = Button(context).apply {
                text = p.name
                textSize = 10f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (i % 3 != 2) marginEnd = dp(6)
                }
                setTextColor(MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    brightness = p.b
                    contrast = p.c
                    saturation = p.s
                    vignette = p.v
                    filterMode = p.mode
                    sliderUpdaters.forEach { it.invoke() }
                    onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                }
            }
            if (i < 3) gradeGrid1.addView(btn) else gradeGrid2.addView(btn)
        }
        content.addView(gradeGrid1)
        content.addView(gradeGrid2)

        // 2. Sliders
        fun createSlider(
            label: String,
            min: Float,
            max: Float,
            getVal: () -> Float,
            setVal: (Float) -> Unit
        ) {
            val labelView = TextView(context).apply {
                text = "$label: ${String.format("%.2f", getVal())}"
                setTextColor(MotionTheme.TEXT_SECONDARY)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(4), 0, 0)
            }
            content.addView(labelView)

            val seek = SeekBar(context).apply {
                this.max = 100
                progress = (((getVal() - min) / (max - min)) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = min + (progress / 100f) * (max - min)
                        labelView.text = "$label: ${String.format("%.2f", value)}"
                        setVal(value)
                        onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            sliderUpdaters.add {
                val cur = getVal()
                labelView.text = "$label: ${String.format("%.2f", cur)}"
                seek.progress = (((cur - min) / (max - min)) * 100).toInt()
            }
            content.addView(seek)
        }

        createSlider("Kecerahan (Brightness)", -0.5f, 0.5f, { brightness }) { brightness = it }
        createSlider("Kontras (Contrast)", 0.2f, 2.5f, { contrast }) { contrast = it }
        createSlider("Saturasi (Saturation)", 0.0f, 2.5f, { saturation }) { saturation = it }
        createSlider("Vignette (Sudut Gelap)", 0.0f, 1.0f, { vignette }) { vignette = it }

        scrollView.addView(content)
        root.addView(scrollView)

        val btnClose = Button(context).apply {
            text = "Tutup & Terapkan"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
                topMargin = dp(12)
            }
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4F46E5"))
            setOnClickListener { dismiss() }
        }
        root.addView(btnClose)

        setContentView(root)
    }
}
