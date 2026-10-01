package com.example.nativecppapp.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
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

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MotionTheme.SURFACE)
            setPadding(36, 36, 36, 36)
        }

        val header = TextView(context).apply {
            text = "Efek Visual GPU (Post-Processing)"
            setTextColor(MotionTheme.TEXT_PRIMARY)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        root.addView(header)

        fun createSlider(label: String, min: Float, max: Float, current: Float, onChange: (Float) -> Unit) {
            val labelView = TextView(context).apply {
                text = "$label: ${String.format("%.2f", current)}"
                setTextColor(MotionTheme.TEXT_SECONDARY)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            root.addView(labelView)

            val seek = SeekBar(context).apply {
                this.max = 100
                progress = (((current - min) / (max - min)) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = min + (progress / 100f) * (max - min)
                        labelView.text = "$label: ${String.format("%.2f", value)}"
                        onChange(value)
                        onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            root.addView(seek)
        }

        createSlider("Kecerahan (Brightness)", -0.5f, 0.5f, brightness) { brightness = it }
        createSlider("Kontras (Contrast)", 0.2f, 2.5f, contrast) { contrast = it }
        createSlider("Saturasi (Saturation)", 0.0f, 2.5f, saturation) { saturation = it }
        createSlider("Vignette (Sudut Gelap)", 0.0f, 1.0f, vignette) { vignette = it }

        val filterLabel = TextView(context).apply {
            text = "FILTER PRESET"
            setTextColor(MotionTheme.TEXT_MUTED)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 16, 0, 8)
        }
        root.addView(filterLabel)

        val filterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }

        val filters = listOf(
            Pair("Normal", 0),
            Pair("Invert", 1),
            Pair("B&W", 2),
            Pair("Sepia", 3),
            Pair("RGB Split", 4),
            Pair("Neon Glow", 5)
        )

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        for (f in filters) {
            val btn = Button(context).apply {
                text = f.first
                textSize = 11f
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    marginEnd = dp(4)
                }
                setTextColor(if (filterMode == f.second) MotionTheme.CANVAS_BG else MotionTheme.TEXT_PRIMARY)
                setBackgroundColor(if (filterMode == f.second) MotionTheme.TEXT_PRIMARY else MotionTheme.SURFACE_HIGH)
                setOnClickListener {
                    filterMode = f.second
                    onEffectsChanged(brightness, contrast, saturation, vignette, filterMode)
                    dismiss()
                }
            }
            filterRow.addView(btn)
        }
        root.addView(filterRow)

        val btnClose = Button(context).apply {
            text = "Tutup & Terapkan"
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
            setTextColor(MotionTheme.CANVAS_BG)
            setBackgroundColor(MotionTheme.TEXT_PRIMARY)
            setOnClickListener { dismiss() }
        }
        root.addView(btnClose)

        setContentView(root)
    }
}
